package fi.benaberg.sts.service.handlers

import fi.benaberg.sts.service.LogRef
import fi.benaberg.sts.service.model.TemperatureReading
import fi.benaberg.sts.service.util.JSONUtil
import kotlinx.coroutines.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class WsServletHandler(
    private val log: LogRef,
    private val port: Int,
    private val storageHandler: StorageHandler,
    private val sessions: MutableSet<WebSocket>)
    : WebSocketServer(InetSocketAddress(port)) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        storageHandler.addListener(this::broadcastTemperature)
    }

    override fun onStart() {
        log.write("Setting up WS servlet on port: $port")

        scope.launch {
            while (true) {
                // Create payload
                val jsonReading = JSONUtil.temperatureReadingToJSON(storageHandler.getCurrentTemperatureReading())

                // Continuously update every second
                broadcast(jsonReading.toString())
                delay(1000)
            }
        }
    }

    override fun onOpen(socket: WebSocket, clientHandshake: ClientHandshake?) {
        log.write("New WS connection established from: ${socket.remoteSocketAddress}.")
        sessions.add(socket)

        // Send current log to session
        log.sendLog(socket)
    }

    override fun onClose(socket: WebSocket, p1: Int, p2: String?, p3: Boolean) {
        log.write("WS connection from ${socket.remoteSocketAddress} closed.")
        sessions.remove(socket)
    }

    override fun onMessage(socket: WebSocket, message: String?) {
        log.write("WS message: $message from ${socket.remoteSocketAddress}")
    }

    override fun onError(socket: WebSocket, exception: Exception?) {
        log.write("WS error: " + exception?.message + " from ${socket.remoteSocketAddress}")
    }

    fun broadcastTemperature(temperatureReading: TemperatureReading) {
        sessions.forEach { it.send(JSONUtil.temperatureReadingToJSON(temperatureReading).toString()) }
    }
}