package one.plaza.nightwaveplaza.socket

interface SocketCallback {
    fun onStatus(s: String)
    fun onListeners(listeners: Int)
    fun onReactions(reactions: Int)
    fun onSocketConnect()
    fun onSocketDisconnect()
    fun onSocketReconnectFailed()
}