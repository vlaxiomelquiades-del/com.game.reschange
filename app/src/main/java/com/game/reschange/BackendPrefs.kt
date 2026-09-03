package com.game.reschange

import android.content.Context

/**
 * Guarda qual backend de execucao privilegiada o usuario escolheu:
 * root (su) ou Shizuku (UserService, sem necessidade de root).
 * Separado do ModePrefs (Default/Alternative), que decide COMO a
 * resolucao e aplicada -- este aqui decide COM QUE PERMISSAO.
 */
object BackendPrefs {
    private const val PREF_NAME = "backend_prefs"
    private const val KEY_BACKEND = "privileged_backend"
    private const val KEY_CHOOSER_DONE = "chooser_done"

    const val BACKEND_ROOT    = "root"
    const val BACKEND_SHIZUKU = "shizuku"

    fun getBackend(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND, BACKEND_ROOT) ?: BACKEND_ROOT
    }

    fun saveBackend(context: Context, backend: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, backend).apply()
    }

    fun isShizuku(context: Context): Boolean = getBackend(context) == BACKEND_SHIZUKU

    /** true assim que o usuario ja passou pela tela inicial de escolha. */
    fun isChooserDone(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHOOSER_DONE, false)
    }

    fun setChooserDone(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CHOOSER_DONE, true).apply()
    }

    /** Texto curto pro subtitulo do toolbar ("Root ativo" / "Shizuku ativo"). */
    fun label(context: Context): String {
        return if (isShizuku(context)) "Shizuku ativo" else "Root ativo"
    }
}
