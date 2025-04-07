package one.plaza.nightwaveplaza.socket

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SocketClient(
    private val callback: SocketCallback,
    private val lifecycle: Lifecycle,
) : LifecycleObserver {
    private lateinit var mSocket: Socket
    public var isConnected = false

    fun initialize() {
        try {
            mSocket = IO.socket(
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

        lifecycle.addObserver(LifeCycleObserver())
    }

    fun connect() {
        mSocket.connect()
    }

    fun disconnect() {
        mSocket.disconnect()
    }

    private fun createSocketListeners() {
        mSocket.on("status") { args ->
            if (args[0] != null) {
                callback.onStatus((args[0] as JSONObject).toString())
            }
        }.on("listeners") { args ->
            if (args[0] != null) {
                callback.onListeners(args[0] as Int)
            }
        }.on("reactions") { args ->
            if (args[0] != null) {
                callback.onReactions(args[0] as Int)
            }
        }.on("connect") { args -> isConnected = true
        }.on("disconnect")  { args -> isConnected = false }

        mSocket.io().on(Manager.EVENT_RECONNECT_FAILED) {
            callback.onSocketReconnectFailed()
        }
    }

    inner class LifeCycleObserver : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    connect()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    disconnect()
                }

                else -> {}
            }
        }
    }
}