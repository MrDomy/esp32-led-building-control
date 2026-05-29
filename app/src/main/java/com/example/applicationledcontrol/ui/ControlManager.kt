package com.example.applicationledcontrol.ui

import com.example.applicationledcontrol.data.Esp32HttpClient
import com.example.applicationledcontrol.domain.BuildingProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ControlManager(private val scope: CoroutineScope) {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    private val sendMutex = Mutex()

    fun updateHost(host: String) {
        val normalizedHost = Esp32HttpClient.normalizeHost(host)
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
        scope.launch(Dispatchers.IO) {
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

            runCatching { Esp32HttpClient.ping(host) }
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            lastCommand = "PING",
                            lastResponse = response.ifBlank { "OK" }
                        )
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
        scope.launch(Dispatchers.IO) {
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

        launchCommands(listOf(BuildingProtocol.RELAY_ON, BuildingProtocol.COMMAND_ALL_ON))
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
                BuildingProtocol.RELAY_ON,
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
                BuildingProtocol.RELAY_ON,
                BuildingProtocol.getFloorCommand(floor)
            )
        )
    }

    fun turnOffFloor(floor: Int) {
        if (!BuildingProtocol.isValidFloor(floor)) {
            return
        }

        val currentRooms = _uiState.value.roomStates[floor].orEmpty()
        if (currentRooms.isEmpty()) {
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

        val offCommands = BuildingProtocol.getFloorOffCommands(floor)
        launchCommands(offCommands + if (newState.isEmpty()) listOf(BuildingProtocol.RELAY_OFF) else emptyList())
    }

    fun setBuildingColor(colorCommand: String) {
        launchCommands(listOf(colorCommand))
    }
}
