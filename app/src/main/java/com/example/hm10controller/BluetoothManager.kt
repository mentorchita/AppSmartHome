package com.example.hm10controller

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.*

class BluetoothManager {
    private var socket: BluetoothSocket? = null
    private var outputStream: java.io.OutputStream? = null
    private var inputStream: java.io.InputStream? = null

    fun connect(device: BluetoothDevice): Boolean {
        return try {
            socket = device.createRfcommSocketToServiceRecord(
                UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            )
            socket?.connect()
            outputStream = socket?.outputStream
            inputStream = socket?.inputStream
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun send(command: String) {
        try {
            outputStream?.write(command.toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun read(): String {
        return try {
            val buffer = ByteArray(256)
            val bytes = inputStream?.read(buffer) ?: 0
            String(buffer, 0, bytes)
        } catch (e: IOException) {
            ""
        }
    }

    fun disconnect() {
        try { socket?.close() } catch (e: IOException) {}
    }

    fun isConnected(): Boolean = socket?.isConnected == true
}