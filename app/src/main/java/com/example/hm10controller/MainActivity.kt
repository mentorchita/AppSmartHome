package com.example.hm10controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.os.Build
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothManager: BluetoothManager
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
    private val deviceList = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = BluetoothManager()
        handler = Handler(Looper.getMainLooper())

        initViews()
        setupListeners()
        checkPermissionsAndEnableScan()
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
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
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
            Toast.makeText(this, "Дозволи потрібні!", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanDevices() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        val adapter = getDefaultAdapter() ?: run {
            Toast.makeText(this, "Bluetooth не підтримується", Toast.LENGTH_SHORT).show()
            return
        }

        if (!adapter.isEnabled) {
            Toast.makeText(this, "Увімкніть Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

// БЕЗПЕЧНИЙ ДОСТУП ДО bondedDevices
        deviceList.clear()
        deviceNames.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

                adapter.bondedDevices?.forEach { device ->
                    deviceList.add(device)
                    deviceNames.add("${device.name ?: "Невідомий"} (${device.address})")
                }
            } else {
                Toast.makeText(this, "Потрібен дозвіл Bluetooth для перегляду спарених пристроїв", Toast.LENGTH_LONG).show()
                requestBluetoothPermissions()
                return
            }
        } else {
            // Для Android < 12
            adapter.bondedDevices?.forEach { device ->
                deviceList.add(device)
                deviceNames.add("${device.name ?: "Невідомий"} (${device.address})")
            }
        }

        if (deviceNames.isEmpty()) {
            Toast.makeText(this, "Немає зв'язаних пристроїв", Toast.LENGTH_SHORT).show()
            return
        }

        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDevices.adapter = arrayAdapter
        spinnerDevices.visibility = android.view.View.VISIBLE
        btnConnect.isEnabled = true
        Toast.makeText(this, "Знайдено ${deviceNames.size} пристроїв", Toast.LENGTH_SHORT).show()
    }

    private fun connectToSelectedDevice() {
        val position = spinnerDevices.selectedItemPosition
        if (position == AdapterView.INVALID_POSITION) {
            Toast.makeText(this, "Виберіть пристрій", Toast.LENGTH_SHORT).show()
            return
        }

        connectedDevice = deviceList[position]
        tvStatus.text = "Підключення до ${deviceNames[position]}..."

        Thread {
            val success = try {
                bluetoothManager.connect(connectedDevice!!)
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

    private fun processIncoming(data: String) {
        when {
            data.contains("danger") -> tvAlert.text = "УВАГА: ГАЗ!"
            data.contains("rain") -> tvAlert.text = "Дощ!"
            data.contains("hydropenia") -> tvAlert.text = "Нестача вологи!"
            data.matches(Regex("\\d+")) -> {
                val value = data.trim().toIntOrNull() ?: return
                when {
                    tvGas.text.startsWith("Газ") -> tvGas.text = "Газ: $value"
                    tvLight.text.startsWith("Світло") -> tvLight.text = "Світло: $value"
                    tvSoil.text.startsWith("Ґрунт") -> {
                        tvSoil.text = "Ґрунт: $value"
                    }
                    tvWater.text.startsWith("Вода") -> tvWater.text = "Вода: $value"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.disconnect()
    }
}