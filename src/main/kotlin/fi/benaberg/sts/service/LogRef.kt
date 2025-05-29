package fi.benaberg.sts.service

import fi.benaberg.sts.service.def.Constants
import org.java_websocket.WebSocket
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Handles logging to console as well as active Websocket (dashboard) sessions.
 */
class LogRef(private val sessions: MutableSet<WebSocket>) {

    companion object {
        const val MAX_MESSAGES = 10000
    }

    private val sdf = SimpleDateFormat("dd-MM-yyy HH:mm:ss.SSS ")
    private val offlineMessages = Collections.synchronizedList(mutableListOf<String>())
    private val activeSessions = Collections.synchronizedList(mutableListOf<WebSocket>())
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    init {
        write("Initializing LogRef...")
        scheduledExecutor.scheduleAtFixedRate(this::validateSessions, 10, 10, TimeUnit.SECONDS)
        write("LogRef initialization complete!")
    }

    fun write(message: String) {
        val formattedMessage = getFormattedTime(System.currentTimeMillis()) + message

        // Print message to console
        println(formattedMessage)

        // Write to active sessions
        if (sessions.isNotEmpty()) {
            sessions.forEach { session ->
                if (session.isOpen) {
                    sendMessage(session, formattedMessage)
                }
            }
        }
        // Store message
        offlineMessages.add(formattedMessage)

        // Check that size limit has not been reached
        if (offlineMessages.size > MAX_MESSAGES) {
            offlineMessages.removeFirst()
        }
    }

    fun sendLog(session: WebSocket) {
        // Send stored offline messages to sessions that have not yet received them
        if (!activeSessions.contains(session)) {
            offlineMessages.forEach { msg ->
                sendMessage(session, msg)
            }
            activeSessions.add(session)
        }
    }

    private fun sendMessage(session: WebSocket, message: String) {
        val json = JSONObject()
        json.put(Constants.LOG, message)
        session.send(json.toString())
    }

    private fun validateSessions() {
        val inactiveSessions = mutableListOf<WebSocket>()
        activeSessions.forEach { session ->
            if (!sessions.contains(session)) {
                inactiveSessions.add(session)
            }
        }
        if (inactiveSessions.isNotEmpty()) {
            write("Removing ${inactiveSessions.size} inactive sessions from LogRef sessions.")
            activeSessions.removeAll(inactiveSessions)
        }
    }

    private fun getFormattedTime(millis: Long):String {
        return sdf.format(Date(millis))
    }
}