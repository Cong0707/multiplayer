package multiplayer.dialogs

import arc.graphics.Color
import arc.net.Client
import arc.scene.ui.TextField
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Strings
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import multiplayer.ClajIntegration.createRoom
import multiplayer.Main

class ManageRoomsDialog : BaseDialog("管理claj房间") {
    var serverIP: String? = null
    var serverPort: Int = 0

    private var list: Table? = null
    private var field: TextField? = null
    private var valid: Boolean = false
    private var flip: Boolean = false
    private var clajURLs: Seq<String> = Seq.with("new.xem8k5.top:1050")

    init {
        addCloseButton()

        cont.defaults().width(if (Vars.mobile) 550f else 750f)

        cont.table { list: Table ->
            list.defaults().growX().padBottom(8f)
            list.update { list.cells.filter { cell: Cell<*> -> cell.get() != null } } // remove closed rooms
            this.list = list
        }.row()

        cont.table { url: Table ->
            url.field(clajURLs.first()) { this.setURL(it) }.maxTextLength(100).valid { this.validURL(it) }.with { f: TextField? -> field = f }.growX()
            url.button(Icon.downOpen, Styles.clearNonei) { flip = !flip }.size(48f).padLeft(8f)
        }.row()

        cont.collapser({ list: Table ->
            clajURLs.each { url: String ->
                list.button(url, Styles.cleart) { setURL(url) }.height(32f).growX().row()
            }
        }, true, { flip }).row()

        cont.button("新建房间并生成claj代码") {
            try {
                list?.add(Room())?.row()
            } catch (ignored: Exception) {
                Vars.ui.showErrorMessage(ignored.message)
            }
        }.disabled { list!!.children.size >= 4 || !valid }.row()

        cont.labelWrap("允许你的朋友通过claj代码联机").labelAlign(2, 8).padTop(16f).width(400f).get().style.fontColor = Color.lightGray

        setURL(clajURLs.first())
        Vars.ui.paused.shown { this.fixPausedDialog() }
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

    // region URL
    private fun setURL(url: String) {
        field!!.text = url

        val semicolon = url.indexOf(':')
        serverIP = url.substring(0, semicolon)
        serverPort = Strings.parseInt(url.substring(semicolon + 1))
    }

    private fun validURL(url: String): Boolean {
        return (url.contains(":") && Strings.canParseInt(url.substring(url.indexOf(':') + 1))).also { valid = it }
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