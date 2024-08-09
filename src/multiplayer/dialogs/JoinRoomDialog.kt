package multiplayer.dialogs

import arc.scene.ui.layout.Stack
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.core.NetClient.connect
import mindustry.gen.Icon
import mindustry.ui.dialogs.BaseDialog
import multiplayer.Link
import java.io.IOException

class JoinRoomDialog : BaseDialog("通过组网加入游戏") {
    private var lastLink: String = "请输入您的房间代码"

    private var valid: Boolean = false
    private var output: String? = null

    init {
        cont.table { table: Table ->
            table.add("房间代码：").padRight(5f).left()
            table.field(lastLink) { link: String -> this.setLink(link) }.size(550f, 54f).maxTextLength(100).valid { link: String -> this.setLink(link) }
        }.row()

        cont.label { output }.width(550f).left()

        buttons.defaults().size(140f, 60f).pad(4f)
        buttons.button("@cancel") { this.hide() }
        buttons.button("@ok") {
            try {
                if (Vars.player.name.trim { it <= ' ' }.isEmpty()) {
                    Vars.ui.showInfo("@noname")
                    return@button
                }

                val link = parseLink(lastLink)
                connect(link.ip, link.port).also {
                    Vars.ui.join.hide()
                    hide()
                }

                Vars.ui.loadfrag.show("@connecting")
                Vars.ui.loadfrag.setButton {
                    Vars.ui.loadfrag.hide()
                    Vars.netClient.disconnectQuietly()
                }
            } catch (e: Throwable) {
                Vars.ui.showErrorMessage(e.message)
            }
        }.disabled { lastLink.isEmpty() || Vars.net.active() }

        fixJoinDialog()
    }

    private fun setLink(link: String): Boolean {
        if (lastLink == link) return valid

        try {
            parseLink(link)

            output = "[lime]代码格式正确, 点击下方按钮尝试连接！"
            valid = true
        } catch (ignored: Throwable) {
            output = ignored.message
            valid = false
        }

        lastLink = link
        return valid
    }

    private fun fixJoinDialog() {
        val stack = Vars.ui.join.children[1] as Stack
        val root = stack.children[1] as Table

        root.button("通过claj代码加入游戏", Icon.play) { this.show() }

        if (!Vars.steam && !Vars.mobile) root.cells.insert(4, root.cells.remove(6))
        else root.cells.insert(3, root.cells.remove(4))
    }

    @Throws(IOException::class)
    private fun parseLink(link: String): Link {
        var link1 = link
        link1 = link1.trim { it <= ' ' }
        if (!link1.startsWith("ZeroTier")) throw IOException("无效的ZeroTier代码：无ZeroTier前缀")

        // 找到冒号的位置
        val colonIndex = link.indexOf(':')
        // 提取IP地址部分（去掉前面的"ZeroTier"）
        val ip = "zerotier:" + link.substring(8, colonIndex)
        // 提取端口部分
        val port = link.substring(colonIndex + 1).toInt()

        return Link(ip, port)
    }
}