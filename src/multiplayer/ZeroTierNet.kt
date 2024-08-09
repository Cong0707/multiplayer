package multiplayer

import arc.ApplicationListener
import arc.Core
import arc.Events
import arc.func.Cons
import arc.util.Log
import arc.util.Threads
import com.zerotier.sockets.ZeroTierNative.zts_util_delay
import com.zerotier.sockets.ZeroTierNode
import com.zerotier.sockets.ZeroTierServerSocket
import com.zerotier.sockets.ZeroTierSocket
import mindustry.Vars.net
import mindustry.game.EventType.ClientLoadEvent
import mindustry.net.ArcNetProvider.PacketSerializer
import mindustry.net.Host
import mindustry.net.Net.NetProvider
import mindustry.net.NetConnection
import mindustry.net.Packet
import mindustry.net.Packets.Connect
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

fun InetAddress.format() = "zerotier:$this"
fun String.toZeroTierLong(): Long {
    val id = BigInteger(this, 16)
    return id.toLong()
}

class ZeroTierNet(val provider: NetProvider) : NetProvider {
    lateinit var serverThreads: Thread
    lateinit var socket: ZeroTierSocket
    var isZeroTierClient: Boolean = false

    val networkId = "6ab565387a0d0396".toZeroTierLong()
    val networkMoons = listOf("7458bf01d0")

    class ServerListener(private val socket: ZeroTierServerSocket) : Runnable {
        override fun run() {
            try {
                val clientSocket = socket.accept()
                val connection = ZeroTierConnection(clientSocket)
                val connect = Connect()
                connect.addressTCP = clientSocket.inetAddress.format()
                Log.info("&bReceived ZeroTier connection: @", connect.addressTCP);
                zerotierConnections.add(connection)
                net.handleServerReceived(connection, connect)
            } catch (e: Throwable) {
                Log.err(e);
            }
        }
    }

    init {
        val node = ZeroTierNode()
        node.start()
        while (!node.isOnline) {
            zts_util_delay(50);
        }
        node.join(networkId)
        Log.info("Join network")
        while (!node.isNetworkTransportReady(networkId)) {
            zts_util_delay(50)
        }
        Log.info("Join successful")

        // IPv4
        val addr4 = node.getIPv4Address(networkId)
        println("IPv4 address = " + addr4.hostAddress)

        // IPv6
        val addr6 = node.getIPv6Address(networkId)
        println("IPv6 address = " + addr6.hostAddress)

        // MAC address
        System.out.println("MAC address = " + node.getMACAddress(networkId))


        Events.on(ClientLoadEvent::class.java) {
            Core.app.addListener(object : ApplicationListener {
                override fun update() {
                    if (net.client() && ::socket.isInitialized) {
                        val bytesRead = socket.inputStream.read(readBuffer.array())
                        if (bytesRead != -1) {
                            readBuffer.position(0).limit(readBuffer.capacity())
                            readCopyBuffer.position(0)
                            readCopyBuffer.put(readBuffer)
                            readCopyBuffer.position(0)

                            val output = serializer.read(readCopyBuffer)

                            //it may be theoretically possible for this to be a framework message, if the packet is malicious or corrupted
                            if (output is Packet) {
                                try {
                                    net.handleClientReceived(output)
                                } catch (t: Throwable) {
                                    net.handleException(t)
                                }
                            }
                        }
                    }
                }
            })
        }

    }

    //input = socket.inputStream
    //output = socket.outputStream

    override fun connectClient(ip: String?, port: Int, success: Runnable?) {
        if (ip!!.startsWith("zerotier:")) {
            val lobbyname = ip.substring("zerotier:".length)
            try {
                socket = ZeroTierSocket(lobbyname, port)
                Log.info("Connected ZeroTier server")
            } catch (e: Exception) {
                Log.err("Zerotier connect error", e)
            }
        } else {
            provider.connectClient(ip, port, success)
        }
    }

    override fun sendClient(`object`: Any?, reliable: Boolean) {
        if (isZeroTierClient && ::socket.isInitialized) {
            if (socket.isClosed) {
                Log.info("Not connected, quitting.")
                return
            }

            try {
                writeBuffer.limit(writeBuffer.capacity())
                writeBuffer.position(0)
                serializer.write(writeBuffer, `object`)
                val length = writeBuffer.position()
                writeBuffer.flip()

                val byteArray = ByteArray(writeBuffer.remaining())
                writeBuffer.get(byteArray)
                socket.outputStream.write(byteArray)
            } catch (e: java.lang.Exception) {
                net.showError(e)
            }
        } else {
            provider.sendClient(`object`, reliable)
        }
    }

    override fun disconnectClient() {
        if (::socket.isInitialized) {
            if (!socket.isClosed) socket.close()
        }
    }

    override fun discoverServers(callback: Cons<Host>?, done: Runnable?) {
        Core.app.post(done)
    }

    override fun pingHost(address: String?, port: Int, valid: Cons<Host>?, failed: Cons<Exception>?) {
        return provider.pingHost(address, port, valid, failed)
    }

    override fun hostServer(port: Int) {
        provider.hostServer(port)
        serverThreads = Threads.daemon { ServerListener(ZeroTierServerSocket(port)) }
        Log.info("Hosting Zerotier server on port @", port)
    }

    override fun getConnections(): MutableIterable<NetConnection> {
        //merge provider connections
        val connectionsOut: CopyOnWriteArrayList<NetConnection> = CopyOnWriteArrayList(zerotierConnections)
        for (c in provider.connections) connectionsOut.add(c)
        return connectionsOut
    }

    override fun closeServer() {
        if (::serverThreads.isInitialized) {
            if (!serverThreads.isInterrupted) serverThreads.interrupt()
        }
        provider.closeServer()
    }

    companion object {
        val serializer = PacketSerializer()
        val writeBuffer: ByteBuffer = ByteBuffer.allocateDirect(16384)
        val readBuffer: ByteBuffer = ByteBuffer.allocateDirect(16384)
        val readCopyBuffer: ByteBuffer = ByteBuffer.allocate(writeBuffer.capacity())

        val zerotierConnections: MutableList<ZeroTierConnection> = mutableListOf()
    }

    class ZeroTierConnection(val socket: ZeroTierSocket) : NetConnection("zerotier:" + socket.remoteAddress) {
        init {
            Log.info("Created Zerotier connection: @", socket.remoteAddress)
        }

        override fun send(`object`: Any, reliable: Boolean) {
            try {
                writeBuffer.limit(writeBuffer.capacity())
                writeBuffer.position(0)
                serializer.write(writeBuffer, `object`)
                val length: Int = writeBuffer.position()
                writeBuffer.flip()

                val byteArray = ByteArray(writeBuffer.remaining())
                writeBuffer.get(byteArray)
                socket.outputStream.write(byteArray)
            } catch (e: java.lang.Exception) {
                Log.err(e)
                Log.info("Error sending packet. Disconnecting invalid client!")
                close()

                val k = zerotierConnections.find { it.address == address }
                if (k != null) zerotierConnections.remove(k)
            }
        }

        override fun isConnected(): Boolean {
            return socket.isConnected
        }

        override fun close() {
            socket.close()
        }
    }
}