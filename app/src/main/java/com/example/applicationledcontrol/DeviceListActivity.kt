package com.example.applicationledcontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.applicationledcontrol.data.BluetoothConnectionManager

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
)

class DeviceListActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val discoveredDevices = LinkedHashMap<String, BluetoothDeviceItem>()
    private val deviceLabels = mutableListOf<String>()
    private var discoveryActive = false

    private lateinit var listView: ListView
    private lateinit var emptyState: TextView
    private lateinit var adapter: ArrayAdapter<String>

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> handleDeviceFound(intent)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (discoveryActive) scheduleDiscoveryRestart()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        listView = findViewById(R.id.deviceList)
        emptyState = findViewById(R.id.tvEmptyState)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceLabels)
        listView.adapter = adapter

        btnRefresh.setOnClickListener { restartDiscovery() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = discoveredDevices.values.elementAtOrNull(position) ?: return@setOnItemClickListener

            // Останавливаем поиск
            cancelDiscovery()
            discoveryActive = false

            // Показываем пользователю, что идет процесс
            Toast.makeText(this, "Подключение к ${selected.name}...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                // Попытка реального подключения
                val isSuccess = BluetoothConnectionManager.connect(selected.address)

                withContext(Dispatchers.Main) {
                    if (isSuccess) {
                        // Только если подключение реально установлено, возвращаемся в MainActivity
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_DEVICE_ADDRESS, selected.address)
                            putExtra(EXTRA_DEVICE_NAME, selected.name)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else {
                        // Если устройство не ответило — остаемся в списке и выводим ошибку
                        Toast.makeText(this@DeviceListActivity, "Не удалось подключиться к ${selected.name}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(discoveryReceiver, filter)
        restartDiscovery()
    }

    override fun onStop() {
        super.onStop()
        discoveryActive = false
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(discoveryReceiver) } catch (e: Exception) {}
        cancelDiscovery()
    }

    private fun restartDiscovery() {
        discoveredDevices.clear()
        if (!hasBluetoothPermission()) {
            renderDevices(getString(R.string.permission_needed_for_devices))
            return
        }

        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null || !btAdapter.isEnabled) {
            renderDevices(getString(R.string.bluetooth_disabled))
            return
        }

        btAdapter.bondedDevices?.forEach { device ->
            val name = (if (hasBluetoothConnectPermission()) device.name else null) ?: getString(R.string.unknown_device_name)
            discoveredDevices[device.address] = BluetoothDeviceItem(name, device.address)
        }

        cancelDiscovery()
        discoveryActive = true
        btAdapter.startDiscovery()
        renderDevices(getString(R.string.scanning_devices))
    }

    private fun handleDeviceFound(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        device?.let {
            val name = (if (hasBluetoothConnectPermission()) it.name else null) ?: getString(R.string.unknown_device_name)
            discoveredDevices[it.address] = BluetoothDeviceItem(name, it.address)
            renderDevices()
        }
    }

    private fun renderDevices(emptyMessage: String? = null) {
        deviceLabels.clear()
        deviceLabels.addAll(discoveredDevices.values.map { "${it.name}\n${it.address}" })
        adapter.notifyDataSetChanged()

        val isEmpty = deviceLabels.isEmpty()
        emptyState.text = emptyMessage ?: getString(R.string.paired_devices_hint)
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun scheduleDiscoveryRestart() {
        handler.postDelayed({ if (discoveryActive) restartDiscovery() }, 2000L)
    }

    private fun cancelDiscovery() {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
    }

    private fun hasBluetoothPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
    }
}