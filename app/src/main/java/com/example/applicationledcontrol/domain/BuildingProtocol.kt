package com.example.applicationledcontrol.domain

/**
 * Command mapping shared by the Android app and ESP32 firmware.
 * Uses structured command format: F<floor>W<window>S<state>
 * F: Floor 1..19 (0 = all)
 * W: Window 1..8 (0 = all)
 * S: State 1 = ON, 0 = OFF, 2 = Auto, C0..C3 = Preset Colors
 */
object BuildingProtocol {

    const val RELAY_ON = "F0W0S1"
    const val RELAY_OFF = "F0W0S0"

    const val MODE_AUTO = "F0W0S2"
    const val MODE_MANUAL = "MODE_MANUAL" // Used internally for UI mode state representation

    const val COMMAND_ALL_ON = "F0W0S1"
    const val COMMAND_ALL_OFF = "F0W0S0"

    const val COLOR_WHITE = "F0W0SC0"
    const val COLOR_GREEN = "F0W0SC1"
    const val COLOR_YELLOW = "F0W0SC2"
    const val COLOR_RED = "F0W0SC3"

    fun isValidFloor(floor: Int): Boolean = floor in 1..19

    fun roomCountForFloor(floor: Int): Int {
        return 8
    }

    fun isValidRoom(floor: Int, room: Int): Boolean {
        return room in 1..roomCountForFloor(floor)
    }

    fun getFloorCommand(floor: Int): String {
        require(isValidFloor(floor)) { "Unsupported floor: $floor" }
        return "F${floor}W0S1"
    }

    fun getFloorOffCommand(floor: Int): String {
        require(isValidFloor(floor)) { "Unsupported floor: $floor" }
        return "F${floor}W0S0"
    }

    fun getRoomCommand(floor: Int, room: Int): String {
        require(isValidRoom(floor, room)) { "Unsupported room: floor=$floor room=$room" }
        return "F${floor}W${room}S1"
    }

    fun getRoomOffCommand(floor: Int, room: Int): String {
        require(isValidRoom(floor, room)) { "Unsupported room: floor=$floor room=$room" }
        return "F${floor}W${room}S0"
    }
}
