package com.example.applicationledcontrol.ui

import com.example.applicationledcontrol.data.Esp32HttpClient
import com.example.applicationledcontrol.domain.BuildingProtocol
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ControlManager : ViewModel() {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    private val sendMutex = Mutex()

    fun updateHost(host: String) {
        val normalizedHost = Esp32HttpClient.normalizeHost(host)
        if (_uiState.value.esp32Host == normalizedHost) return
        _uiState.update {
            it.copy(
                esp32Host = normalizedHost,
                isConnected = false,
                lastResponse = null
            )
        }
    }

    fun toggleOfflineInteraction() {
        _uiState.update { it.copy(allowOfflineInteraction = !it.allowOfflineInteraction) }
    }

    fun setOfflineInteractionEnabled(enabled: Boolean) {
        _uiState.update { it.copy(allowOfflineInteraction = enabled) }
    }

    fun selectFloor(floor: Int) {
        _uiState.update { it.copy(selectedFloor = floor) }
    }

    fun pingHost() {
        viewModelScope.launch(Dispatchers.IO) {
            val host = _uiState.value.esp32Host
            if (host.isBlank()) {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        lastResponse = "ESP32 host пуст"
                    )
                }
                return@launch
            }

            runCatching { Esp32HttpClient.fetchStatus(host) }
                .onSuccess { response ->
                    runCatching {
                        val json = org.json.JSONObject(response)
                        val mode = json.optString("mode", "Manual")
                        val relay = json.optBoolean("relay", false)
                        
                        // Parse active rooms
                        val activeArray = json.optJSONArray("active")
                        val roomStates = mutableMapOf<Int, Set<Int>>()
                        if (activeArray != null) {
                            for (i in 0 until activeArray.length()) {
                                val item = activeArray.getString(i)
                                val match = Regex("F(\\d+)W(\\d+)").matchEntire(item)
                                if (match != null) {
                                    val f = match.groupValues[1].toInt()
                                    val r = match.groupValues[2].toInt()
                                    val current = roomStates[f] ?: emptySet()
                                    roomStates[f] = current + r
                                }
                            }
                        }

                        // Parse room colors
                        val colorsObj = json.optJSONObject("colors")
                        val roomColors = mutableMapOf<String, Int>()
                        if (colorsObj != null) {
                            val keys = colorsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val colorVal = colorsObj.getInt(key)
                                roomColors[key] = colorVal
                            }
                        }

                        _uiState.update {
                            it.copy(
                                isConnected = true,
                                lastCommand = "STATUS",
                                lastResponse = "Синхронизировано",
                                currentMode = mode,
                                isRelayActive = relay,
                                roomStates = roomStates,
                                roomColors = roomColors
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isConnected = true,
                                lastCommand = "STATUS",
                                lastResponse = "Ошибка JSON: ${error.message}"
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            lastResponse = error.message ?: "ESP32 не отвечает"
                        )
                    }
                }
        }
    }

    private fun launchCommands(commands: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMutex.withLock {
                val state = _uiState.value
                val host = state.esp32Host

                if (!state.allowOfflineInteraction && !state.isConnected) {
                    _uiState.update {
                        it.copy(lastResponse = "ESP32 не подключен")
                    }
                    return@withLock
                }

                commands.forEachIndexed { index, command ->
                    if (command.isNotBlank()) {
                        if (state.allowOfflineInteraction) {
                            _uiState.update { current ->
                                current.copy(
                                    lastCommand = command,
                                    lastResponse = "Офлайн-режим"
                                )
                            }
                        } else {
                            runCatching { Esp32HttpClient.sendCommand(host, command) }
                                .onSuccess { response ->
                                    _uiState.update { current ->
                                        current.copy(
                                            lastCommand = command,
                                            lastResponse = response.ifBlank { "OK" },
                                            isConnected = true
                                        )
                                    }
                                }
                                .onFailure { error ->
                                    _uiState.update { current ->
                                        current.copy(
                                            lastCommand = command,
                                            lastResponse = error.message ?: "HTTP-запрос не удался",
                                            isConnected = false
                                        )
                                    }
                                    return@withLock
                                }
                        }
                    }

                    if (index < commands.lastIndex) {
                        delay(25)
                    }
                }
            }
        }
    }

    fun turnOnAll() {
        val newState = (1..19).associateWith { floor ->
            (1..BuildingProtocol.roomCountForFloor(floor)).toSet()
        }

        _uiState.update {
            it.copy(
                roomStates = newState,
                currentMode = "Manual",
                isRelayActive = true
            )
        }

        launchCommands(listOf(BuildingProtocol.COMMAND_ALL_ON))
    }

    fun turnOffAll() {
        _uiState.update {
            it.copy(
                roomStates = emptyMap(),
                currentMode = "Manual",
                isRelayActive = false
            )
        }

        launchCommands(listOf(BuildingProtocol.RELAY_OFF))
    }

    fun setAutoMode() {
        _uiState.update {
            it.copy(
                currentMode = "Auto",
                roomStates = emptyMap(),
                isRelayActive = true
            )
        }

        launchCommands(listOf(BuildingProtocol.MODE_AUTO))
    }

    fun toggleRoom(floor: Int, room: Int) {
        if (!BuildingProtocol.isValidRoom(floor, room)) {
            return
        }

        val currentState = _uiState.value.roomStates
        val currentFloorRooms = currentState[floor] ?: emptySet()
        val isCurrentlyOn = room in currentFloorRooms

        val newState = if (isCurrentlyOn) {
            val remainingRooms = currentFloorRooms - room
            if (remainingRooms.isEmpty()) {
                currentState - floor
            } else {
                currentState + (floor to remainingRooms)
            }
        } else {
            currentState + (floor to (currentFloorRooms + room))
        }

        _uiState.update {
            it.copy(
                roomStates = newState,
                currentMode = "Manual",
                isRelayActive = newState.values.any { rooms -> rooms.isNotEmpty() }
            )
        }

        if (isCurrentlyOn) {
            if (newState.isEmpty()) {
                launchCommands(listOf(BuildingProtocol.RELAY_OFF))
            } else {
                launchCommands(listOf(BuildingProtocol.getRoomOffCommand(floor, room)))
            }
            return
        }

        launchCommands(
            listOf(
                BuildingProtocol.getRoomCommand(floor, room)
            )
        )
    }

    fun turnOnFloor(floor: Int) {
        if (!BuildingProtocol.isValidFloor(floor)) {
            return
        }

        val roomCount = BuildingProtocol.roomCountForFloor(floor)
        val floorRooms = (1..roomCount).toSet()
        val newState = _uiState.value.roomStates + (floor to floorRooms)

        _uiState.update {
            it.copy(
                roomStates = newState,
                currentMode = "Manual",
                isRelayActive = true
            )
        }

        launchCommands(
            listOf(
                BuildingProtocol.getFloorCommand(floor)
            )
        )
    }

    fun turnOffFloor(floor: Int) {
        if (!BuildingProtocol.isValidFloor(floor)) {
            return
        }

        val newState = _uiState.value.roomStates - floor
        _uiState.update {
            it.copy(
                roomStates = newState,
                currentMode = "Manual",
                isRelayActive = newState.values.any { rooms -> rooms.isNotEmpty() }
            )
        }

        val offCommand = BuildingProtocol.getFloorOffCommand(floor)
        launchCommands(listOf(offCommand) + if (newState.isEmpty()) listOf(BuildingProtocol.RELAY_OFF) else emptyList())
    }

    fun setBuildingColor(colorCommand: String) {
        val colorIdx = colorCommand.substringAfter("SC").toIntOrNull() ?: 0
        _uiState.update { state ->
            val tempColors = state.roomColors.toMutableMap()
            for (f in 1..19) {
                for (r in 1..8) {
                    tempColors["F${f}W$r"] = colorIdx
                }
            }
            state.copy(
                roomColors = tempColors,
                currentMode = "Manual"
            )
        }
        launchCommands(listOf(colorCommand))
    }

    fun setCustomColor(floor: Int, room: Int, colorIndex: Int, colorCommand: String) {
        _uiState.update { state ->
            val updatedColors = if (room == 0) {
                val tempColors = state.roomColors.toMutableMap()
                for (r in 1..8) {
                    tempColors["F${floor}W$r"] = colorIndex
                }
                tempColors
            } else {
                state.roomColors + ("F${floor}W$room" to colorIndex)
            }

            val updatedStates = if (room == 0) {
                val floorRooms = (1..BuildingProtocol.roomCountForFloor(floor)).toSet()
                state.roomStates + (floor to floorRooms)
            } else {
                val currentFloorRooms = state.roomStates[floor] ?: emptySet()
                state.roomStates + (floor to (currentFloorRooms + room))
            }

            state.copy(
                roomColors = updatedColors,
                roomStates = updatedStates,
                currentMode = "Manual",
                isRelayActive = true
            )
        }
        launchCommands(listOf(colorCommand))
    }
}
