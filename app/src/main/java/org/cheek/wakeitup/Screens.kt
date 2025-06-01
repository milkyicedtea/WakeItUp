package org.cheek.wakeitup

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun GroupListScreen(viewModel: DeviceViewModel, scaffoldPadding: PaddingValues) {
    val groups by viewModel.allGroups.collectAsState()
    val selectedGroupName by viewModel.selectedGroupName.collectAsState()
    val devicesInSelectedGroup by viewModel.devicesInSelectedGroup.collectAsState()

    var newGroupName by remember { mutableStateOf("") }

    var deviceToEditState by remember { mutableStateOf<Device?>(null) }

    Box(modifier = Modifier.fillMaxSize()) { // Root Box is edge-to-edge
        Column(modifier = Modifier.fillMaxSize()) { // Main content column
            // 1. Header
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding() // Respects status bar
                    .padding(horizontal = 8.dp, vertical = 16.dp), // Padding for the Surface content itself
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(4.dp) // Nicer shape for a header
            ) {
                Text(
                    "Groups Management", // Updated title
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 2. Create New Group UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("New Group Name") },
                    modifier = Modifier
                        .weight(3f)
                        .alignBy(FirstBaseline),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.addGroup(newGroupName)
                            newGroupName = "" // Clear input field
                        }
                    },
                    enabled = newGroupName.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .alignBy(FirstBaseline)
                ) {
                    Text("Add")
                }
            }

            // 3. Group Selection UI
            Text(
                "Select a Group:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (groups.isEmpty()) {
                    item {
                        Text(
                            "No groups created yet. Use the field above to add one.",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(groups.size) { index ->
                    val group = groups[index]
                    Button(
                        onClick = { viewModel.selectGroup(group.name) },
                        enabled = selectedGroupName != group.name,
                    ) {
                        Text(group.name)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Devices in $selectedGroupName: (${devicesInSelectedGroup.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.addGroup(newGroupName)
                            newGroupName = "" // Clear input field
                        }
                    },
                    enabled = newGroupName.isNotBlank(),
                    modifier = Modifier
                        .alignBy(FirstBaseline)
                ) {
                    Text("Wake all")
                }
            }

            if (devicesInSelectedGroup.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        // Apply bottom padding from scaffold to ensure this message is not hidden
                        .padding(bottom = scaffoldPadding.calculateBottomPadding()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (groups.isEmpty() && selectedGroupName == "Default") "Create a group and add devices." else "No devices in this group.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Takes remaining vertical space
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 0.dp, // Horizontal padding is handled by DeviceRowItem
                        end = 0.dp,
                        top = 8.dp,
                        // Important: Apply bottom padding from scaffold to ensure list scrolls above bottom nav bar
                        bottom = scaffoldPadding.calculateBottomPadding() + 16.dp // +16 for extra margin
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between device rows
                ) {
                    items(devicesInSelectedGroup.size) { index ->
                        val device = devicesInSelectedGroup[index]
                        // Assuming DeviceRowItem is defined as shown in your DeviceListScreen
                        DeviceRowItem(device = device, viewModel = viewModel, onEditRequest = { deviceToEditState = it })
                    }
                }
            }
        }

        deviceToEditState?.let { currentDeviceToEdit ->
            val allGroupsForDialog by viewModel.allGroups.collectAsState() // Re-collect for dialog context if needed, or pass `groups`
            val context = LocalContext.current
            EditDeviceDialog(
                deviceToEdit = currentDeviceToEdit,
                allGroupsFromDb = allGroupsForDialog, // Use the collected groups
                onDismiss = { deviceToEditState = null },
                onSave = { updatedDevice ->
                    viewModel.addDevice(updatedDevice) // Use addDevice (REPLACE)
                    deviceToEditState = null
                    Toast.makeText(context, "${updatedDevice.name} updated", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

}

@Composable
fun DeviceListScreen(viewModel: DeviceViewModel, scaffoldPadding: PaddingValues) {
    val devices by viewModel.allDevicesList.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val selectedGroupNameForNewDevice by viewModel.selectedGroupName.collectAsState()

    var deviceToEditState by remember { mutableStateOf<Device?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "All Devices (${devices.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f) // Fills available space in the Column
                        .fillMaxWidth()
                        // Pad the content of this Box away from the bottom navigation bar
                        .padding(bottom = scaffoldPadding.calculateBottomPadding())
                        .padding(16.dp), // Your general content padding
                    contentAlignment = Alignment.Center
                ) {
                    Text("No devices found.\nTap the '+' button to add one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Fills available space
                        .fillMaxWidth(),
                    // Content padding for items inside LazyColumn.
                    // The bottom padding ensures the last item isn't hidden by the BottomNavBar.
                    contentPadding = PaddingValues(
                        bottom = scaffoldPadding.calculateBottomPadding() + 16.dp // Space for nav bar + 16dp margin
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices.size) { index ->
                        val device = devices[index]

                        DeviceRowItem(device, viewModel, onEditRequest = { deviceToEditState = it })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(scaffoldPadding)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Device")
        }

        if (showAddDialog) {
            val allGroups by viewModel.allGroups.collectAsState()
            AddDeviceDialog(
                allGroupsFromDb = allGroups,
                initialSelectedGroupName = selectedGroupNameForNewDevice, // Use the globally selected group as default
                onDismiss = { showAddDialog = false },
                onSave = { device ->
                    viewModel.addDevice(device)
                    showAddDialog = false
                },
                viewModel = viewModel
            )
        }
    }

    deviceToEditState?.let { currentDeviceToEdit ->
        val allGroupsForDialog by viewModel.allGroups.collectAsState() // Re-collect for dialog context if needed, or pass `groups`
        val context = LocalContext.current
        EditDeviceDialog(
            deviceToEdit = currentDeviceToEdit,
            allGroupsFromDb = allGroupsForDialog, // Use the collected groups
            onDismiss = { deviceToEditState = null },
            onSave = { updatedDevice ->
                viewModel.addDevice(updatedDevice) // Use addDevice (REPLACE)
                deviceToEditState = null
                Toast.makeText(context, "${updatedDevice.name} updated", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceRowItem(
    device: Device,
    viewModel: DeviceViewModel,
    onEditRequest: (Device) -> Unit
) {

    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp) // Item's own horizontal padding
            // vertical padding/spacing is handled by LazyColumn's verticalArrangement
            .background(
                Color(device.color).copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .clickable {
                if (device.macAddress.isNotBlank()) {
                    viewModel.wakeDeviceUp(device) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "MAC Address not set for ${device.name}. Cannot send WOL.", Toast.LENGTH_LONG).show()
                }
            }
            .padding(8.dp) // Inner padding for the content of the row
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "MAC: ${device.macAddress.ifBlank { "Not set" }}", // Show MAC
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "IP: ${device.ipAddress.ifBlank { "N/A" }} | Port: ${device.port}", // Show IP and WOL Port
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Group: ${device.groupName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
        Box( // Color indicator
            Modifier
                .size(24.dp)
                .background(Color(device.color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.size(8.dp))

        // Wake Up Button
        IconButton(
            onClick = { onEditRequest(device) },
            // enabled = device.macAddress.isNotBlank() // Enable only if MAC is present
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit ${device.name}"
            )
        }

        IconButton(onClick = {
            viewModel.deleteDevice(device)
            Toast.makeText(context, "${device.name} deleted", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${device.name}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceDialog(
    deviceToEdit: Device, // The device instance to edit
    allGroupsFromDb: List<Group>,
    onDismiss: () -> Unit,
    onSave: (Device) -> Unit, // Callback with the updated device
    // viewModel: DeviceViewModel // Not strictly needed if onSave handles the VM call
) {
    // Initialize states with the deviceToEdit's properties
    // Use remember with deviceToEdit.id as a key to re-initialize if a different device is edited
    // (though typically the dialog is dismissed and recreated)
    var nameState by remember(deviceToEdit.id) { mutableStateOf(deviceToEdit.name) }
    var macState by remember(deviceToEdit.id) { mutableStateOf(deviceToEdit.macAddress) }
    var ipState by remember(deviceToEdit.id) { mutableStateOf(deviceToEdit.ipAddress) }

    // Assuming WOL port is stored in device.port for simplicity from previous setup
    var wolPortState by remember(deviceToEdit.id) { mutableStateOf(deviceToEdit.port.toString()) }
    // If you had a separate broadcastAddress field in Device entity, load it here too.
    // For now, we'll infer or let user input it.
    var broadcastAddressState by remember(deviceToEdit.id) { mutableStateOf(inferBroadcastAddress(deviceToEdit.ipAddress) ?: "") }


    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var currentSelectedGroupString by remember(deviceToEdit.id) { mutableStateOf(deviceToEdit.groupName) }

    val availableColors = remember {
        listOf(
            Color(0xFF29B6F6), Color(0xFFAB47BC), Color(0xFFC0CA33),
            Color(0xFFFFB74D), Color(0xFFEF5350), Color(0xFF26C6DA), Color(0xFF7E57C2),
            Color.Gray, Color.Black, Color.White, Color.Transparent
        )
    }
    // Find the index of the current device's color, or default
    val initialColorIndex = remember(deviceToEdit.id) {
        availableColors.indexOfFirst { it.toArgb() == deviceToEdit.color }.takeIf { it != -1 } ?: 0
    }
    var selectedColorIndex by remember(deviceToEdit.id) { mutableIntStateOf(initialColorIndex) }

    // Ensure selected group is valid if it changed in the DB
    LaunchedEffect(allGroupsFromDb, deviceToEdit.groupName) {
        if (!allGroupsFromDb.any { it.name == currentSelectedGroupString }) {
            currentSelectedGroupString = allGroupsFromDb.firstOrNull()?.name ?: "Default"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()) // Make dialog content scrollable
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text("Edit Device", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        if (macState.isNotBlank() && !isValidMacAddress(macState)) {
                            // Show error: MAC is invalid
                            return@IconButton
                        }
                        if (broadcastAddressState.isNotBlank() && !isValidIpAddress(broadcastAddressState)) {
                            // Show error: Broadcast IP is invalid
                            return@IconButton
                        }

                        val updatedDevice = deviceToEdit.copy( // IMPORTANT: Use copy to preserve ID
                            name = nameState.ifBlank { "Unnamed Device" },
                            macAddress = macState.trim(),
                            ipAddress = ipState.trim(),
                            port = wolPortState.toIntOrNull() ?: 9, // WOL port
                            groupName = currentSelectedGroupString,
                            color = availableColors[selectedColorIndex].toArgb()

                            // If you add broadcastAddress to Device entity, update it here:
                            // broadcastAddress = broadcastAddressState.trim()
                        )
                        onSave(updatedDevice)
                    }) { Icon(Icons.Default.Save, "Save Changes") }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Color selection UI (same as AddDeviceDialog)
                Text("Device Color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                LazyRow(
                    modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableColors.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(availableColors[index], CircleShape)
                                .clickable { selectedColorIndex = index }
                                .border(
                                    width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColorIndex == index) {
                                Icon(Icons.Default.Check, null, tint = if(availableColors[index] == Color.Black || availableColors[index] == Color(0xFF7E57C2)) Color.White else Color.Black )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Form Fields (similar to AddDeviceDialog's FORM state)
                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Group selection Dropdown (same as AddDeviceDialog)
                Text("Group", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(
                    expanded = groupDropdownExpanded,
                    onExpandedChange = { groupDropdownExpanded = !groupDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentSelectedGroupString,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryEditable,
                                enabled = true
                            ).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = groupDropdownExpanded,
                        onDismissRequest = { groupDropdownExpanded = false }
                    ) {
                        if (allGroupsFromDb.isEmpty()){
                            DropdownMenuItem(
                                text = { Text("No groups available.") },
                                onClick = { groupDropdownExpanded = false },
                                enabled = false
                            )
                        }
                        allGroupsFromDb.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    currentSelectedGroupString = group.name
                                    groupDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))


                Text("Wake-on-LAN Details (Required)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = macState,
                    onValueChange = { macState = it.replace(" ", "").replace(":", "").replace("-", "") },
                    label = { Text("MAC Address (e.g., 001122AABBCC)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = macState.isNotBlank() && !isValidMacAddress(macState)
                )
                if (macState.isNotBlank() && !isValidMacAddress(macState)) {
                    Text("Invalid MAC: 12 hex characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = broadcastAddressState,
                    onValueChange = { broadcastAddressState = it },
                    label = { Text("Broadcast IP (e.g., 192.168.1.255)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = broadcastAddressState.isNotBlank() && !isValidIpAddress(broadcastAddressState)
                )
                if (broadcastAddressState.isNotBlank() && !isValidIpAddress(broadcastAddressState)) {
                    Text("Invalid IP format", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wolPortState,
                    onValueChange = { wolPortState = it },
                    label = { Text("WOL Port (Default: 9)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Device Network Info (Reference)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = ipState,
                    onValueChange = { ipState = it },
                    label = { Text("Device's Current IP (if known)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

enum class AddDeviceState {
    SCANNING,
    FORM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    allGroupsFromDb: List<Group>,
    initialSelectedGroupName: String,
    onDismiss: () -> Unit,
    onSave: (Device) -> Unit,
    viewModel: DeviceViewModel
) {
    var screenState by remember { mutableStateOf(AddDeviceState.SCANNING) }
    var selectedDeviceFromScan by remember { mutableStateOf<NetworkDevice?>(null) }

    // Connect to ViewModel state flows
    val discoveredDevices by viewModel.scannedNetworkDevices.collectAsState()
    val isScanning by viewModel.isScanningNetwork.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val scanTotal by viewModel.scanTotal.collectAsState()

    var nameState by remember { mutableStateOf("") }
    var macState by remember { mutableStateOf("") }
    var ipState by remember { mutableStateOf("") }
    var broadcastAddressState by remember { mutableStateOf("") }
    var portState by remember { mutableStateOf("9") }
    var deviceId by remember { mutableStateOf("") }

    // Group selection state
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var currentSelectedGroupString by remember { mutableStateOf(initialSelectedGroupName) }

    // Color selection (index based)
    val availableColors = remember { listOf(
        Color(0xFF29B6F6), Color(0xFFAB47BC), Color(0xFFC0CA33),
        Color(0xFFFFB74D), Color(0xFFEF5350), Color(0xFF26C6DA), Color(0xFF7E57C2),
        Color.Gray, Color.Black, Color.White
    ) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (screenState == AddDeviceState.SCANNING) {
            viewModel.startNetworkScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopNetworkScan()
            viewModel.clearScannedDevices() // Clear the list for next time
        }
    }

    LaunchedEffect(allGroupsFromDb, initialSelectedGroupName) {
        if (!allGroupsFromDb.any { it.name == currentSelectedGroupString}) {
            currentSelectedGroupString = allGroupsFromDb.firstOrNull()?.name ?: "Default"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight().padding(vertical = 64.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text("Add Device", style = MaterialTheme.typography.titleLarge)
                    IconButton(
                        onClick = {
                            val deviceToSave = Device(
                                name = nameState,
                                macAddress = macState,
                                ipAddress = ipState,
                                port = portState.toIntOrNull() ?: 9,
                                groupName = currentSelectedGroupString,
                                color = availableColors[selectedColorIndex].toArgb()
                            )
                            onSave(deviceToSave)
                        },
                        enabled = (
                            (macState.isNotBlank() && isValidMacAddress(macState))
                                    &&
                            (broadcastAddressState.isNotBlank() && isValidIpAddress(broadcastAddressState))
                        )
                    ) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color selection
                Text("Device Color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableColors.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(availableColors[index], CircleShape)
                                .clickable { selectedColorIndex = index }
                                .border(
                                    width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColorIndex == index) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Main content area - using weight to fill available space
                Box(modifier = Modifier.weight(1f)) {
                    when (screenState) {
                        AddDeviceState.SCANNING -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (isScanning) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Scanning network... ($scanProgress/$scanTotal)")
                                        Text("${discoveredDevices.size} devices found so far")
                                    }
                                } else {
                                    if (discoveredDevices.isEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Error,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("No devices found. Try scanning again or add a device manually.")
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        ) {
                                            items(discoveredDevices.size) { index ->
                                                val device = discoveredDevices[index]
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedDeviceFromScan = device
                                                            nameState = device.name
                                                            ipState = device.ip
                                                            macState = device.macAddress ?: ""
                                                            broadcastAddressState = device.broadcastAddress
                                                            portState = device.port.toString()
                                                            screenState = AddDeviceState.FORM
                                                        }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.DevicesOther,
                                                        null,
                                                        Modifier.padding(end = 8.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(device.name)
                                                        Text(
                                                            "${device.ip} ${if (device.macAddress != null) "(MAC: ${device.macAddress})" else ""}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (device.serviceType.isNotEmpty() && device.serviceType != "ping_discovered") {
                                                            Text(
                                                                "Service: ${device.serviceType.removeSuffix(".")}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                if (index < discoveredDevices.lastIndex) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AddDeviceState.FORM -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                OutlinedTextField(
                                    value = nameState,
                                    onValueChange = { nameState = it },
                                    label = { Text("Device Name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Group selection Dropdown
                                Text(
                                    "Group",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenuBox(
                                    expanded = groupDropdownExpanded,
                                    onExpandedChange = { groupDropdownExpanded = !groupDropdownExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = currentSelectedGroupString,
                                        onValueChange = {}, // Value changed by dropdown selection
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(
                                                type = ExposedDropdownMenuAnchorType.SecondaryEditable,
                                                enabled = true
                                            ).fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = groupDropdownExpanded,
                                        onDismissRequest = { groupDropdownExpanded = false }
                                    ) {
                                        allGroupsFromDb.forEach { group ->
                                            DropdownMenuItem(
                                                text = { Text(group.name) },
                                                onClick = {
                                                    currentSelectedGroupString = group.name
                                                    groupDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                val maxChar = 17
                                OutlinedTextField(

                                    singleLine = true,
                                    value = macState,
                                    onValueChange = { if (it.length <= maxChar) macState = it },
                                    label = { Text("MAC Address") },
                                    placeholder = { Text("Format: 00:11:22:33:44:55") },
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = !isValidMacAddress(macState),
                                    trailingIcon = {
                                        if (macState.isEmpty() || !isValidMacAddress(macState))
                                            Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
                                    },
                                    supportingText = {
                                        Text(
                                            text = "${macState.length} / $maxChar",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End,
                                        )
                                    },
                                )

                                if (macState.isEmpty()) {
                                    Text(
                                        "Due to restrictions in Android 11+ this Mac address could not be fetched " +
                                        "automatically. Please enter it manually.\n" +
                                        "For more info check the help page in the settings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                var advancedExpanded by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { advancedExpanded = !advancedExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Advanced Details", style = MaterialTheme.typography.titleMedium)
                                    Icon(
                                        if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        if (advancedExpanded) "Collapse" else "Expand"
                                    )
                                }

                                AnimatedVisibility(visible = advancedExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = ipState,
                                            onValueChange = { ipState = it },
                                            label = { Text("Device IP") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = broadcastAddressState,
                                            onValueChange = { broadcastAddressState = it },
                                            label = { Text("Broadcast Address") },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("Default: subnet.255") }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = portState,
                                            onValueChange = { portState = it },
                                            label = { Text("WOL Port") },
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            placeholder = { Text("Default: 9") }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = deviceId,
                                            onValueChange = { deviceId = it },
                                            label = { Text("Device ID (Optional)") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom buttons - now at the bottom with padding
                if (screenState == AddDeviceState.SCANNING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { viewModel.startNetworkScan() },
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan Again")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                selectedDeviceFromScan = null
                                nameState = ""
                                ipState = ""
                                macState = ""
                                portState = "9"
                                broadcastAddressState = ""
                                screenState = AddDeviceState.FORM
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Manually")
                        }
                    }
                }
            }
        }
    }
}

// --- Helper Functions ---
fun isValidMacAddress(mac: String): Boolean {
    val cleanMac = mac.replace(":", "").replace("-", "").replace(" ", "")
    return cleanMac.matches(Regex("^[0-9A-Fa-f]{12}$"))
}

fun isValidIpAddress(ip: String?): Boolean {
    if (ip == null) return false
    return ip.matches(Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$"))
}

fun inferBroadcastAddress(ipAddress: String?): String? {
    if (ipAddress == null || !isValidIpAddress(ipAddress)) return null
    val parts = ipAddress.split(".")
    if (parts.size == 4) {
        return "${parts[0]}.${parts[1]}.${parts[2]}.255"
    }
    return null
}