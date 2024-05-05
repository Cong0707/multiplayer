package multiplayer

import kotlinx.serialization.Serializable

@Serializable
data class Server(val address: String, val port: Int)