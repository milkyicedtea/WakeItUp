package org.cheek.wakeitup

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cheek.wakeitup.ui.theme.WakeItUpTheme
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val database = DeviceDatabase.getDatabase(applicationContext)
        val deviceDao = database.deviceDao()
        val groupDao = database.groupDao()
        val factory = DeviceViewModelFactory(application, deviceDao, groupDao)

        setContent {
            val viewModel: DeviceViewModel = viewModel(factory = factory)
            var selectedItem by remember { mutableIntStateOf(0) }

            WakeItUpTheme {
                Scaffold(
                    bottomBar = {
                        BottomNavbar(
                            selectedItem = selectedItem,
                            onItemSelected = { selectedItem = it }
                        )
                    },
                ) { innerPadding ->
                    BuildBody(innerPadding, selectedItem, viewModel)
                }
            }
        }
    }
}

@Composable
fun BottomNavbar(selectedItem: Int = 0, onItemSelected: (Int) -> Unit) {
    val items = listOf("Devices", "Groups")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.DeviceHub, Icons.Filled.Devices)
    val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.DeviceHub, Icons.Outlined.Devices)

    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = item
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { onItemSelected(index) },
            )
        }
    }
}

@Composable
fun BuildBody(padding: PaddingValues, selectedItem: Int, viewModel: DeviceViewModel) {
    when (selectedItem) {
        0 -> DeviceListScreen(viewModel, padding)
        1 -> GroupListScreen(viewModel, padding)
    }
}

suspend fun sendWakeOnLanPacket(macAddress: String, broadcastAddress: String, port: Int = 9): Boolean {
    return withContext(Dispatchers.IO) { // Ensure network operations are off the main thread
        val cleanMac = macAddress.replace(":", "").replace("-", "").replace(" ", "")
        if (!cleanMac.matches(Regex("^[0-9A-Fa-f]{12}$"))) {
            Log.e("WOL", "Invalid MAC address format: $macAddress")
            return@withContext false
        }

        val macBytes = try {
            (0 until 12 step 2).map { cleanMac.substring(it, it + 2).toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            Log.e("WOL", "Error parsing MAC bytes for $macAddress", e)
            return@withContext false
        }

        if (macBytes.size != 6) {
            Log.e("WOL", "MAC address must resolve to 6 bytes: $macAddress")
            return@withContext false
        }

        val magicPacket = ByteArray(6 + 16 * 6) // 6 bytes FF + 16 repetitions of 6-byte MAC

        for (i in 0..5) {
            magicPacket[i] = 0xFF.toByte()
        }
        for (i in 0..15) {
            macBytes.copyInto(magicPacket, 6 + i * 6)
        }

        try {
            val address = InetAddress.getByName(broadcastAddress)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)
                socket.send(packet)
                Log.d("WOL", "Magic packet sent to MAC $macAddress via $broadcastAddress:$port")
            }
            true
        } catch (e: Exception) {
            Log.e("WOL", "Failed to send WOL packet for $macAddress to $broadcastAddress:$port", e)
            false
        }
    }
}