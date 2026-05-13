package com.example.applicationledcontrol.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.OutputStream
import java.util.*

object BluetoothConnectionManager {
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // Стандартный UUID для последовательного порта (SPP)
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1005-8000-00000000fb21")

    fun connect(address: String): Boolean {
        // Очищаем старые ресурсы перед попыткой
        disconnect()

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter?.getRemoteDevice(address)

        return try {
            // 1. Создаем сокет
            socket = device?.createRfcommSocketToServiceRecord(MY_UUID)
            // 2. Останавливаем поиск (критично для стабильности подключения)
            adapter?.cancelDiscovery()
            // 3. Пытаемся соединиться (блокирующая операция)
            socket?.connect()
            // 4. Если мы здесь, значит connect() прошел успешно
            outputStream = socket?.outputStream
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    fun send(data: String): Boolean {
        return try {
            val stream = outputStream ?: return false
            val commandWithDelimiter = if (data.endsWith("\n")) data else "$data\n"
            stream.write(commandWithDelimiter.toByteArray())
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    fun isConnected(): Boolean {
        return socket != null && socket!!.isConnected
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket = null
            outputStream = null
        }
    }
}