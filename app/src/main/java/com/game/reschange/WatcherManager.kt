package com.game.reschange

import android.content.Context
import java.io.DataOutputStream
import java.io.File

/**
 * Copia reschange_watcher.sh de assets/ para /data/local/tmp e garante
 * que ele esteja rodando em background como root.
 *
 * O watcher monitora o app em primeiro plano (mesma tecnica do PerfGame:
 * `dumpsys window`) e troca a RESOLUCAO REAL DA TELA via `wm size` quando
 * o app esta na lista configurada — sem depender de GameManagerService,
 * CATEGORY_GAME ou Game Space.
 */
object WatcherManager {

    private const val SCRIPT_PATH = "/data/local/tmp/reschange_watcher.sh"

    fun ensureRunning(context: Context) {
        try {
            copyScriptIfNeeded(context)
            if (!isRunning()) {
                startWatcher()
            }
        } catch (_: Exception) {}
    }

    private fun copyScriptIfNeeded(context: Context) {
        val target = File(SCRIPT_PATH)
        val assetBytes = context.assets.open("reschange_watcher.sh").readBytes()

        val needsCopy = !target.exists() || !target.readBytes().contentEquals(assetBytes)
        if (needsCopy) {
            // Escreve num arquivo temporario proprio do app e move via su,
            // pois o app nao tem permissao de escrever direto em /data/local/tmp.
            val tmp = File(context.cacheDir, "reschange_watcher.sh")
            tmp.writeBytes(assetBytes)
            runAsRoot(
                "cp ${tmp.absolutePath} $SCRIPT_PATH && " +
                    "chmod 755 $SCRIPT_PATH"
            )
        }
    }

    private fun isRunning(): Boolean {
        val result = runAsRootWithOutput("pgrep -f reschange_watcher.sh")
        return result.trim().isNotEmpty()
    }

    private fun startWatcher() {
        runAsRoot("nohup sh $SCRIPT_PATH > /dev/null 2>&1 &")
    }

    fun stopWatcher() {
        runAsRoot("pkill -f reschange_watcher.sh; wm size reset")
    }

    private fun runAsRoot(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (_: Exception) {}
    }

    private fun runAsRootWithOutput(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            ""
        }
    }
}
