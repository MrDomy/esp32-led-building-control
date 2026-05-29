package com.example.applicationledcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildingProtocolTest {

    @Test
    fun floorCommandsMatchFirmware() {
        assertEquals("10001", BuildingProtocol.getFloorCommand(1))
        assertEquals("10019", BuildingProtocol.getFloorCommand(19))
    }

    @Test
    fun roomCommandsMatchFirmware() {
        assertEquals("111", BuildingProtocol.getRoomCommand(1, 1))
        assertEquals("118", BuildingProtocol.getRoomCommand(1, 8))
        assertEquals("81", BuildingProtocol.getRoomCommand(2, 1))
        assertEquals("90", BuildingProtocol.getRoomCommand(2, 2))
        assertEquals("160", BuildingProtocol.getRoomCommand(3, 1))
        assertEquals("1510", BuildingProtocol.getRoomCommand(19, 8))
        assertEquals("218", BuildingProtocol.getRoomOffCommand(1, 8))
    }

    @Test
    fun floorRoomCountsAreValidated() {
        assertEquals(8, BuildingProtocol.roomCountForFloor(1))
        assertEquals(8, BuildingProtocol.roomCountForFloor(2))
        assertTrue(BuildingProtocol.isValidRoom(1, 8))
        assertFalse(BuildingProtocol.isValidRoom(1, 9))
    }
}
