package org.rust.ide.sdk.add

import org.rust.cargo.icons.CargoIcons
import javax.swing.Icon

class RsAddCargoPanel : RsAddSdkPanel() {
    override val panelName: String = "Cargo"
    override val icon: Icon = CargoIcons.ICON
}
