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
    private val roomButtons = mutableListOf<MaterialButton>()

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        try {
            setContentView(R.layout.activity_main)
            controlManager = androidx.lifecycle.ViewModelProvider(this)[ControlManager::class.java]
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
        val btnSettings = findViewById<MaterialButton>(R.id.btnSettings)
        val floorChipGroup = findViewById<ChipGroup>(R.id.floorChipGroup)
        val btnFloorOn = findViewById<MaterialButton>(R.id.btnFloorOn)
        val btnFloorOff = findViewById<MaterialButton>(R.id.btnFloorOff)
        val btnAllOn = findViewById<MaterialButton>(R.id.btnAllOn)
        val btnAuto = findViewById<MaterialButton>(R.id.btnAuto)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)

        val savedHost = preferences.getString(PREF_KEY_HOST, DEFAULT_HOST).orEmpty()
            .ifBlank { DEFAULT_HOST }
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
                    setOnLongClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showColorChooserDialog(i, room = 0)
                        true
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
                    setOnLongClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showColorChooserDialog(controlManager.uiState.value.selectedFloor, i)
                        true
                    }
                }
                roomButtons.add(btn)
                grid.addView(btn)
            }
        }

        btnFloorOn?.setOnClickListener { controlManager.turnOnFloor(controlManager.uiState.value.selectedFloor) }
        btnFloorOff?.setOnClickListener { controlManager.turnOffFloor(controlManager.uiState.value.selectedFloor) }
        btnAllOn?.setOnClickListener { controlManager.turnOnAll() }
        btnAuto?.setOnClickListener { controlManager.setAutoMode() }
        btnOff?.setOnClickListener { controlManager.turnOffAll() }

        findViewById<View>(R.id.btnMainColorDefault)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_WHITE)
        }
        findViewById<View>(R.id.btnMainColorGreen)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_GREEN)
        }
        findViewById<View>(R.id.btnMainColorYellow)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_YELLOW)
        }
        findViewById<View>(R.id.btnMainColorRed)?.setOnClickListener {
            controlManager.setBuildingColor(BuildingProtocol.COLOR_RED)
        }

        updateControlsState(false, false)
        updateRoomButtons(controlManager.uiState.value)
    }

    private fun updateControlsState(isConnected: Boolean, canInteractOffline: Boolean) {
        val floorChipGroup = findViewById<ChipGroup>(R.id.floorChipGroup)
        val btnFloorOn = findViewById<MaterialButton>(R.id.btnFloorOn)
        val btnFloorOff = findViewById<MaterialButton>(R.id.btnFloorOff)
        val btnAllOn = findViewById<MaterialButton>(R.id.btnAllOn)
        val btnAuto = findViewById<MaterialButton>(R.id.btnAuto)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)
        val btnColorDefault = findViewById<View>(R.id.btnMainColorDefault)
        val btnColorGreen = findViewById<View>(R.id.btnMainColorGreen)
        val btnColorYellow = findViewById<View>(R.id.btnMainColorYellow)
        val btnColorRed = findViewById<View>(R.id.btnMainColorRed)

        val canInteract = isConnected || canInteractOffline
        val alpha = if (canInteract) 1.0f else 0.5f

        floorChipGroup?.isEnabled = canInteract
        floorChipGroup?.alpha = alpha
        btnFloorOn?.isEnabled = canInteract
        btnFloorOff?.isEnabled = canInteract
        btnAllOn?.isEnabled = canInteract
        btnAuto?.isEnabled = canInteract
        btnOff?.isEnabled = canInteract

        btnColorDefault?.isEnabled = canInteract
        btnColorDefault?.alpha = alpha
        btnColorGreen?.isEnabled = canInteract
        btnColorGreen?.alpha = alpha
        btnColorYellow?.isEnabled = canInteract
        btnColorYellow?.alpha = alpha
        btnColorRed?.isEnabled = canInteract
        btnColorRed?.alpha = alpha
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)
        val switchDarkMode = dialogView.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val dialogEtHost = dialogView.findViewById<TextInputEditText>(R.id.etEsp32Host)
        val dialogBtnConnect = dialogView.findViewById<MaterialButton>(R.id.btnConnect)
        val dialogBtnOfflineMode = dialogView.findViewById<MaterialButton>(R.id.btnOfflineMode)

        // Populate values
        val currentHost = controlManager.uiState.value.esp32Host
        dialogEtHost?.setText(currentHost)

        fun updateDialogOfflineButtonText(offlineEnabled: Boolean) {
            dialogBtnOfflineMode?.text = getString(
                if (offlineEnabled) R.string.offline_mode_on else R.string.offline_mode_off
            )
        }
        updateDialogOfflineButtonText(controlManager.uiState.value.allowOfflineInteraction)

        switchDarkMode?.isChecked = ThemeManager.isDarkMode(this)
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            dialog.dismiss()
            window?.decorView?.post {
                ThemeManager.setDarkMode(applicationContext, isChecked)
            }
        }

        dialogBtnConnect?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val host = dialogEtHost?.text?.toString().orEmpty().trim()
            if (host.isBlank()) {
                Toast.makeText(this, R.string.host_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveHost(host)
            controlManager.updateHost(host)
            controlManager.pingHost()
        }

        dialogBtnOfflineMode?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            controlManager.toggleOfflineInteraction()
        }

        val job = lifecycleScope.launch {
            controlManager.uiState.collect { state ->
                updateDialogOfflineButtonText(state.allowOfflineInteraction)
            }
        }

        dialog.setOnDismissListener {
            job.cancel()
            val host = dialogEtHost?.text?.toString().orEmpty().trim()
            if (host.isNotBlank()) {
                saveHost(host)
                controlManager.updateHost(host)
            }
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
                    R.color.surface_panel
                )
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.on_surface_sub))
            } else if (activeRooms.contains(roomNumber)) {
                val colorIdx = state.roomColors["F${state.selectedFloor}W$roomNumber"] ?: 0
                val (bgRes, textRes) = when (colorIdx) {
                    1 -> Pair(R.color.preset_green, R.color.white)
                    2 -> Pair(R.color.preset_yellow, R.color.black)
                    3 -> Pair(R.color.preset_red, R.color.white)
                    else -> Pair(R.color.brand_primary, R.color.brand_on_primary)
                }
                button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, bgRes)
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, textRes))
            } else {
                button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
                    this,
                    R.color.surface_panel
                )
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.on_surface_main))
            }
        }
    }

    private fun showColorChooserDialog(floor: Int, room: Int) {
        val colors = arrayOf(
            getString(R.string.color_default), // Тёплый белый
            getString(R.string.color_green),   // Зелёный
            getString(R.string.color_yellow),  // Жёлтый
            getString(R.string.color_red)      // Красный
        )

        val title = if (room == 0) {
            "Цвет для всего этажа $floor"
        } else {
            "Цвет для комнаты $room (этаж $floor)"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(colors) { dialog, which ->
                val colorCommand = if (room == 0) {
                    "F${floor}W0SC$which"
                } else {
                    "F${floor}W${room}SC$which"
                }
                controlManager.setCustomColor(floor, room, which, colorCommand)
                dialog.dismiss()
            }
            .show()
    }

    override fun onStop() {
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
