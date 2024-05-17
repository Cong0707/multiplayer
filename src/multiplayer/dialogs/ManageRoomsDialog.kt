package multiplayer.dialogs

import arc.graphics.Color
import arc.net.Client
import arc.scene.ui.Label
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.util.Log
import arc.util.Time
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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

class ManageRoomsDialog : BaseDialog("管理 CLaJ 房间") {
    var serverIP: String? = null
    var serverPort: Int = 0
    lateinit var label: Cell<Label>

    private var list: Table? = null

    init {
        loadURL()

        addCloseButton()

        cont.defaults().width(if (Vars.mobile) 550f else 750f)

        cont.table { list: Table ->
            list.defaults().growX().padBottom(8f)
            list.update { list.cells.filter { cell: Cell<*> -> cell.get() != null } } // remove closed rooms
            this.list = list
        }.row()

        cont.button("新建 CLaJ 房间") {
            try {
                loadURL()
                if (serverIP == null) throw Exception("获取联机服务器失败")
                list?.add(Room())?.row()
            } catch (ignored: Exception) {
                Vars.ui.showErrorMessage(ignored.message)
            }
        }.disabled { list!!.children.size >= 1 }.row()

        label = cont.labelWrap("Using server $serverIP:$serverPort").labelAlign(2, 8).padTop(16f).width(400f).apply {
            get().style.fontColor = Color.lightGray
        }

        Vars.ui.paused.shown { this.fixPausedDialog() }
    }

    fun loadURL() {
        val serverListJson = URL("http://p4.simpfun.cn:8667/client/servers").readText()
        val serverList = Json.decodeFromString<List<Server>>(serverListJson)
        Log.info("Loading multiplayer worker servers $serverList")
        val pingMap = mutableMapOf<Int, Server>()
        serverList.forEach {
            val host = pingHostImpl(it.address, it.port)
            if (host.modeName == "MultiPlayer" || host.players < 5) {
                pingMap[host.ping] = it
            }
        }
        Log.info("Ping result ${pingMap.toSortedMap()}")
        val result = pingMap.toSortedMap().minByOrNull { it.key }?.value
        Log.info("Result is $result")
        serverIP = result?.address
        serverPort = result?.port!!
        Log.info("Using $serverIP:$serverPort")
        //label.update or something
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
            root.row().buttonRow("管理 CLaJ 房间", Icon.planet) { this.show() }.colspan(3).disabled { !Vars.net.server() }
            return
        }

        root.row()
        root.button("管理 CLaJ 房间", Icon.planet) { this.show() }.colspan(2).width(450f).disabled { !Vars.net.server() }.row()

        val index = if (Vars.state.isCampaign || Vars.state.isEditor) 5 else 7
        root.cells.insert(index, root.cells.remove(index + 1))
    }

    // endregion
    inner class Room : Table() {
        private var client: Client
        private var link: String? = null

        init {
            client = createRoom(serverIP!!, serverPort, { link: String? -> this.link = link }, { this.close() })

            table(Tex.underline) { cont: Table ->
                cont.label { link }.growX().left().fontScale(.7f).ellipsis(true)
            }.growX()

            table { btns: Table ->
                btns.defaults().size(48f).padLeft(8f)
                btns.button(Icon.copy, Styles.clearNonei) { Main.copy(link) }
                btns.button(Icon.cancel, Styles.clearNonei) { this.close() }
            }
        }

        private fun close() {
            client.close()
            remove()
        }
    }
}