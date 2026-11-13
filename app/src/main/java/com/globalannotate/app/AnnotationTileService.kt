package com.globalannotate.app

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** No-root entry point: a QS tile always available in the shade. */
class AnnotationTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, AnnotationService::class.java)
            .setAction(Actions.TOGGLE)
            .putExtra(Actions.EXTRA_TOKEN, Actions.TOKEN)
        runCatching { startForegroundService(intent) }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (Session.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
