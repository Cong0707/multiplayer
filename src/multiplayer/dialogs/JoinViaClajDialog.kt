package multiplayer.dialogs

import arc.Core
import arc.scene.ui.TextButton
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import multiplayer.ClajIntegration.joinRoom
import java.io.IOException

class JoinViaClajDialog : BaseDialog("通过claj加入游戏") {
    private var lastLink: String? = "请输入您的claj代码"

    private var valid = false
    private var output: String? = null

    init {
        cont.table { table: Table ->
            table.add("房间代码：").padRight(5f).left()
            val tf = table.field(
                lastLink
            ) { link: String -> this.setLink(link) }.size(550f, 54f).maxTextLength(100)
                .valid { link: String ->
                    this.setLink(
                        link
                    )
                }.get()
            tf.programmaticChangeEvents = true

            table.defaults().size(48f).padLeft(8f)
            table.button(Icon.paste, Styles.clearNonei) { tf.text = Core.app.clipboardText }
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
        }.disabled { b: TextButton? -> lastLink!!.isEmpty() || Vars.net.active() }
    }

    private fun setLink(link: String): Boolean {
        if (lastLink == link) return valid

        try {
            parseLink(link)

            output = "[lime]代码格式正确, 点击下方按钮尝试连接！"
            valid = true
        } catch (e: Throwable) {
            output = e.message
            valid = false
        }

        lastLink = link
        return valid
    }

    @Throws(IOException::class)
    private fun parseLink(link: String?): Link {
        var link1 = link
        link1 = link1!!.trim { it <= ' ' }
        if (!link1.startsWith("CLaJ")) throw IOException("无效的claj代码：无CLaJ前缀")

        val hash = link1.indexOf('#')
        if (hash != 42 + 4) throw IOException("无效的claj代码：长度错误")

        val semicolon = link1.indexOf(':')
        if (semicolon == -1) throw IOException("无效的claj代码：服务器地址格式不正确")

        val port: Int
        try {
            port = link1.substring(semicolon + 1).toInt()
        } catch (ignored: Throwable) {
            throw IOException("无效的claj代码：找不到服务器端口")
        }

        return Link(link1.substring(0, hash), link1.substring(hash + 1, semicolon), port)
    }

    class Link(val key: String, internal val ip: String, val port: Int)
}