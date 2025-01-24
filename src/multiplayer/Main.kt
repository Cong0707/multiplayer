package multiplayer

import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.mod.Mod
import multiplayer.dialogs.JoinViaClajDialog
import multiplayer.dialogs.ManageRoomsDialog

@Suppress("unused")
class Main : Mod() {

    override fun init() {
        ClajIntegration.load()
        joinViaClaj = JoinViaClajDialog()
        manageRooms = ManageRoomsDialog()

        val buttons: Table = Vars.ui.join.buttons
        buttons.button("通过claj代码加入游戏", Icon.play, joinViaClaj::show)

        val pausedDialog = Vars.ui.paused
        pausedDialog.shown {
            pausedDialog.cont.row()
                .collapser(
                    { t: Table ->
                        t.button("管理claj房间", Icon.planet) { manageRooms.show() }
                            .growX().fillY()
                    },
                    { Vars.net.server() })
                .name("ClajInfo").size(0f, 60f).colspan(pausedDialog.cont.columns).fill().row()
        }

    }

    companion object {
        lateinit var joinViaClaj: JoinViaClajDialog
        lateinit var manageRooms: ManageRoomsDialog
    }
}