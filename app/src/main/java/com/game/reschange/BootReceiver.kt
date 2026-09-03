package com.game.reschange

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Garante que o watcher de resolucao esteja rodando apos o boot,
 * assim como o service.sh de um modulo Magisk/KernelSU faria.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WatcherManager.ensureRunning(context)
        }
    }
}
