package cn.shakeguard.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import cn.shakeguard.app.data.GuardRepository
import cn.shakeguard.app.ui.I18n

class ShakeGuardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        GuardRepository.init(applicationContext)
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        GuardRepository.init(applicationContext)
        GuardRepository.masterShieldEnabled = !GuardRepository.masterShieldEnabled
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val active = GuardRepository.masterShieldEnabled
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) {
            I18n.t("防摇护盾:开启", "ShakeGuard: ON")
        } else {
            I18n.t("防摇护盾:暂停", "ShakeGuard: OFF")
        }
        tile.updateTile()
    }
}
