package multiplayer

import arc.Core
import mindustry.Vars
import mindustry.mod.Mod
import multiplayer.dialogs.JoinViaClajDialog
import multiplayer.dialogs.ManageRoomsDialog

@Suppress("unused")
class Main : Mod() {


    override fun init() {
        ClajIntegration.load()
        joinViaClaj = JoinViaClajDialog()
        manageRooms = ManageRoomsDialog()

    }

    companion object {
        var joinViaClaj: JoinViaClajDialog? = null
        var manageRooms: ManageRoomsDialog? = null
        fun copy(text: String?) {
            if (text == null) return

            Core.app.clipboardText = text
            Vars.ui.showInfoFade("@copied")
        }
    }
}