package com.example.applicationledcontrol

import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.applicationledcontrol.domain.BuildingProtocol
import com.example.applicationledcontrol.ui.ControlManager
import com.example.applicationledcontrol.ui.ControlUiState
import com.example.applicationledcontrol.ui.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var controlManager: ControlManager
    private var tvStatus: TextView? = null
    private var roomGrid: GridLayout? = null
    private var etHost: TextInputEditText? = null
    private var btnOfflineMode: MaterialButton? = null
    private val roomButtons = mutableListOf<MaterialButton>()

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        try {
            setContentView(R.layout.activity_main)
            controlManager = ControlManager(lifecycleScope)
            initViews()
            setupObservers()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "UI Error: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        roomGrid = findViewById(R.id.roomGrid)
        etHost = findViewById(R.id.etEsp32Host)
        val btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        btnOfflineMode = findViewById(R.id.btnOfflineMode)
        val btnSettings = findViewById<MaterialButton>(R.id.btnSettings)
        val floorChipGroup = findViewById<ChipGroup>(R.id.floorChipGroup)
        val btnFloorOn = findViewById<MaterialButton>(R.id.btnFloorOn)
        val btnFloorOff = findViewById<MaterialButton>(R.id.btnFloorOff)
        val btnAllOn = findViewById<MaterialButton>(R.id.btnAllOn)
        val btnAuto = findViewById<MaterialButton>(R.id.btnAuto)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)

        val savedHost = preferences.getString(PREF_KEY_HOST, DEFAULT_HOST).orEmpty()
            .ifBlank { DEFAULT_HOST }
        etHost?.setText(savedHost)
        controlManager.updateHost(savedHost)

        btnSettings?.setOnClickListener { showSettingsDialog() }

        floorChipGroup?.let { group ->
            for (i in 1..19) {
                val chip = Chip(this).apply {
                    text = getString(R.string.floor_chip_label, i)
                    isCheckable = true
                    id = View.generateViewId()
                    if (i == 1) isChecked = true
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        controlManager.selectFloor(i)
                    }
                }
                group.addView(chip)
            }
        }

        roomGrid?.let { grid ->
            grid.removeAllViews()
            roomButtons.clear()
            for (i in 1..8) {
                val btn = MaterialButton(this).apply {
                    text = i.toString()
                    val params = GridLayout.LayoutParams()
                    params.width = 0
                    params.height = GridLayout.LayoutParams.WRAP_CONTENT
                    params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    params.setMargins(4, 4, 4, 4)
                    layoutParams = params

                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        controlManager.toggleRoom(controlManager.uiState.value.selectedFloor, i)
                    }
                }
                roomButtons.add(btn)
                grid.addView(btn)
            }
        }

        btnConnect?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val host = etHost?.text?.toString().orEmpty().trim()
            if (host.isBlank()) {
                Toast.makeText(this, R.string.host_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveHost(host)
            controlManager.updateHost(host)
            controlManager.pingHost()
        }

        btnOfflineMode?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            controlManager.toggleOfflineInteraction()
        }

        btnFloorOn?.setOnClickListener { controlManager.turnOnFloor(controlManager.uiState.value.selectedFloor) }
        btnFloorOff?.setOnClickListener { controlManager.turnOffFloor(controlManager.uiState.value.selectedFloor) }
        btnAllOn?.setOnClickListener { controlManager.turnOnAll() }
        btnAuto?.setOnClickListener { controlManager.setAutoMode() }
        btnOff?.setOnClickListener { controlManager.turnOffAll() }

        updateControlsState(false, false)
        updateRoomButtons(controlManager.uiState.value)
        updateOfflineModeButton(controlManager.uiState.value.allowOfflineInteraction)
    }

    private fun updateControlsState(isConnected: Boolean, canInteractOffline: Boolean) {
        val floorChipGroup = findViewById<ChipGroup>(R.id.floorChipGroup)
        val btnFloorOn = findViewById<MaterialButton>(R.id.btnFloorOn)
        val btnFloorOff = findViewById<MaterialButton>(R.id.btnFloorOff)
        val btnAllOn = findViewById<MaterialButton>(R.id.btnAllOn)
        val btnAuto = findViewById<MaterialButton>(R.id.btnAuto)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)
        val btnConnect = findViewById<MaterialButton>(R.id.btnConnect)

        val canInteract = isConnected || canInteractOffline
        val alpha = if (canInteract) 1.0f else 0.5f

        floorChipGroup?.isEnabled = canInteract
        floorChipGroup?.alpha = alpha
        btnFloorOn?.isEnabled = canInteract
        btnFloorOff?.isEnabled = canInteract
        btnAllOn?.isEnabled = canInteract
        btnAuto?.isEnabled = canInteract
        btnOff?.isEnabled = canInteract
        btnConnect?.isEnabled = true
        etHost?.isEnabled = true
        btnOfflineMode?.isEnabled = true
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)
        val switchDarkMode = dialogView.findViewById<SwitchMaterial>(R.id.switchDarkMode)

        switchDarkMode?.isChecked = ThemeManager.isDarkMode(this)
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            dialog.dismiss()
            window?.decorView?.post {
                ThemeManager.setDarkMode(applicationContext, isChecked)
            }
        }

        dialogView.findViewById<View>(R.id.btnColorDefault)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_WHITE)
        }
        dialogView.findViewById<View>(R.id.btnColorGreen)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_GREEN)
        }
        dialogView.findViewById<View>(R.id.btnColorYellow)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_YELLOW)
        }
        dialogView.findViewById<View>(R.id.btnColorRed)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_RED)
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controlManager.uiState.collect { state ->
                    updateUI(state)
                    updateRoomButtons(state)
                    updateControlsState(state.isConnected, state.allowOfflineInteraction)
                    updateOfflineModeButton(state.allowOfflineInteraction)
                }
            }
        }
    }

    private fun updateUI(state: ControlUiState) {
        val statusText = tvStatus ?: return
        statusText.text = when {
            state.isConnected -> getString(R.string.status_connected, state.esp32Host)
            state.allowOfflineInteraction -> getString(R.string.status_offline_mode)
            !state.lastResponse.isNullOrBlank() -> getString(
                R.string.status_connection_error,
                state.lastResponse
            )
            else -> getString(
                R.string.status_device_not_connected
            )
        }
    }

    private fun updateRoomButtons(state: ControlUiState) {
        val activeRooms = state.roomStates[state.selectedFloor] ?: emptySet()
        val canInteract = state.isConnected || state.allowOfflineInteraction

        roomButtons.forEachIndexed { index, button ->
            val roomNumber = index + 1
            val isValidRoom = BuildingProtocol.isValidRoom(state.selectedFloor, roomNumber)
            button.isEnabled = canInteract && isValidRoom
            button.alpha = if (isValidRoom) 1.0f else 0.35f

            if (!isValidRoom) {
                button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
                    this,
                    android.R.color.darker_gray
                )
                button.setTextColor(Color.WHITE)
            } else if (activeRooms.contains(roomNumber)) {
                button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
                    this,
                    R.color.brand_primary
                )
                button.setTextColor(Color.WHITE)
            } else {
                button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
                    this,
                    android.R.color.darker_gray
                )
                button.setTextColor(Color.WHITE)
            }
        }
    }

    private fun updateOfflineModeButton(enabled: Boolean) {
        btnOfflineMode?.text = getString(
            if (enabled) R.string.offline_mode_on else R.string.offline_mode_off
        )
    }

    override fun onStop() {
        saveHost(etHost?.text?.toString().orEmpty().trim())
        super.onStop()
    }

    private fun saveHost(host: String) {
        val normalizedHost = host.ifBlank { DEFAULT_HOST }
        preferences.edit().putString(PREF_KEY_HOST, normalizedHost).apply()
    }

    companion object {
        private const val PREFS_NAME = "esp32_control"
        private const val PREF_KEY_HOST = "esp32_host"
        private const val DEFAULT_HOST = "192.168.4.1"
    }
}
