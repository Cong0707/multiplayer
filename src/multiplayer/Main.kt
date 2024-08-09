package multiplayer

import arc.Core
import mindustry.Vars
import mindustry.mod.Mod
import mindustry.net.ArcNetProvider
import mindustry.net.Net
import multiplayer.dialogs.JoinRoomDialog

@Suppress("unused")
class Main : Mod() {


    override fun init() {
        Vars.net = Net(ZeroTierNet(ArcNetProvider()))
        joinViaClaj = JoinRoomDialog()

    }

    companion object {
        var joinViaClaj: JoinRoomDialog? = null
        fun copy(text: String?) {
            if (text == null) return

            Core.app.clipboardText = text
            Vars.ui.showInfoFade("@copied")
        }
    }
}