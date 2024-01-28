package multiplayer

import arc.Events
import arc.func.Cons
import arc.net.Client
import arc.net.Connection
import arc.net.DcReason
import arc.net.NetListener
import arc.struct.Seq
import arc.util.Reflect
import arc.util.Threads
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.EventType.ClientPreConnectEvent
import mindustry.gen.Call
import mindustry.io.TypeIO
import mindustry.net.ArcNetProvider.PacketSerializer
import java.io.IOException
import java.nio.ByteBuffer

object ClajIntegration {
    private var clients: Seq<Client> = Seq()
    private var serverListener: NetListener? = null

    fun load() {
        Events.run(EventType.HostEvent::class.java, ClajIntegration::clear)
        Events.run(ClientPreConnectEvent::class.java, ClajIntegration::clear)

        var provider = Reflect.get<Any>(Vars.net, "provider")
        if (Vars.steam) provider = Reflect.get(provider, "provider") // thanks


        val server = Reflect.get<Any>(provider, "server")
        serverListener = Reflect.get(server, "dispatchListener")
    }

    // region room management
    @Throws(IOException::class)
    fun createRoom(ip: String, port: Int, link: Cons<String?>, disconnected: Runnable): Client {
        val client = Client(8192, 8192, Serializer())
        Threads.daemon("CLaJ Room") { client.run() }

        client.addListener(object : NetListener {
            /** Used when creating redirectors.  */
            var key: String? = null

            override fun connected(connection: Connection) {
                client.sendTCP("new")
            }

            override fun disconnected(connection: Connection, reason: DcReason) {
                disconnected.run()
            }

            override fun received(connection: Connection, `object`: Any) {
                if (`object` is String) {
                    if (`object`.startsWith("CLaJ")) {
                        this.key = `object`
                        link["$key#$ip:$port"]
                    } else if (`object` == "new") {
                        try {
                            createRedirector(ip, port, key)
                        } catch (ignored: Exception) {
                        }
                    } else Call.sendMessage(`object`)
                }
            }
        })

        client.connect(5000, ip, port, port)
        clients.add(client)

        return client
    }

    @Throws(IOException::class)
    fun createRedirector(ip: String?, port: Int, key: String?) {
        val client = Client(8192, 8192, Serializer())
        Threads.daemon("CLaJ Redirector") { client.run() }

        client.addListener(serverListener)
        client.addListener(object : NetListener {
            override fun connected(connection: Connection) {
                client.sendTCP("host$key")
            }
        })

        client.connect(5000, ip, port, port)
        clients.add(client)
    }

    fun joinRoom(ip: String, port: Int, key: String, success: Runnable) {
        Vars.logic.reset()
        Vars.net.reset()

        Vars.netClient.beginConnecting()
        Vars.net.connect(ip, port) {
            if (!Vars.net.client()) return@connect
            success.run()

            val buffer = ByteBuffer.allocate(8192)
            buffer.put(Serializer.linkID)
            TypeIO.writeString(buffer, "join$key")

            buffer.limit(buffer.position()).position(0)
            Vars.net.send(buffer, true)
        }
    }

    private fun clear() {
        clients.each { obj: Client -> obj.close() }
        clients.clear()
    }

    // endregion


    class Serializer : PacketSerializer() {
        override fun write(buffer: ByteBuffer, `object`: Any) {
            if (`object` is String) {
                buffer.put(linkID)
                TypeIO.writeString(buffer, `object`)
            } else super.write(buffer, `object`)
        }

        override fun read(buffer: ByteBuffer): Any {
            if (buffer.get() == linkID) return TypeIO.readString(buffer)

            buffer.position(buffer.position() - 1)
            return super.read(buffer)
        }

        companion object {
            const val linkID: Byte = -3
        }
    }
}