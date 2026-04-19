package com.example.hm10controller

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.util.*

class BluetoothLeService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null
    private val binder = LocalBinder()

    // UUID для HM-10
    val UUID_HM10_SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val UUID_HM10_CHAR: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder = binder

    // Виправлення помилки з Cast та Adapter
    fun connect(address: String): Boolean {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = manager.adapter
        val device = adapter?.getRemoteDevice(address) ?: return false

        // Перевірка дозволів для Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        try {
            bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        // Перевірка дозволу перед discoverServices
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(this@BluetoothLeService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) return

                        gatt.discoverServices()
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException: No permission to connect")
            return false
        }
        return true
    }

    fun writeData(data: String) {
        val service = bluetoothGatt?.getService(UUID_HM10_SERVICE)
        val characteristic = service?.getCharacteristic(UUID_HM10_CHAR)

        if (characteristic != null) {
            try {
                // Для Android 13+ використовується новий метод, для старих - старий
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeCharacteristic(characteristic, data.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    characteristic.value = data.toByteArray()
                    bluetoothGatt?.writeCharacteristic(characteristic)
                }
            } catch (e: SecurityException) {
                Log.e("BLE", "No permission to write")
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private fun close() {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: SecurityException) { }
    }
}
