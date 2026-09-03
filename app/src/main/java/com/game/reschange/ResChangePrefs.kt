package com.game.reschange

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Gerencia as preferencias de escala por app.
 * Alem do SharedPreferences (para a UI), grava um arquivo
 * world-readable em /data/local/tmp/reschange_config.txt
 * para qualquer outro processo ler sem SELinux block.
 *
 * Formato do arquivo: uma linha por app
 *   com.exemplo.app=0.80
 */
object ResChangePrefs {
    private const val PREF_NAME   = "scale_prefs"
    const val CONFIG_FILE         = "/data/local/tmp/reschange_config.txt"

    fun saveScale(context: Context, packageName: String, scale: Float) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(packageName, scale) }
        writeConfigFile(context)
    }

    fun getScale(context: Context, packageName: String): Float {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat(packageName, 1.0f)
    }

    fun getAllPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).all.keys
    }

    fun removeScale(context: Context, packageName: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { remove(packageName) }
        writeConfigFile(context)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
        writeConfigFile(context)
    }

    /**
     * Grava /data/local/tmp/reschange_config.txt com chmod 644.
     * Legivel por qualquer processo (incluindo system_server)
     * sem depender de createPackageContext (bloqueado pelo SELinux 12+).
     *
     * IMPORTANTE: nao da pra escrever direto em /data/local/tmp a partir do
     * processo do proprio app (File.writeText) — SELinux bloqueia apps
     * comuns de criar arquivos ali, mesmo a pasta sendo 1777. A escrita
     * precisa passar por root: grava num arquivo proprio do app
     * (context.cacheDir, sempre gravavel) e copia via `su` pro destino.
     */
    private fun writeConfigFile(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lines = prefs.all
                .filterValues { it is Float && (it as Float) < 1.0f }
                .map { (pkg, scale) -> "$pkg=$scale" }
                .joinToString("\n")

            val tmp = File(context.cacheDir, "reschange_config.txt")
            tmp.writeText(lines)

            val process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)
            os.writeBytes("cp ${tmp.absolutePath} $CONFIG_FILE\n")
            os.writeBytes("chmod 644 $CONFIG_FILE\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (_: Exception) {}
    }
}
