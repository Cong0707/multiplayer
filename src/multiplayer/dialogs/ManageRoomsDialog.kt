package multiplayer.dialogs

import arc.graphics.Color
import arc.net.Client
import arc.scene.ui.Label
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.util.Log
import arc.util.Time
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.net.Host
import mindustry.net.NetworkIO
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import multiplayer.ClajIntegration.createRoom
import multiplayer.Main
import multiplayer.Server
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer
import kotlinx.serialization.json.Json
import java.util.*

class ManageRoomsDialog : BaseDialog("管理claj房间") {
    var hostList: MutableList<Host> = Collections.synchronizedList(mutableListOf<Host>())
    var serverIP: String? = null
    var serverPort: Int = 0
    var label: Cell<Label>

    private var list: Table? = null

    init {
        addCloseButton()

        cont.defaults().width(if (Vars.mobile) 550f else 750f)

        cont.table { list: Table ->
            list.defaults().growX().padBottom(8f)
            list.update { list.cells.removeAll { cell: Cell<*> -> cell.get() == null } } // remove closed rooms
            this.list = list
        }.row()

        cont.button("新建房间并生成房间链接") {
            try {
                loadURL()
                if (serverIP == null) throw Exception("获取联机服务器失败")
                list?.add(Room())?.row()
            } catch (ignored: Exception) {
                Vars.ui.showErrorMessage(ignored.message)
            }
        }.disabled { list!!.children.size >= 1 }.row()

        label = cont.labelWrap(
            buildServerList()
        ).labelAlign(2, 8).padTop(16f).width(500f).apply {
            get().style.fontColor = Color.lightGray
        }

        Vars.ui.paused.shown { this.fixPausedDialog() }
        /*
        try {
            loadURL()
        } catch (e: Exception) {
            Log.err("Err on loading claj servers", e)
        }
        */
    }

    fun loadURL() {
        val serverListJson = URL("http://p4.simpfun.cn:8667/client/servers").readText()
        val serverList: List<Server> = Json.decodeFromString(serverListJson)
        Log.info("Loading multiplayer worker servers $serverList")

        hostList = Collections.synchronizedList(mutableListOf<Host>())
        val threads = serverList.map {
            Thread {
                val host = pingHostImpl(it.address, it.port)
                hostList.add(host)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        synchronized(hostList){
            hostList.sortBy { it.ping }
            Log.info("Result is $hostList")
            val first = hostList.first { host -> host.modeName == "MultiPlayer" || host.players < host.playerLimit }
            serverIP = first.address
            serverPort = first.port
            Log.info("Using $serverIP:$serverPort")
            label.update {
                it.setText(
                    buildServerList()
                )
            }
        }
    }

    private fun buildServerList(): String {
        if (hostList.size == 0) {
            return ""
        }
        return StringBuilder()
            .appendLine("在线服务器(${hostList.size}):")
            .apply {
                hostList.forEach {
                    this.appendLine("${it.address}:${it.port}=${it.ping}ms -> ${it.players}/${it.playerLimit} Rooms")
                }
            }
            .toString()
    }

    private fun pingHostImpl(address: String, port: Int): Host {
        DatagramSocket().use { socket ->
            val time = Time.millis()
            socket.send(
                DatagramPacket(
                    byteArrayOf(-2, 1),
                    2,
                    InetAddress.getByName(address),
                    port
                )
            )
            socket.soTimeout = 2000

            val packet = DatagramPacket(ByteArray(512), 512)
            socket.receive(packet)

            val buffer = ByteBuffer.wrap(packet.data)
            val host = NetworkIO.readServerData(
                Time.timeSinceMillis(time).toInt(),
                packet.address.hostAddress,
                buffer
            )
            host.port = port
            return host
        }
    }

    private fun fixPausedDialog() {
        val root = Vars.ui.paused.cont

        if (Vars.mobile) {
            root.row().buttonRow("管理claj房间", Icon.planet) { this.show() }.colspan(3).disabled { !Vars.net.server() }
            return
        }

        root.row()
        root.button("管理claj房间", Icon.planet) { this.show() }.colspan(2).width(450f).disabled { !Vars.net.server() }.row()

        val index = if (Vars.state.isCampaign || Vars.state.isEditor) 5 else 7
        root.cells.insert(index, root.cells.remove(index + 1))
    }

    // endregion
    inner class Room : Table() {
        private var client: Client
        private var link: String? = null

        init {
            client = createRoom(serverIP!!, serverPort, { this.link = it }, { this.close() })

            table(Tex.underline) {
                it.label { link }.growX().left().fontScale(.7f).ellipsis(true)
            }.growX()

            table {
                it.defaults().size(48f).padLeft(8f)
                it.button(Icon.copy, Styles.clearNonei) { Main.copy(link) }
                it.button(Icon.cancel, Styles.clearNonei) { this.close() }
            }
        }

        private fun close() {
            client.close()
            remove()
        }
    }
}