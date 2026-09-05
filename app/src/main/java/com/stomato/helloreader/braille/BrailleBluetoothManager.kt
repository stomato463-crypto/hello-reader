package com.stomato.helloreader.braille

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * 点字ディスプレイ Bluetooth SPP (Serial Port Profile) 接続 ＆ 未ペアリング端末スキャン管理
 * BrailleSense, BrailleMemo, Focus, Seika 等に対応
 */
class BrailleBluetoothManager(
    private val context: Context,
    private val onDataReceived: (ByteArray) -> Unit,
    private val onConnectionStateChanged: (Boolean, String?) -> Unit
) {
    companion object {
        private const val TAG = "BrailleBluetoothManager"
        // 標準 SPP (Serial Port Profile) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    var connectedDeviceName: String? = null
        private set

    private var connectThread: Thread? = null
    private var receiveThread: Thread? = null

    // スキャンで見つかった点字端末のコールバック
    var onDeviceDiscovered: ((BluetoothDevice) -> Unit)? = null

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        Log.d(TAG, "Discovered Bluetooth Device: ${device.name ?: "Unknown"} (${device.address})")
                        onDeviceDiscovered?.invoke(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Bluetooth discovery finished.")
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            context.registerReceiver(discoveryReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register discoveryReceiver", e)
        }
    }

    /**
     * ペアリング済みの点字端末一覧を取得
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bonded devices", e)
            emptyList()
        }
    }

    /**
     * 未ペアリングの周囲の点字端末をスキャン開始
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
            Log.d(TAG, "Bluetooth discovery started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start bluetooth discovery", e)
        }
    }

    /**
     * 対象の点字端末に Bluetooth SPP で接続
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        disconnect()

        connectThread = Thread {
            try {
                Log.d(TAG, "Connecting to ${device.name ?: "Device"} (${device.address})...")
                bluetoothAdapter?.cancelDiscovery()

                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()

                socket = s
                inputStream = s.inputStream
                outputStream = s.outputStream
                isConnected = true
                connectedDeviceName = device.name ?: device.address

                Log.d(TAG, "Successfully connected to $connectedDeviceName")
                onConnectionStateChanged(true, connectedDeviceName)

                // 受信ループの開始
                startReceiveLoop()

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                disconnect()
                onConnectionStateChanged(false, null)
            }
        }.apply { start() }
    }

    /**
     * 点字セルデータを端末へ送信
     */
    fun sendData(packet: ByteArray) {
        if (!isConnected) return
        try {
            outputStream?.write(packet)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to braille device", e)
            disconnect()
            onConnectionStateChanged(false, null)
        }
    }

    private fun startReceiveLoop() {
        receiveThread = Thread {
            val buffer = ByteArray(128)
            while (isConnected) {
                try {
                    val inStream = inputStream ?: break
                    val bytesRead = inStream.read(buffer)
                    if (bytesRead > 0) {
                        val receivedData = buffer.copyOf(bytesRead)
                        onDataReceived(receivedData)
                    }
                } catch (e: Exception) {
                    if (isConnected) {
                        Log.e(TAG, "Error in receive loop", e)
                        disconnect()
                        onConnectionStateChanged(false, null)
                    }
                    break
                }
            }
        }.apply { start() }
    }

    fun disconnect() {
        isConnected = false
        connectedDeviceName = null
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    fun shutdown() {
        disconnect()
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {}
    }
}
