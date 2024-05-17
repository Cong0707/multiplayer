package multiplayer.dialogs

import arc.scene.ui.layout.Stack
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.ui.dialogs.BaseDialog
import multiplayer.CLaJIntegration.joinRoom
import multiplayer.Link
import java.io.IOException

class JoinViaCLaJDialog : BaseDialog("通过 CLaJ 加入游戏") {
    private var lastLink: String = "请输入您的 CLaJ 链接"

    private var valid: Boolean = false
    private var output: String? = null

    init {
        cont.table { table: Table ->
            table.add("房间链接：").padRight(5f).left()
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
                joinRoom(link.ip, link.port, link.key) {
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

            output = "[lime]有效的链接"
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

        root.button("加入游戏", Icon.play) { this.show() }

        if (!Vars.steam && !Vars.mobile) root.cells.insert(4, root.cells.remove(6))
        else root.cells.insert(3, root.cells.remove(4))
    }

    @Throws(IOException::class)
    private fun parseLink(link: String): Link {
        var link1 = link
        link1 = link1.trim { it <= ' ' }
        if (!link1.startsWith("CLaJ")) throw IOException("无效的链接：无 CLaJ 前缀")

        val hash = link1.indexOf('#')
        if (hash != 42 + 4) throw IOException("无效的链接：长度错误")

        val semicolon = link1.indexOf(':')
        if (semicolon == -1) throw IOException("无效的链接：服务器地址格式不正确")

        val port: Int
        try {
            port = link1.substring(semicolon + 1).toInt()
        } catch (ignored: Throwable) {
            throw IOException("无效的链接：找不到服务器端口")
        }

        return Link(link1.substring(0, hash), link1.substring(hash + 1, semicolon), port)
    }
}