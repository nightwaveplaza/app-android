package one.plaza.nightwaveplaza.socket

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import org.json.JSONObject
import timber.log.Timber
import java.net.URISyntaxException

class SocketClient(
    private val callback: SocketCallback,
    private val lifecycle: Lifecycle,
) : LifecycleObserver {
    private var socket: Socket? = null
    var isConnected = false
    private val lifeCycleObserver = LifeCycleObserver()

    fun initialize() {
        try {
            socket = IO.socket(
                "https://plaza.one",
                IO.Options.builder()
                    .setPath("/ws")
                    .setReconnection(true)
                    .setReconnectionAttempts(10)
                    .build()
            )
        } catch (_: URISyntaxException) {
        }

        createSocketListeners()

        lifecycle.addObserver(lifeCycleObserver)
    }

    fun connect() {
        Timber.d("Socket: Connect attempt")
        socket?.connect()
    }

    fun disconnect() {
        Timber.d("Socket: Disconnect attempt")
        socket?.disconnect()
    }

    fun destroy() {
        disconnect()
        socket?.off()
        lifecycle.removeObserver(lifeCycleObserver)
    }

    private fun createSocketListeners() {
        socket?.on("status") { args ->
            (args.getOrNull(0) as? JSONObject)?.let {
                callback.onStatus(it.toString())
            }
        }?.on("listeners") { args ->
            (args.getOrNull(0) as? Int)?.let {
                callback.onListeners(it)
            }
        }?.on("reactions") { args ->
            (args.getOrNull(0) as? Int)?.let {
                callback.onReactions(it)
            }
        }?.on("connect") { _ ->
            Timber.d("Socket: Connected")
            isConnected = true
            callback.onSocketConnect()
        }?.on("disconnect")  { _ ->
            Timber.d("Socket: Disconnected")
            isConnected = false
            callback.onSocketDisconnect()
        }

        socket?.io()?.on(Manager.EVENT_RECONNECT_FAILED) {
            Timber.d("Socket: Reconnect failed")
            callback.onSocketReconnectFailed()
        }
    }

    inner class LifeCycleObserver : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_DESTROY -> {
                    Timber.d("Lifecycle: onDestroy")
                    destroy()
                }

                Lifecycle.Event.ON_RESUME -> {
                    Timber.d("Lifecycle: onResume")
                    connect()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Timber.d("Lifecycle: onPause")
                    disconnect()
                }

                else -> {}
            }
        }
    }
}