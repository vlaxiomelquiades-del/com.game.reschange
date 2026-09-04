package com.game.reschange

import android.content.Context
import java.io.DataOutputStream

/**
 * Ponto unico de execucao privilegiada do app.
 *
 * Antes, cada arquivo (WatcherManager, GameCategoryHint, MainActivity,
 * SettingsActivity) tinha sua propria copia de runAsRoot() via
 * Runtime.exec("su"). Agora todos passam por aqui, que decide entre
 * root (su) e Shizuku (UserService) de acordo com BackendPrefs -- o
 * resto do app nao precisa saber qual dos dois esta rodando por baixo.
 */
object PrivilegedExecutor {

    /** Roda um comando descartando a saida (equivalente ao antigo runAsRoot). */
    fun run(context: Context, command: String) {
        when (BackendPrefs.getBackend(context)) {
            BackendPrefs.BACKEND_SHIZUKU -> ShizukuExecutor.runCommand(context, command)
            else -> suExec(command, asShellUid = false)
        }
    }

    /** Roda um comando e devolve stdout+stderr (equivalente ao antigo runAsRootWithOutput). */
    fun runWithOutput(context: Context, command: String): String {
        return when (BackendPrefs.getBackend(context)) {
            BackendPrefs.BACKEND_SHIZUKU -> ShizukuExecutor.runCommandWithOutput(context, command)
            else -> suExec(command, asShellUid = false)
        }
    }

    /**
     * Especifico do GameCategoryHint: precisa rodar como UID de shell
     * (2000), nao root (0), porque o metodo oculto valida a UID de quem
     * chama. No root isso exige "su 2000" (nem toda gerenciadora aceita).
     * No Shizuku isso e automatico -- uma sessao iniciada via ADB/wireless
     * debugging ja roda nativamente como UID 2000.
     */
    fun runAsShellUid(context: Context, command: String): String {
        return when (BackendPrefs.getBackend(context)) {
            BackendPrefs.BACKEND_SHIZUKU -> ShizukuExecutor.runCommandWithOutput(context, command)
            else -> suExec(command, asShellUid = true)
        }
    }

    /**
     * BUG FIX: "su 2000" puro (sem -lp) e a forma menos universal de pedir
     * uma UID nao-root pra gerenciadora -- nem toda su honra um segundo
     * argumento posicional como UID sem mais nada. O CMDLite (modulo
     * separado, ja em uso) faz a mesma troca de identidade pra shell com
     * "su -lp 2000 -c ...", entao alinhamos aqui: -l (login shell) + -p
     * (preserva ambiente) sao aceitos pela sintaxe do Magisk e tendem a
     * ser respeitados por mais gerenciadoras do que o "su 2000" cru.
     * Se a gerenciadora do usuario ainda assim ignorar a UID pedida e
     * ficar em root puro (0), tudo que depende de UID literal de shell
     * (GameCategoryHint) falha OU "funciona" sem nunca se confirmar de
     * verdade -- use debugShellUid() abaixo pra confirmar qual dos dois
     * esta acontecendo antes de assumir que e outra coisa.
     */
    private fun suExec(command: String, asShellUid: Boolean): String {
        return try {
            val process = if (asShellUid)
                Runtime.getRuntime().exec(arrayOf("su", "-lp", "2000"))
            else
                Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val output = process.inputStream.bufferedReader().readText()
            val errOutput = process.errorStream.bufferedReader().readText()
            process.waitFor()
            output + errOutput
        } catch (e: Exception) {
            "EXEC_FAILED: ${e.message}"
        }
    }

    /**
     * Diagnostico: confirma se runAsShellUid() esta realmente caindo pra
     * UID 2000 (shell) ou se a gerenciadora esta ignorando o pedido e
     * ficando em UID 0 (root). "uid=2000(shell)" = ok; "uid=0(root)" =
     * a gerenciadora nao suporta a troca e o GameCategoryHint vai
     * continuar instavel nesse backend. Chame de um botao de debug, do
     * Logcat, ou temporariamente de onCreate pra conferir uma vez.
     */
    fun debugShellUid(context: Context): String = runAsShellUid(context, "id").trim()
}
