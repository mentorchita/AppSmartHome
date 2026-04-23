package com.example.hm10controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.IBinder
import java.util.UUID

// ============================================================
// BleScanManager - для сканування BLE пристроїв
// ============================================================

@SuppressLint("MissingPermission")
class BleScanManager(
    private val bluetoothAdapter: BluetoothAdapter,
    private val onDeviceFound: (BluetoothDevice, Int) -> Unit  // device + rssi
) {
    private var isScanning = false

    // scanCallback оголошується ДО startScan, щоб уникнути NPE
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            android.util.Log.d("BLE_SCAN", "Знайдено: ${result.device.name} (${result.device.address}) rssi=${result.rssi}")
            onDeviceFound(result.device, result.rssi)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                android.util.Log.d("BLE_SCAN", "Batch: ${result.device.name} (${result.device.address})")
                onDeviceFound(result.device, result.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e("BLE_SCAN", "Помилка сканування errorCode=$errorCode")
            isScanning = false
        }
    }

    fun startScan(): Boolean {
        // Отримуємо scanner щоразу заново — не кешуємо!
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            android.util.Log.e("BLE_SCAN", "BluetoothLeScanner == null, Bluetooth вимкнутий?")
            return false
        }

        if (isScanning) {
            android.util.Log.w("BLE_SCAN", "Сканування вже запущено")
            return false
        }

        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Запускаємо БЕЗ фільтрів — знаходить ВСІ BLE пристрої
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            android.util.Log.d("BLE_SCAN", "Сканування ЗАПУЩЕНО")
            true
        } catch (e: SecurityException) {
            android.util.Log.e("BLE_SCAN", "SecurityException: ${e.message}")
            false
        }
    }

    fun stopScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        if (!isScanning) return
        try {
            scanner.stopScan(scanCallback)
            android.util.Log.d("BLE_SCAN", "Сканування ЗУПИНЕНО")
        } catch (e: SecurityException) {
            android.util.Log.e("BLE_SCAN", "SecurityException при зупинці: ${e.message}")
        } finally {
            isScanning = false
        }
    }

    fun isScanning(): Boolean = isScanning
}

// ============================================================
// MainActivity
// ============================================================

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private var bluetoothService: BluetoothLeService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BluetoothLeService.LocalBinder
            bluetoothService = binder?.getService()

            val address = intent.getStringExtra("device_address")
            if (address != null && bluetoothService != null) {
                bluetoothService?.connect(address)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
        }
    }

    private lateinit var bluetoothManager: BluetoothLEManager
    private lateinit var bleScanManager: BleScanManager
    private lateinit var handler: Handler
    private var connectedDevice: BluetoothDevice? = null

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var spinnerDevices: Spinner
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnLedOn: Button
    private lateinit var btnLedOff: Button
    private lateinit var btnRelayOn: Button
    private lateinit var btnRelayOff: Button
    private lateinit var btnAlarmArm: Button
    private lateinit var btnAlarmDisarm: Button
    private lateinit var seekServo1: SeekBar
    private lateinit var seekServo2: SeekBar
    private lateinit var seekFan: SeekBar
    private lateinit var seekLedPwm: SeekBar
    private lateinit var btnServo1Set: Button
    private lateinit var btnServo2Set: Button
    private lateinit var btnFanSet: Button
    private lateinit var btnFanStop: Button
    private lateinit var btnLedPwmSet: Button
    private lateinit var btnMusic1: Button
    private lateinit var btnMusic2: Button
    private lateinit var btnMusicStop: Button
    private lateinit var tvGas: TextView
    private lateinit var tvLight: TextView
    private lateinit var tvSoil: TextView
    private lateinit var tvWater: TextView
    private lateinit var tvAlert: TextView
    private lateinit var btnDot: Button
    private lateinit var btnDash: Button
    private lateinit var btnEnter: Button
    private lateinit var tvPassword: TextView
    private lateinit var btnRefresh: Button

    private var passwordInput = ""
    private val discoveredDevices = mutableMapOf<String, Pair<BluetoothDevice, Int>>() // MAC -> (device, rssi)
    private val deviceNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = BluetoothLEManager(this)
        handler = Handler(Looper.getMainLooper())

        // Ініціалізуємо BLE сканер
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bleScanManager = BleScanManager(bluetoothAdapter) { device, rssi ->
            onDeviceDiscovered(device, rssi)
        }

        initViews()
        setupListeners()
        checkPermissionsAndEnableScan()
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        spinnerDevices = findViewById(R.id.spinnerDevices)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnLedOn = findViewById(R.id.btnLedOn)
        btnLedOff = findViewById(R.id.btnLedOff)
        btnRelayOn = findViewById(R.id.btnRelayOn)
        btnRelayOff = findViewById(R.id.btnRelayOff)
        btnAlarmArm = findViewById(R.id.btnAlarmArm)
        btnAlarmDisarm = findViewById(R.id.btnAlarmDisarm)
        seekServo1 = findViewById(R.id.seekServo1)
        seekServo2 = findViewById(R.id.seekServo2)
        seekFan = findViewById(R.id.seekFan)
        seekLedPwm = findViewById(R.id.seekLedPwm)
        btnServo1Set = findViewById(R.id.btnServo1Set)
        btnServo2Set = findViewById(R.id.btnServo2Set)
        btnFanSet = findViewById(R.id.btnFanSet)
        btnFanStop = findViewById(R.id.btnFanStop)
        btnLedPwmSet = findViewById(R.id.btnLedPwmSet)
        btnMusic1 = findViewById(R.id.btnMusic1)
        btnMusic2 = findViewById(R.id.btnMusic2)
        btnMusicStop = findViewById(R.id.btnMusicStop)
        tvGas = findViewById(R.id.tvGas)
        tvLight = findViewById(R.id.tvLight)
        tvSoil = findViewById(R.id.tvSoil)
        tvWater = findViewById(R.id.tvWater)
        tvAlert = findViewById(R.id.tvAlert)
        btnDot = findViewById(R.id.btnDot)
        btnDash = findViewById(R.id.btnDash)
        btnEnter = findViewById(R.id.btnEnter)
        tvPassword = findViewById(R.id.tvPassword)
        btnRefresh = findViewById(R.id.btnRefresh)
    }

    private fun setupListeners() {
        btnScan.setOnClickListener { scanDevices() }
        btnConnect.setOnClickListener { connectToSelectedDevice() }
        btnDisconnect.setOnClickListener { disconnect() }

        btnLedOn.setOnClickListener { send("a") }
        btnLedOff.setOnClickListener { send("b") }
        btnRelayOn.setOnClickListener { send("c") }
        btnRelayOff.setOnClickListener { send("d") }
        btnAlarmArm.setOnClickListener { send("m") }
        btnAlarmDisarm.setOnClickListener { send("n") }

        btnServo1Set.setOnClickListener { send("t${seekServo1.progress}#") }
        btnServo2Set.setOnClickListener { send("u${seekServo2.progress}#") }
        btnFanSet.setOnClickListener { send("w${seekFan.progress}#") }
        btnFanStop.setOnClickListener { send("s") }
        btnLedPwmSet.setOnClickListener { send("v${seekLedPwm.progress}#") }
        btnMusic1.setOnClickListener { send("e") }
        btnMusic2.setOnClickListener { send("f") }
        btnMusicStop.setOnClickListener { send("g") }

        btnDot.setOnClickListener { passwordInput += "."; tvPassword.text = passwordInput }
        btnDash.setOnClickListener { passwordInput += "-"; tvPassword.text = passwordInput }
        btnEnter.setOnClickListener {
            if (passwordInput == ".--.-.") send("l") else Toast.makeText(this, "Неправильно!", Toast.LENGTH_SHORT).show()
            passwordInput = ""; tvPassword.text = ""
        }

        btnRefresh.setOnClickListener { refreshSensors() }
    }

    private fun checkPermissionsAndEnableScan() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
        } else {
            btnScan.isEnabled = true
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 6–11: потрібна ACCESS_FINE_LOCATION для BLE сканування
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Android 6–11: GPS (Location Services) ОБОВ'ЯЗКОВО увімкнений для BLE сканування
    private fun isLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true // Android 12+ не потребує GPS
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            btnScan.isEnabled = true
        } else {
            Toast.makeText(this, "Дозволи потрібні для пошуку BLE пристроїв!", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanDevices() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Toast.makeText(this, "Bluetooth не підтримується", Toast.LENGTH_SHORT).show()
            return
        }

        if (!adapter.isEnabled) {
            Toast.makeText(this, "Увімкніть Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        // Android 6–11: GPS обов'язковий для BLE сканування!
        if (!isLocationEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Потрібна геолокація")
                .setMessage("На Android до версії 12 для пошуку BLE пристроїв потрібно увімкнути геолокацію (GPS). Увімкнути зараз?")
                .setPositiveButton("Увімкнути") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Скасувати", null)
                .show()
            return
        }

        discoveredDevices.clear()
        deviceNames.clear()
        tvStatus.text = "Сканування..."
        btnScan.isEnabled = false

        // 1. Спочатку показуємо спарені пристрої
        try {
            adapter.bondedDevices?.forEach { device ->
                discoveredDevices[device.address] = Pair(device, -50)
                android.util.Log.d("BLE_SCAN", "Спарений: ${device.name} (${device.address})")
            }
        } catch (e: SecurityException) {
            android.util.Log.e("BLE_SCAN", "SecurityException bondedDevices: ${e.message}")
        }
        updateDeviceList()

        // 2. Запускаємо BLE сканування
        val started = bleScanManager.startScan()
        android.util.Log.d("BLE_SCAN", "startScan() = $started")

        if (!started) {
            Toast.makeText(this, "Не вдалося запустити BLE сканування", Toast.LENGTH_SHORT).show()
            btnScan.isEnabled = true
            return
        }

        tvStatus.text = "Сканування BLE (15 сек)..."

        // 3. Зупиняємо через 15 секунд
        handler.postDelayed({
            bleScanManager.stopScan()
            runOnUiThread {
                updateDeviceList()
                btnScan.isEnabled = true
                tvStatus.text = "Знайдено ${discoveredDevices.size} пристроїв"
            }
        }, 15000)
    }

    private fun onDeviceDiscovered(device: BluetoothDevice, rssi: Int) {
        if (!hasBluetoothPermissions()) return

        val address = device.address
        if (!discoveredDevices.containsKey(address)) {
            discoveredDevices[address] = Pair(device, rssi)
            try {
                android.util.Log.d("BLE_SCAN", "Новий BLE: ${device.name} ($address) rssi=$rssi")
            } catch (e: SecurityException) { /* ігноруємо */ }
            handler.post { updateDeviceList() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateDeviceList() {
        if (!hasBluetoothPermissions()) return

        deviceNames.clear()
        discoveredDevices.forEach { (address, pair) ->
            val name = try { pair.first.name ?: "Невідомий" } catch (e: SecurityException) { "Невідомий" }
            val rssi = pair.second
            deviceNames.add("$name ($address) [${rssi}dBm]")
        }

        if (deviceNames.isEmpty()) {
            tvStatus.text = "Пристроїв не знайдено"
            return
        }

        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDevices.adapter = arrayAdapter
        spinnerDevices.visibility = android.view.View.VISIBLE
        btnConnect.isEnabled = true
        tvStatus.text = "Знайдено ${deviceNames.size} пристроїв"
    }

    @SuppressLint("SetTextI18n", "MissingPermission")
    private fun connectToSelectedDevice() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        val position = spinnerDevices.selectedItemPosition
        if (position == AdapterView.INVALID_POSITION) {
            Toast.makeText(this, "Виберіть пристрій", Toast.LENGTH_SHORT).show()
            return
        }

        val address = discoveredDevices.keys.toList()[position]
        connectedDevice = discoveredDevices[address]?.first ?: return
        tvStatus.text = "Підключення до ${deviceNames[position]}..."

        Thread {
            val success = try {
                bluetoothManager.connect(connectedDevice!!)
                Thread.sleep(1500)
                bluetoothManager.isConnected()
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (success) {
                    tvStatus.text = "Підключено!"
                    btnConnect.isEnabled = false
                    btnDisconnect.isEnabled = true
                    startSensorPolling()
                } else {
                    tvStatus.text = "Помилка підключення"
                    Toast.makeText(this, "Не вдалося підключитися", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun disconnect() {
        bluetoothManager.disconnect()
        tvStatus.text = "Відключено"
        btnConnect.isEnabled = true
        btnDisconnect.isEnabled = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun send(command: String) {
        if (bluetoothManager.isConnected()) {
            Thread { bluetoothManager.send(command) }.start()
        } else {
            Toast.makeText(this, "Не підключено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshSensors() {
        send("h"); send("i"); send("j"); send("k")
    }

    private fun startSensorPolling() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (bluetoothManager.isConnected()) {
                    refreshSensors()
                    handler.postDelayed(this, 2000)
                }
            }
        }, 2000)

        Thread {
            while (bluetoothManager.isConnected()) {
                val data = bluetoothManager.read()
                if (data.isNotEmpty()) {
                    runOnUiThread { processIncoming(data) }
                }
                Thread.sleep(100)
            }
        }.start()
    }

    // Буфер для накопичення даних між пакетами BLE
    private val dataBuffer = StringBuilder()

    @SuppressLint("SetTextI18n")
    private fun processIncoming(data: String) {
        dataBuffer.append(data)

        while (dataBuffer.contains('#')) {
            val endIndex = dataBuffer.indexOf('#')
            val message  = dataBuffer.substring(0, endIndex).trim()
            dataBuffer.delete(0, endIndex + 1)

            android.util.Log.d("BLE_DATA", "Отримано: '$message'")

            when {
                message.contains("danger",     ignoreCase = true) -> tvAlert.text = "⚠️ УВАГА: ГАЗ!"
                message.contains("rain",       ignoreCase = true) -> tvAlert.text = "🌧️ Дощ/Волога!"
                message.contains("hydropenia", ignoreCase = true) -> tvAlert.text = "🌱 Нестача вологи ґрунту!"

                message.equals("armed",    ignoreCase = true) -> {
                    tvAlert.text = "🔒 Охорона УВІМКНЕНА"
                    btnAlarmArm.isEnabled    = false
                    btnAlarmDisarm.isEnabled = true
                }
                message.equals("disarmed", ignoreCase = true) -> {
                    tvAlert.text = "🔓 Охорона ВИМКНЕНА"
                    btnAlarmArm.isEnabled    = true
                    btnAlarmDisarm.isEnabled = false
                }
                message.equals("motion", ignoreCase = true) -> {
                    tvAlert.text = "🚨 ТРИВОГА! Виявлено рух!"
                    triggerVibration()
                }

                // Дані датчиків: перша літера = тип, решта = число
                message.length >= 2 -> {
                    val value = message.substring(1).toIntOrNull()
                    if (value != null) {
                        when (message[0].lowercaseChar()) {
                            'h' -> tvLight.text = "Світло: $value"
                            'i' -> tvGas.text   = "Газ: $value"
                            'j' -> tvSoil.text  = "Ґрунт: $value"
                            'k' -> tvWater.text = "Вода: $value"
                        }
                    }
                }
            }
        }
    }

    private fun triggerVibration() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        val pattern  = longArrayOf(0, 500, 200, 500, 200, 500)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.disconnect()
        bleScanManager.stopScan()
    }
}