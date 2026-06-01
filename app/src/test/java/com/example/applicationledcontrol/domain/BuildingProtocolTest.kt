package com.example.applicationledcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildingProtocolTest {

    @Test
    fun floorCommandsMatchFirmware() {
        assertEquals("F1W0S1", BuildingProtocol.getFloorCommand(1))
        assertEquals("F19W0S1", BuildingProtocol.getFloorCommand(19))
    }

    @Test
    fun roomCommandsMatchFirmware() {
        assertEquals("F1W1S1", BuildingProtocol.getRoomCommand(1, 1))
        assertEquals("F1W8S1", BuildingProtocol.getRoomCommand(1, 8))
        assertEquals("F2W1S1", BuildingProtocol.getRoomCommand(2, 1))
        assertEquals("F2W2S1", BuildingProtocol.getRoomCommand(2, 2))
        assertEquals("F3W1S1", BuildingProtocol.getRoomCommand(3, 1))
        assertEquals("F19W8S1", BuildingProtocol.getRoomCommand(19, 8))
        assertEquals("F1W8S0", BuildingProtocol.getRoomOffCommand(1, 8))
    }

    @Test
    fun floorRoomCountsAreValidated() {
        assertEquals(8, BuildingProtocol.roomCountForFloor(1))
        assertEquals(8, BuildingProtocol.roomCountForFloor(2))
        assertTrue(BuildingProtocol.isValidRoom(1, 8))
        assertFalse(BuildingProtocol.isValidRoom(1, 9))
    }
}
