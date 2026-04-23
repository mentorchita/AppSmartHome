package com.example.hm10controller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothLEManager(private val context: Context) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicTx: BluetoothGattCharacteristic? = null
    private var characteristicRx: BluetoothGattCharacteristic? = null
    private var isConnected = false
    private var receivedData = StringBuilder()

    // HM-10 сервіс та характеристики — правильний повний UUID формат
    private val uuidService = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val uuidTx     = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB") // Send
    private val uuidRx     = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB") // Receive
    private val uuidCccd   = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    fun connect(device: BluetoothDevice): Boolean {
        return try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    fun send(command: String) {
        if (characteristicTx != null && isConnected) {
            try {
                characteristicTx?.value = command.toByteArray()
                bluetoothGatt?.writeCharacteristic(characteristicTx)
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun read(): String {
        return try {
            val result = receivedData.toString()
                .replace("\r", "")  // Arduino println додає \r\n — прибираємо
                .replace("\n", "")
            receivedData.clear()
            result
        } catch (e: Exception) {
            ""
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            isConnected = false
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isConnected(): Boolean = isConnected

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            try {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    isConnected = true
                    gatt?.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    isConnected = false
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Логуємо всі знайдені сервіси для діагностики
                    gatt?.services?.forEach { service ->
                        android.util.Log.d("BLE_GATT", "Сервіс: ${service.uuid}")
                        service.characteristics.forEach { char ->
                            android.util.Log.d("BLE_GATT", "  Характеристика: ${char.uuid}")
                        }
                    }

                    val service = gatt?.getService(uuidService)
                    if (service != null) {
                        android.util.Log.d("BLE_GATT", "✅ HM-10 сервіс FFE0 знайдено!")
                        characteristicTx = service.getCharacteristic(uuidTx)
                        characteristicRx = service.getCharacteristic(uuidRx)
                        gatt.setCharacteristicNotification(characteristicRx, true)
                        val descriptor = characteristicRx?.getDescriptor(uuidCccd)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        android.util.Log.d("BLE_GATT", "✅ Характеристика TX/RX налаштована")
                    } else {
                        android.util.Log.e("BLE_GATT", "❌ Сервіс FFE0 НЕ знайдено! Перевірте UUID")
                    }
                } else {
                    android.util.Log.e("BLE_GATT", "onServicesDiscovered помилка status=$status")
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            try {
                if (characteristic?.uuid == uuidRx) {
                    val data = characteristic.value
                    if (data != null) {
                        receivedData.append(String(data))
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            try {
                // Дані успішно надіслані
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}