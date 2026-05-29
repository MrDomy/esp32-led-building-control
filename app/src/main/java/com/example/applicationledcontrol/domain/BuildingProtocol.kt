package com.example.applicationledcontrol.domain

/**
 * Command mapping shared by the Android app and ESP32 firmware.
 */
object BuildingProtocol {

    const val RELAY_ON = "50"
    const val RELAY_OFF = "52"

    const val MODE_AUTO = "80"
    const val MODE_MANUAL = "60"

    const val COMMAND_ALL_ON = "11111"
    const val COMMAND_ALL_OFF = RELAY_OFF

    const val COLOR_WHITE = "50000"
    const val COLOR_GREEN = "50100"
    const val COLOR_YELLOW = "50200"
    const val COLOR_RED = "50300"

    fun isValidFloor(floor: Int): Boolean = floor in 1..19

    fun roomCountForFloor(floor: Int): Int {
        return when (floor) {
            1 -> 8
            in 2..19 -> 8
            else -> 0
        }
    }

    fun isValidRoom(floor: Int, room: Int): Boolean {
        return room in 1..roomCountForFloor(floor)
    }

    fun getFloorCommand(floor: Int): String {
        require(isValidFloor(floor)) { "Unsupported floor: $floor" }
        return (10000 + floor).toString()
    }

    fun getRoomCommand(floor: Int, room: Int): String {
        require(isValidRoom(floor, room)) { "Unsupported room: floor=$floor room=$room" }

        return when (floor) {
            1 -> when (room) {
                1 -> "111"
                2 -> "112"
                3 -> "113"
                4 -> "114"
                5 -> "115"
                6 -> "116"
                7 -> "117"
                8 -> "118"
                else -> error("Unsupported room: floor=$floor room=$room")
            }

            2 -> when (room) {
                1 -> "81"
                2 -> "90"
                3 -> "100"
                4 -> "110"
                5 -> "120"
                6 -> "130"
                7 -> "140"
                8 -> "150"
                else -> error("Unsupported room: floor=$floor room=$room")
            }

            else -> ((floor - 1) * 80 + (room - 1) * 10).toString()
        }
    }

    fun getRoomOffCommand(floor: Int, room: Int): String {
        require(isValidRoom(floor, room)) { "Unsupported room: floor=$floor room=$room" }
        return (getRoomCommand(floor, room).toInt() + 100).toString()
    }

    fun getFloorOffCommands(floor: Int): List<String> {
        require(isValidFloor(floor)) { "Unsupported floor: $floor" }
        return getRoomsForFloor(floor).map { command -> (command.toInt() + 100).toString() }
    }

    fun getRoomsForFloor(floor: Int): List<String> {
        val roomCount = roomCountForFloor(floor)
        return (1..roomCount).map { room -> getRoomCommand(floor, room) }
    }
}
