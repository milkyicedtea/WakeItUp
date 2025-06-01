package org.cheek.wakeitup

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
// import kotlinx.coroutines.flow.update // Alternative for StateFlow updates
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetAddress
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class DeviceViewModel(
    private val application: Application,
    private val deviceDao: DeviceDao,
    private val groupDao: GroupDao
) : ViewModel() {

    private val nsdManager: NsdManager = application.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener?>()
    private val discoveredServicesMap = mutableMapOf<String, NsdServiceInfo>()

    private val _scannedNetworkDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val scannedNetworkDevices: StateFlow<List<NetworkDevice>> = _scannedNetworkDevices.asStateFlow()

    private val _isScanningNetwork = MutableStateFlow(false)
    val isScanningNetwork: StateFlow<Boolean> = _isScanningNetwork.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val _scanTotal = MutableStateFlow(254)
    val scanTotal: StateFlow<Int> = _scanTotal.asStateFlow()

    // For the Dart-style ping scan
    private val sharedPingProgressCounter = AtomicInteger(0)
    private var pingChainJobs: MutableList<Job> = mutableListOf()

    companion object {
        val SERVICE_TYPES = listOf(
        "_services._dns-sd._udp",
        "_http._tcp.",
        "_workstation._tcp.",
        "_companion-link._tcp.",
        "_ssh._tcp.",
        "_smb._tcp.",
        "_printer._tcp.",
        "_ipp._tcp.",
        "_device-info._tcp.",
        "_googlecast._tcp.",
        "_spotify-connect._tcp.",
        "_airplay._tcp.",
        "_raop._tcp.",
        "_sleep-proxy._udp",
        "_sleep-proxy._tcp",
        "_homekit._tcp"
        )
        private const val PING_SCAN_CONCURRENT_CHAINS = 25 // Equivalent to 'step' in Dart
    }

    val allGroups: StateFlow<List<Group>> = groupDao.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedGroupName = MutableStateFlow("Bookmarked")
    val selectedGroupName: StateFlow<String> = _selectedGroupName.asStateFlow()

    fun selectGroup(groupName: String) {
        _selectedGroupName.value = groupName
    }

    val allDevicesList: StateFlow<List<Device>> = deviceDao.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val devicesInSelectedGroup: StateFlow<List<Device>> = _selectedGroupName.flatMapLatest { groupName ->
        deviceDao.getDevicesByGroup(groupName)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun addDevice(device: Device) {
        viewModelScope.launch {
            deviceDao.insert(device)
        }
    }

    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            deviceDao.delete(device)
        }
    }

    fun addGroup(groupName: String) {
        viewModelScope.launch {
            if (groupName.isNotBlank()) {
                groupDao.insertGroup(Group(name = groupName))
            }
        }
    }

    init {
        viewModelScope.launch {
            val defaultGroupExists = groupDao.getGroupByName("Bookmarked")
            if (defaultGroupExists == null) {
                groupDao.insertGroup(Group(name = "Bookmarked"))
            }
        }
    }

    // --- Start/Stop Scan Logic ---
    override fun onCleared() {
        super.onCleared()
        Log.d("VIEWMODEL", "onCleared called, stopping network scan.")
        stopNetworkScan()
    }

    fun startNetworkScan() {
        if (_isScanningNetwork.value) {
            Log.d("SCAN", "Scan already in progress.")
            return
        }
        Log.d("SCAN", "Starting network scan with updated API usage.")
        stopNetworkScan()

        _isScanningNetwork.value = true
        _scannedNetworkDevices.value = emptyList()
        discoveredServicesMap.clear()
        _scanProgress.value = 0
        sharedPingProgressCounter.set(0)
        pingChainJobs.clear()

        viewModelScope.launch {
            val localSubnetPrefix = getLocalSubnet()
            Log.d("SCAN", "Local subnet prefix determined: $localSubnetPrefix")

            // Start Ping Scan (high priority)
            if (localSubnetPrefix != null && _isScanningNetwork.value) {
                Log.d("SCAN", "Starting ping scan for subnet: $localSubnetPrefix")
                _scanTotal.value = 254
                for (i in 1..PING_SCAN_CONCURRENT_CHAINS) {
                    if (!_isScanningNetwork.value) break
                    val job = launch(Dispatchers.IO) {
                        pingDeviceChain(localSubnetPrefix, i, PING_SCAN_CONCURRENT_CHAINS)
                    }
                    pingChainJobs.add(job)
                }
            } else if (localSubnetPrefix == null) {
                Log.e("SCAN", "Could not get local subnet prefix, ping scan skipped.")
            }

            // Start common service discovery in parallel with ping scan
            val priorityServices = listOf("_workstation._tcp.", "_http._tcp.", "_smb._tcp.")
            for (serviceType in priorityServices) {
                if (!_isScanningNetwork.value) break
                startServiceDiscovery(serviceType)
                delay(100)
            }

            // Add a small delay to let pings run a bit before more NSD
            if (_isScanningNetwork.value) delay(500)

            // Start discovery for remaining services
            for (serviceType in SERVICE_TYPES.filter { it !in priorityServices }) {
                if (!_isScanningNetwork.value) break
                startServiceDiscovery(serviceType)
                delay(80) // Slightly shorter delays
            }

            // At this point we have both ping and NSD running in parallel
            val scanTimeoutMillis = 3000L // Shorter timeout for better UX
            Log.d("SCAN", "All scan processes launched. Waiting up to $scanTimeoutMillis ms")
            delay(scanTimeoutMillis)

            if (_isScanningNetwork.value) {
                Log.i("SCAN", "Scan timeout reached. Finishing scan.")
                // Let NSD listeners finish naturally, just stop active pings
                pingChainJobs.forEach { if (it.isActive) it.cancel() }
                pingChainJobs.clear()

                // Set scanning to false but keep the listeners running a bit longer
                _isScanningNetwork.value = false

                // After a short delay, clean up any remaining listeners
                delay(1000)
                stopAllDiscoveryListeners()
            }
        }
    }

    private fun stopAllDiscoveryListeners() {
        val listenerKeys = discoveryListeners.keys.toList()
        listenerKeys.forEach { serviceType ->
            discoveryListeners.remove(serviceType)?.let { listener ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                    Log.d("NSD", "Stopped discovery for $serviceType")
                } catch (e: Exception) {
                    Log.w("NSD", "Error stopping $serviceType: ${e.message}")
                }
            }
        }
    }

    fun stopNetworkScan() {
        Log.d("VIEWMODEL", "stopNetworkScan called. isScanning: ${_isScanningNetwork.value}")
        if (!_isScanningNetwork.value && pingChainJobs.isEmpty() && discoveryListeners.isEmpty()) {
            return
        }
        _isScanningNetwork.value = false

        pingChainJobs.forEach { if (it.isActive) it.cancel() }
        pingChainJobs.clear()

        stopAllDiscoveryListeners()

        _scanProgress.value = 0
        Log.d("VIEWMODEL", "stopNetworkScan finished processing.")
    }

    // --- Improved Ping Scan Implementation ---
    private suspend fun pingDeviceChain(networkPrefix: String, startIndex: Int, step: Int) {
        var currentIndex = startIndex
        val pingTimeoutMS = 200 // Slightly shorter timeout for faster scanning

        Log.d("PING_CHAIN", "Chain $startIndex starting: $networkPrefix.$currentIndex, step $step")
        while (currentIndex <= 254 && _isScanningNetwork.value) {
            val hostIp = "$networkPrefix.$currentIndex"
            var deviceFoundThisIp = false

            if (_isScanningNetwork.value) {
                val isReachable = isHostReachable(hostIp, pingTimeoutMS)
                if (isReachable && _isScanningNetwork.value) {
                    Log.d("PING_CHAIN", "Trying to get hostname for ip $hostIp")
                    val hostname = getHostname(hostIp)
                    Log.d("PING_CHAIN", "Trying to get MAC for ip $hostIp")
                    val macAddress = getMacFromArpCache(hostIp) // Try to get MAC from ARP
                    if (!_isScanningNetwork.value) break

                    Log.i("PING_CHAIN", "Device found: $hostIp (Hostname: ${hostname ?: "N/A"}, MAC: ${macAddress ?: "Unknown"})")
                    val newDevice = NetworkDevice(
                        name = hostname ?: hostIp,
                        ip = hostIp,
                        macAddress = macAddress,
                        broadcastAddress = getBroadcastAddress(hostIp),
                        port = 9, // Default WoL port
                        serviceType = "ping_discovered"
                    )

                    synchronized(_scannedNetworkDevices) {
                        val currentList = _scannedNetworkDevices.value
                        if (currentList.none { it.ip == newDevice.ip }) {
                            _scannedNetworkDevices.value = currentList + newDevice
                        }
                    }
                    deviceFoundThisIp = true
                }
            }

            val currentOverallProgress = sharedPingProgressCounter.incrementAndGet()
            _scanProgress.value = currentOverallProgress.coerceAtMost(_scanTotal.value)

            currentIndex += step
            if (_isScanningNetwork.value) {
                delay(if (deviceFoundThisIp) 2L else 5L) // Shorter delays for better performance
            }
        }
        Log.d("PING_CHAIN", "Chain $startIndex finished or scan stopped. Progress: ${sharedPingProgressCounter.get()}/${_scanTotal.value}")
    }

    // --- Get MAC address from ARP cache ---
    private fun getMacFromArpCache(ip: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/net/arp")
            process.inputStream.bufferedReader().use { reader ->
                val output = reader.readText()
                val lines = output.split("\n")
                for (line in lines) {
                    if (line.contains(ip)) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val mac = parts[3]
                            if (mac != "00:00:00:00:00:00" && mac.matches("([0-9a-fA-F]{2}[:-]){5}([0-9a-fA-F]{2})".toRegex())) {
                                return mac
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d("ARP", "Failed to get MAC from ARP: ${e.message}")
            null
        }
    }

    // --- Helper for Ping ---
    private suspend fun isHostReachable(host: String, timeoutMs: Int): Boolean {
        if (!_isScanningNetwork.value) return false
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(host).isReachable(timeoutMs)
            } catch (_: Exception) {
                false
            }
        }
    }

    // --- Improved NSD Implementation ---
    private fun startServiceDiscovery(serviceType: String) {
        if (!_isScanningNetwork.value || discoveryListeners.containsKey(serviceType)) return
        val listener = initializeDiscoveryListener(serviceType)
        discoveryListeners[serviceType] = listener
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            Log.d("NSD", "Discovery initiated for $serviceType")
        } catch (e: Exception) {
            Log.e("NSD", "Error starting discovery for $serviceType", e)
            discoveryListeners.remove(serviceType)
        }
    }

    private fun initializeDiscoveryListener(serviceTypeToDiscover: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "Listener for '$serviceTypeToDiscover' started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!_isScanningNetwork.value) return
                Log.d("NSD", "Service found: ${service.serviceName} (Type: ${service.serviceType})")
                val serviceKey = "${service.serviceName}-${service.serviceType}"
                if (!discoveredServicesMap.containsKey(serviceKey)) {
                    discoveredServicesMap[serviceKey] = service
                    try {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(service, initializeResolveListener(serviceKey, serviceTypeToDiscover))
                    } catch (e: IllegalArgumentException) {
                        Log.e("NSD", "Error resolving ${service.serviceName}: ${e.message}")
                        discoveredServicesMap.remove(serviceKey)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val serviceKey = "${service.serviceName}-${service.serviceType}"
                Log.d("NSD", "Service lost: ${service.serviceName}")
                discoveredServicesMap.remove(serviceKey)

                // Only remove from scanned devices if we're still scanning
                if (_isScanningNetwork.value) {
                    synchronized(_scannedNetworkDevices) {
                        _scannedNetworkDevices.value = _scannedNetworkDevices.value.filterNot {
                            it.name == service.serviceName && it.serviceType == service.serviceType
                        }
                    }
                }
            }

            override fun onDiscoveryStopped(stoppedRegType: String) {
                Log.i("NSD", "Discovery stopped for: $stoppedRegType")
                discoveryListeners.remove(serviceTypeToDiscover)
            }

            override fun onStartDiscoveryFailed(failedRegType: String, errorCode: Int) {
                Log.e("NSD", "Discovery start failed for $failedRegType: $errorCode")
                discoveryListeners.remove(serviceTypeToDiscover)
            }

            override fun onStopDiscoveryFailed(failedRegType: String, errorCode: Int) {
                Log.e("NSD", "Discovery stop failed for $failedRegType: $errorCode")
                discoveryListeners.remove(serviceTypeToDiscover)
            }
        }
    }

    private fun initializeResolveListener(serviceKey: String, originalServiceType: String): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e("NSD", "Resolve failed for ${serviceInfo?.serviceName ?: serviceKey}: $errorCode")
                discoveredServicesMap.remove(serviceKey)
            }

            @SuppressLint("NewApi")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null || !_isScanningNetwork.value) {
                    discoveredServicesMap.remove(serviceKey)
                    return
                }

                var chosenHostAddressString: String? = null

                // Get host address (handle both API versions)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hostAddresses: List<InetAddress> = serviceInfo.hostAddresses
                    for (addr in hostAddresses) {
                        if (!addr.isLoopbackAddress && addr.hostAddress!!.contains(".")) {
                            chosenHostAddressString = addr.hostAddress
                            break
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    chosenHostAddressString = serviceInfo.host?.hostAddress
                }

                if (chosenHostAddressString == null) {
                    Log.w("NSD", "No suitable IPv4 address for ${serviceInfo.serviceName}")
                    discoveredServicesMap.remove(serviceKey)
                    return
                }

                Log.i("NSD", "Service resolved: ${serviceInfo.serviceName}, Host: $chosenHostAddressString, Port: ${serviceInfo.port}")
                discoveredServicesMap.remove(serviceKey)

                var deviceName = serviceInfo.serviceName ?: "Unknown Service"
                Log.d("NSD", "Device name is $deviceName")
                deviceName = extractBetterName(serviceInfo, deviceName)

                // Clean up the service name if needed
                if (deviceName.contains(".local")) {
                    deviceName = deviceName.replace(".local", "")
                }

                // For some service types, the service name needs special handling
                when {
                    originalServiceType.contains("_googlecast") -> {
                        // For Google Cast devices, remove the hash suffix often appended
                        deviceName = deviceName.replace(Regex("-[0-9a-f]{8}$"), "")
                    }
                    originalServiceType.contains("_airplay") || originalServiceType.contains("_raop") -> {
                        // For Apple devices, decode the hex encoding sometimes used
                        if (deviceName.contains("%")) {
                            try {
                                deviceName = URLDecoder.decode(deviceName, "UTF-8")
                            } catch (_: Exception) {
                                Log.d("NSD", "Failed to decode device name: $deviceName")
                            }
                        }
                    }
                }

                Log.i("NSD", "Service resolved: $deviceName, Host: $chosenHostAddressString, Port: ${serviceInfo.port}")

                val newDevice = NetworkDevice(
                    name = deviceName,
                    ip = chosenHostAddressString,
                    port = serviceInfo.port,
                    serviceType = serviceInfo.serviceType ?: originalServiceType,
                    macAddress = getMacFromArpCache(chosenHostAddressString),
                    broadcastAddress = getBroadcastAddress(chosenHostAddressString)
                )

                synchronized(_scannedNetworkDevices) {
                    val currentList = _scannedNetworkDevices.value
                    if (currentList.none { it.ip == newDevice.ip && it.name == newDevice.name }) {
                        _scannedNetworkDevices.value = (currentList + newDevice).distinctBy {
                            "${it.ip}-${it.name}-${it.port}-${it.serviceType}"
                        }
                    }
                }
            }
        }
    }

    // --- Improved Subnet Detection ---
    private fun getLocalSubnet(): String? {
        // Try modern API first
        val connectivityManager = application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (connectivityManager != null) {
            try {
                val activeNetwork = connectivityManager.activeNetwork ?: return null
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

                for (linkAddress in linkProperties.linkAddresses) {
                    val address = linkAddress.address
                    if (address is InetAddress && !address.isLoopbackAddress && address.hostAddress!!.contains(".")) {
                        val ipString = address.hostAddress
                        Log.d("Subnet", "Found IP Address: $ipString")
                        if (ipString != null) {
                            return ipString.substringBeforeLast('.')
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Subnet", "Error with modern API: ${e.message}")
            }
        }

        // Fallback to older WifiManager API
        try {
            @Suppress("DEPRECATION")
            val wifiManager = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager?.connectionInfo
            @Suppress("DEPRECATION")
            val ipAddressInt = connectionInfo?.ipAddress ?: 0

            if (ipAddressInt != 0) {
                val ipString = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipAddressInt and 0xff,
                    ipAddressInt shr 8 and 0xff,
                    ipAddressInt shr 16 and 0xff,
                    ipAddressInt shr 24 and 0xff
                )
                Log.d("Subnet", "Fallback WifiManager: Found IP: $ipString")
                return ipString.substringBeforeLast('.')
            }
        } catch (e: Exception) {
            Log.e("Subnet", "Error with fallback API: ${e.message}")
        }

        // Try a last resort method - check for common prefixes
        for (commonPrefix in listOf("192.168.0", "192.168.1", "10.0.0", "10.0.1")) {
            try {
                if (InetAddress.getByName("$commonPrefix.1").isReachable(500)) {
                    Log.d("Subnet", "Last resort found working prefix: $commonPrefix")
                    return commonPrefix
                }
            } catch (_: Exception) {
                // Ignore failure, try next prefix
            }
        }

        return null
    }

    private fun getBroadcastAddress(ipAddress: String?): String {
        if (ipAddress == null || !isValidIpAddress(ipAddress)) return "255.255.255.255"
        return ipAddress.substringBeforeLast(".") + ".255"
    }

    private val hostnameCache = mutableMapOf<String, String?>()

    private suspend fun getHostname(ip: String): String? {
        // Quick return from cache
        if (hostnameCache.containsKey(ip)) {
            return hostnameCache[ip]
        }

        Log.d("HOSTNAME", "Resolving hostname for IP: $ip")
        return withContext(Dispatchers.IO) {
            // Set a shorter timeout for DNS operations
            val result = try {
                // Try to get hostname with a timeout of 800ms
                withTimeoutOrNull(800L) {
                    val inetAddress = InetAddress.getByName(ip)
                    val canonicalName = inetAddress.canonicalHostName
                    val hostName = inetAddress.hostName
                    val hostAddress = inetAddress.hostAddress
                    Log.d("INET", "canonicalHostName is: $canonicalName")
                    Log.d("INET", "hostName is: $hostName")
                    Log.d("INET", "hostAddress is $hostAddress")

                    // Try canonical hostname first
                    if (canonicalName != null && canonicalName != ip) {
                        Log.d("HOSTNAME", "Found canonical name: $canonicalName for $ip")
                        return@withTimeoutOrNull canonicalName
                    }

                    // Try regular hostname
                    if (hostName != null && hostName != ip) {
                        Log.d("HOSTNAME", "Found host name: $hostName for $ip")
                        return@withTimeoutOrNull hostName
                    }

                    // Try NetBIOS name (common for Windows machines)
                    tryGetNetbiosName(ip)?.let {
                        Log.d("HOSTNAME", "Found NetBIOS name: $it for $ip")
                        return@withTimeoutOrNull it
                    }

                    Log.d("HOSTNAME", "Returning `null` hostname for $ip")
                    null
                }
            } catch (e: Exception) {
                Log.d("HOSTNAME", "Error resolving hostname for $ip: ${e.message}")
                null
            }

            // Try other methods if nothing worked so far
            val fallbackName = tryGetAlternativeHostname(ip)

            // Cache the result (even if null) to avoid repeated lookups
            hostnameCache[ip] = result ?: fallbackName
            result ?: fallbackName
        }
    }

    private fun tryGetNetbiosName(ip: String): String? {
        return try {
            // Simple command execution to try jcifs/NetBIOS lookup
            val process = Runtime.getRuntime().exec("getent hosts $ip")
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Parse output - typically looks like: "192.168.1.5 hostname"
            if (output.isNotBlank()) {
                val parts = output.trim().split("\\s+".toRegex())
                if (parts.size > 1) {
                    return parts[1]
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryGetAlternativeHostname(ip: String): String? {
        try {
            // Try reading from common network configuration files
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists() && arpFile.canRead()) {
                arpFile.bufferedReader().use { reader ->
                    reader.lineSequence().drop(1) // Skip header line
                        .filter { line -> line.contains(ip) }
                        .forEach { line ->
                            val parts = line.trim().split("\\s+".toRegex())
                            if (parts.size >= 6) {
                                // Check if there's an interface name that might help with device identification
                                val interfaceName = parts.lastOrNull()
                                val interfaceType = when {
                                    interfaceName?.contains("wlan", ignoreCase = true) == true -> "WiFi"
                                    interfaceName?.contains("eth", ignoreCase = true) == true -> "Ethernet"
                                    else -> "Device"
                                }

                                // Use vendor info from MAC address if available
                                val mac = if (parts.size >= 4) parts[3] else null
                                if (!mac.isNullOrBlank() && mac != "00:00:00:00:00:00") {
                                    val vendorPrefix = mac.split(":").take(3).joinToString(":")
                                    val vendor = getMacVendor(vendorPrefix)
                                    if (vendor != null) {
                                        return "$vendor $interfaceType"
                                    }
                                }

                                return "$interfaceType on ${ip.split(".").last()}"
                            }
                        }
                }
            }

            // Basic fallback - just use last octet of IP as identifier
            val lastOctet = ip.split(".").lastOrNull()
            if (lastOctet != null) {
                return "Device $lastOctet"
            }
        } catch (e: Exception) {
            Log.d("HOSTNAME", "Error in alternative hostname resolution: ${e.message}")
        }
        return null
    }

    // Common MAC address vendor prefixes
    private fun getMacVendor(prefix: String): String? {
        val vendorMap = mapOf(
            "00:50:56" to "VMware",
            "00:0C:29" to "VMware",
            "00:1A:11" to "Google",
            "08:00:27" to "VirtualBox",
            "00:1B:44" to "SanDisk",
            "00:25:00" to "Apple",
            "08:00:20" to "Oracle",
            "00:04:76" to "3Com",
            "00:13:10" to "Cisco",
            "00:1C:B3" to "Apple",
            "00:1D:BA" to "Sony",
            "00:21:19" to "Samsung",
            "00:22:41" to "Apple",
            "00:25:BC" to "Apple",
            "00:26:BB" to "Apple",
            "00:30:48" to "Supermicro",
            "00:0E:8F" to "Sercomm",
            "00:90:FB" to "TP-Link",
            "18:31:BF" to "Netgear",
            "B8:27:EB" to "Raspberry Pi",
            "DC:A6:32" to "Raspberry Pi",
            "E0:DC:FF" to "Xiaomi",
            "D8:3A:DD" to "Intel",
            "48:D7:05" to "Apple",
            "68:DB:CA" to "Apple",
            "A0:99:9B" to "Apple"
            // Add more common vendors as needed
        )

        return vendorMap[prefix]
    }

    @SuppressLint("NewApi")
    private fun extractBetterName(serviceInfo: NsdServiceInfo, originalName: String): String {
        // Try to get attributes (Android 11+)
        try {
            // Different services store device names in different attributes
            val attributes = serviceInfo.attributes

            Log.d("ATTR", attributes.map {"${it.key}: ${it.value}"}.joinToString(", "))

            // Common attributes that contain device names
            val possibleNameAttributes = listOf(
                "n", "fn", "name", "model", "md", "deviceName", "am", "dn"
            )

            for (attr in possibleNameAttributes) {
                attributes[attr]?.let { nameBytes ->
                    val nameStr = String(nameBytes, Charset.forName("UTF-8"))
                    if (nameStr.isNotBlank() && nameStr != serviceInfo.host?.hostAddress) {
                        Log.d("NSD", "Found better name '$nameStr' in attribute '$attr'")
                        return nameStr
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("NSD", "Error extracting attributes: ${e.message}")
        }

        return originalName
    }


    fun wakeDeviceUp(device: Device, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            Log.d("WOL_VM", "Attempting to wake device: ${device.name} (MAC: ${device.macAddress})")
            if (device.macAddress.isBlank()) {
                onResult(false, "MAC address is missing for ${device.name}.")
                return@launch
            }
            var targetBroadcastAddress = getBroadcastAddress(device.ipAddress.ifBlank { getLocalSubnet() })
            if (targetBroadcastAddress == "255.255.255.255" && isValidIpAddress(device.ipAddress)) {
                targetBroadcastAddress = device.ipAddress.substringBeforeLast(".") + ".255"
            } else if (targetBroadcastAddress == "255.255.255.255") {
                val localSub = getLocalSubnet()
                if (localSub != null) targetBroadcastAddress = "$localSub.255"
            }

            Log.d("WOL_VM", "Targeting broadcast: $targetBroadcastAddress for MAC: ${device.macAddress}")

            val success = sendWakeOnLanPacket(device.macAddress, targetBroadcastAddress, device.port)
            if (success) onResult(true, "WOL packet sent for ${device.name}!")
            else onResult(false, "Failed to send WOL for ${device.name}.")
        }
    }

    private fun isValidIpAddress(ip: String?): Boolean {
        return ip != null && ip.matches(Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$"))
    }

    fun clearScannedDevices() {
        _scannedNetworkDevices.value = emptyList()
        discoveredServicesMap.clear()
    }
}

class DeviceViewModelFactory(
    private val application: Application,
    private val deviceDao: DeviceDao,
    private val groupDao: GroupDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceViewModel(application, deviceDao, groupDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}