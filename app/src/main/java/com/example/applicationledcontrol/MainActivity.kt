package com.example.applicationledcontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.applicationledcontrol.data.BluetoothConnectionManager
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private var lastDeviceName: String? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    BluetoothConnectionManager.disconnect()
                    updateUIStatus()
                }
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (!result.values.all { it }) {
                showStatus(getString(R.string.status_permissions_missing))
            }
        }

    private val deviceListLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Если мы вернулись сюда с RESULT_OK, значит в DeviceListActivity
                // соединение уже успешно установлено.
                lastDeviceName = result.data?.getStringExtra(DeviceListActivity.EXTRA_DEVICE_NAME)
                updateUIStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        val btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        val btnAllOn = findViewById<MaterialButton>(R.id.btnAllOn)
        val btnAuto = findViewById<MaterialButton>(R.id.btnAuto)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)
        val spinnerFloor = findViewById<Spinner>(R.id.spinnerFloor)
        val btnFloorOn = findViewById<MaterialButton>(R.id.btnFloorOn)

        val roomButtons = listOf(
            findViewById<MaterialButton>(R.id.btnRoom1), findViewById<MaterialButton>(R.id.btnRoom2),
            findViewById<MaterialButton>(R.id.btnRoom3), findViewById<MaterialButton>(R.id.btnRoom4),
            findViewById<MaterialButton>(R.id.btnRoom5), findViewById<MaterialButton>(R.id.btnRoom6),
            findViewById<MaterialButton>(R.id.btnRoom7), findViewById<MaterialButton>(R.id.btnRoom8)
        )

        val floors = (1..19).map { "Этаж $it" }
        spinnerFloor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, floors)

        ensureRuntimePermissions()
        updateUIStatus()

        btnConnect.setOnClickListener {
            if (hasRuntimePermissions()) {
                deviceListLauncher.launch(Intent(this, DeviceListActivity::class.java))
            } else {
                ensureRuntimePermissions()
            }
        }

        btnAllOn.setOnClickListener { sendBluetoothCommand("11111") }
        btnAuto.setOnClickListener { sendBluetoothCommand("80") }
        btnOff.setOnClickListener { sendBluetoothCommand("60") }

        btnFloorOn.setOnClickListener {
            val floor = spinnerFloor.selectedItemPosition + 1
            sendBluetoothCommand(generateFloorCommand(floor))
        }

        roomButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val floor = spinnerFloor.selectedItemPosition + 1
                val room = index + 1
                sendBluetoothCommand(generateCommand(floor, room))
            }
        }
    }

    private fun generateFloorCommand(floor: Int): String {
        return (10000 + floor).toString()
    }

    private fun generateCommand(floor: Int, room: Int): String {
        if (floor == 1) {
            val map = mapOf(1 to "111", 2 to "112", 3 to "113", 4 to "114", 5 to "115", 6 to "116", 7 to "117")
            return map[room] ?: "111"
        } else {
            val fIdx = floor - 2
            val rIdx = room - 1
            val code = (fIdx * 8) + 8 + rIdx
            var cmd = code * 10
            if (cmd == 80 && rIdx == 0) cmd = 81
            return cmd.toString()
        }
    }

    private fun sendBluetoothCommand(command: String) {
        if (!BluetoothConnectionManager.send(command)) {
            Toast.makeText(this, "Связь потеряна", Toast.LENGTH_SHORT).show()
            updateUIStatus()
        }
    }

    private fun updateUIStatus() {
        if (BluetoothConnectionManager.isConnected()) {
            showStatus(getString(R.string.status_connected, lastDeviceName ?: "Устройство"))
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_secondary))
        } else {
            showStatus(getString(R.string.status_device_not_connected))
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_secondary))
        }
    }

    private fun showStatus(message: String) {
        tvStatus.text = message
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        updateUIStatus()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothConnectionManager.disconnect()
    }

    private fun hasRuntimePermissions(): Boolean {
        return runtimePermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureRuntimePermissions() {
        if (!hasRuntimePermissions()) {
            permissionLauncher.launch(runtimePermissions())
        }
    }

    private fun runtimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}