package com.game.reschange

import android.content.Context
import java.io.File

/**
 * Copia reschange_watcher.sh de assets/ para /data/local/tmp e garante
 * que ele esteja rodando em background com privilegio elevado (root ou
 * Shizuku, de acordo com BackendPrefs -- ver PrivilegedExecutor).
 *
 * O watcher monitora o app em primeiro plano (mesma tecnica do PerfGame:
 * `dumpsys window`) e troca a RESOLUCAO REAL DA TELA via `wm size` quando
 * o app esta na lista configurada -- sem depender de GameManagerService,
 * CATEGORY_GAME ou Game Space.
 *
 * O script em si, uma vez iniciado com nohup e detached, roda como um
 * processo comum do Linux independente de quem o lancou -- sobrevive
 * tanto a saida do "su" quanto ao unbind da UserService do Shizuku.
 */
object WatcherManager {

    private const val SCRIPT_PATH = "/data/local/tmp/reschange_watcher.sh"

    fun ensureRunning(context: Context) {
        try {
            copyScriptIfNeeded(context)
            if (!isRunning(context)) {
                startWatcher(context)
            }
        } catch (_: Exception) {}
    }

    private fun copyScriptIfNeeded(context: Context) {
        val target = File(SCRIPT_PATH)
        val assetBytes = context.assets.open("reschange_watcher.sh").readBytes()

        val needsCopy = !target.exists() || !target.readBytes().contentEquals(assetBytes)
        if (needsCopy) {
            // Escreve num arquivo temporario proprio do app e move via
            // backend privilegiado, pois o app nao tem permissao de
            // escrever direto em /data/local/tmp.
            val tmp = File(context.cacheDir, "reschange_watcher.sh")
            tmp.writeBytes(assetBytes)
            PrivilegedExecutor.run(
                context,
                "cp ${tmp.absolutePath} $SCRIPT_PATH && chmod 755 $SCRIPT_PATH"
            )
        }
    }

    private fun isRunning(context: Context): Boolean {
        val result = PrivilegedExecutor.runWithOutput(context, "pgrep -f reschange_watcher.sh")
        return result.trim().isNotEmpty()
    }

    private fun startWatcher(context: Context) {
        // nohup + & para o processo sobreviver depois que o backend
        // (su ou a UserService do Shizuku) sair/desconectar
        PrivilegedExecutor.run(context, "nohup sh $SCRIPT_PATH > /dev/null 2>&1 &")
    }

    fun stopWatcher(context: Context) {
        PrivilegedExecutor.run(context, "pkill -f reschange_watcher.sh; wm size reset")
    }
}
