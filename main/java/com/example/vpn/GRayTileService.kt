package com.example.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.data.model.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GRayTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        collectJob?.cancel()
        collectJob = scope.launch {
            VpnStateHolder.state.collect { runtime ->
                updateTile(runtime.status, runtime.activeServer?.name)
            }
        }
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val currentStatus = VpnStateHolder.state.value.status
        if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING) {
            val stopIntent = Intent(this, GRayVpnService::class.java).apply {
                action = GRayVpnService.ACTION_STOP
            }
            startService(stopIntent)
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_AUTO_CONNECT, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = android.app.PendingIntent.getActivity(
                    this,
                    303,
                    launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        } else {
            val startIntent = Intent(this, GRayVpnService::class.java).apply {
                action = GRayVpnService.ACTION_START
                putExtra(GRayVpnService.EXTRA_STARTED_IN_FOREGROUND, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }
    }

    private fun updateTile(status: VpnStatus, serverName: String?) {
        val tile = qsTile ?: return
        tile.state = when (status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> Tile.STATE_ACTIVE
            VpnStatus.DISCONNECTED, VpnStatus.DISCONNECTING -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                VpnStatus.CONNECTED -> serverName ?: "Подключено"
                VpnStatus.CONNECTING -> "Подключение…"
                VpnStatus.DISCONNECTING -> "Отключение…"
                VpnStatus.DISCONNECTED -> "Отключено"
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_CONNECT = "extra_auto_connect"
    }
}
