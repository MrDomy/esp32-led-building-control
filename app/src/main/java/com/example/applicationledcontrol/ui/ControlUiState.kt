package com.example.applicationledcontrol.ui

/**
 * UI state for the main screen.
 */
data class ControlUiState(
    val esp32Host: String = "192.168.4.1",
    val isConnected: Boolean = false,
    val allowOfflineInteraction: Boolean = false,
    val selectedFloor: Int = 1,
    val lastCommand: String? = null,
    val lastResponse: String? = null,
    val isRelayActive: Boolean = false,
    val currentMode: String = "Manual",
    val roomStates: Map<Int, Set<Int>> = emptyMap(),
    val roomColors: Map<String, Int> = emptyMap()
)
