package com.example.btcomm

import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // region check permissions
        var permissionsGranted by mutableStateOf(false)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                permissionsGranted = true

                val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // 300 seconds
                }
                startActivity(discoverableIntent)
            }
        }

        // Check if permissions are already granted
        val bluetoothConnectGranted = ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val bluetoothScanGranted = ContextCompat.checkSelfPermission(this, BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val bluetoothAdvertiseGranted = ContextCompat.checkSelfPermission(this, BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

        if (bluetoothConnectGranted && bluetoothScanGranted && bluetoothAdvertiseGranted) {
            permissionsGranted = true
            makeDeviceDiscoverable()
        } else {
            // Request the permissions if not already granted
            requestPermissionLauncher.launch(arrayOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE))
        }
        // endregion


        setContent {
            if (permissionsGranted) {
                BluetoothAppUI()
            } else {
                Text("Requesting Bluetooth permissions...")
            }
        }
    }

    // Function to make the device discoverable
    private fun makeDeviceDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // 300 seconds
        }
        startActivity(discoverableIntent)
    }
}

@RequiresPermission(BLUETOOTH_CONNECT)
@Composable
fun BluetoothAppUI(viewModel: BluetoothViewModel = viewModel()) {
    val receivedMessage by viewModel.receivedMessage.collectAsState()
    var messageToSend by remember { mutableStateOf(TextFieldValue("")) }
    val availableDevices by viewModel.availableDevices.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.discoverDevices(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isConnected) {
            TextField(
                value = messageToSend,
                onValueChange = { messageToSend = it },
                label = { Text("Type a message") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.sendMessage(messageToSend.text) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Send")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Received message:")
            Text(receivedMessage)

            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Text("Available devices:")
            LazyColumn {
                items(availableDevices) { device ->
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onClick = {
                            viewModel.connectDevice(device)
                        }
                    ) {

                        Text("${device.name} - ${device.address}")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
class BluetoothViewModel : ViewModel() {

    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var manageConnection: BluetoothManageConnection? = null

    /** State for displaying received message */
    private val _receivedMessage = MutableStateFlow("")
    val receivedMessage= _receivedMessage.asStateFlow()

    /** State for displaying available devices */
    private val _availableDevices = MutableStateFlow(emptyList<BluetoothDevice>())
    val availableDevices= _availableDevices.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    init {
        // Start accepting incoming connections
        startServer()
    }

    // Coroutine to send a message asynchronously
    fun sendMessage(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            manageConnection?.write(message.toByteArray())
        }
    }

    private fun startServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serverSocket: BluetoothServerSocket = bluetoothAdapter
                    .listenUsingRfcommWithServiceRecord("BluetoothApp", MY_UUID)
                val socket = serverSocket.accept()
                _isConnected.value = true
                manageConnection = BluetoothManageConnection(socket) { receivedData ->
                    _receivedMessage.value = receivedData
                }

                // Start receiving messages
                manageConnection?.receiveMessages()
                serverSocket.close()

            } catch (e: IOException) {
                _isConnected.value = false
                e.printStackTrace()
            }
        }
    }

    // Discover nearby devices
    fun discoverDevices(context: Context) {
        viewModelScope.launch {
            if (!bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.startDiscovery()
            }

            val discoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action: String = intent.action ?: return
                    if (action == BluetoothDevice.ACTION_FOUND) {
                        val device: BluetoothDevice =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                        _availableDevices.value += device
                    }
                }
            }

            // Register for broadcasts when a device is discovered
            val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(discoveryReceiver, intentFilter)
        }
    }

    // Connect to a Bluetooth device
    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothAdapter.cancelDiscovery()
                 socket.connect()
                _isConnected.value = true

                manageConnection = BluetoothManageConnection(socket) { receivedData ->
                    _receivedMessage.value = receivedData
                }
                manageConnection?.receiveMessages()
            } catch (e: IOException) {
                _isConnected.value = false
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        manageConnection?.close()
    }
}

class BluetoothManageConnection(
    private val socket: BluetoothSocket,
    private val onReceive: (String) -> Unit
) {

    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream

    // Coroutine to receive messages
    suspend fun receiveMessages() {
        try {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                bytes = withContext(Dispatchers.IO) {
                    inputStream.read(buffer)
                }
                val message = String(buffer, 0, bytes)
                onReceive(message)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Coroutine to send a message
    fun write(bytes: ByteArray) {
        try {
            outputStream.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            socket.close()
        } catch(ex: IOException) {
            ex.printStackTrace()
        }
    }
}