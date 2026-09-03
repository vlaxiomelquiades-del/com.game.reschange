package com.game.reschange

import android.content.Context
import java.io.DataOutputStream

/**
 * Forca a categoria "Jogo" de um pacote-alvo.
 *
 * Mecanismo reverso-engenheirado direto do APK do TMPAD (Themed Per-App
 * Downscale), extraido do classes.dex (strings + tabela de metodos/classes
 * lida diretamente do formato .dex). Descobertas principais:
 *
 * 1) O installer-of-record do pacote-alvo precisa ser especificamente
 *    "com.android.shell" — nao o nosso proprio pacote. Reinstala usando o
 *    fluxo de SESSAO do pm (install-create/install-write/install-commit
 *    com "-i com.android.shell" e "--bypass-low-target-sdk-block", ambos
 *    confirmados como strings literais no dex do TMPAD).
 *
 * 2) Na chamada `service call package`, o parametro de "pacote chamador"
 *    tambem precisa ser literalmente "com.android.shell".
 *
 * 3) VERIFICACAO REAL: a tabela de metodos do dex do TMPAD referencia
 *    PackageManager.getApplicationInfo() e ApplicationInfoFlags.of() —
 *    APIs publicas e documentadas (nao hidden API, category e um campo
 *    publico do ApplicationInfo desde a API 26). Entao a verificacao mais
 *    provavel e simplesmente reler getApplicationInfo(pkg).category e
 *    conferir se bateu CATEGORY_GAME, em vez de confiar no texto de saida
 *    do `service call` (que pode "parecer sucesso" sem ter feito nada).
 */
object GameCategoryHint {

    private const val PREF_NAME = "category_hint_prefs"
    private const val KEY_CODE = "working_code_global"
    private const val KEY_FORCED_SET = "forced_pkgs"
    const val CATEGORY_GAME = 0
    private const val CATEGORY_UNDEFINED = -1
    private const val SHELL_PKG = "com.android.shell"

    val KNOWN_CODES = intArrayOf(50, 49, 47)

    data class Result(val success: Boolean, val codeUsed: Int, val message: String)

    fun forceGameCategory(context: Context, pkg: String): Result {
        val result = runCategoryChange(context, pkg, CATEGORY_GAME)
        if (result.success) markForced(context, pkg)
        return result
    }

    fun removeGameCategory(context: Context, pkg: String): Result {
        val result = runCategoryChange(context, pkg, CATEGORY_UNDEFINED)
        if (result.success) unmarkForced(context, pkg)
        return result
    }

    private fun runCategoryChange(context: Context, pkg: String, targetCategory: Int): Result {
        val originalInstaller = getInstaller(pkg)

        val reinstallOut = reinstallWithInstaller(pkg, SHELL_PKG)
        val currentInstaller = getInstaller(pkg)
        if (currentInstaller != SHELL_PKG) {
            return Result(
                false, -1,
                "Reinstalação com installer=com.android.shell falhou. " +
                    "Installer atual: ${currentInstaller ?: "nenhum"}. Saída: ${reinstallOut.take(250)}"
            )
        }

        val savedCode = getSavedCode(context)
        val codesToTry = buildList {
            if (savedCode != null) add(savedCode)
            addAll(KNOWN_CODES.filter { it != savedCode })
        }

        var workedCode = -1
        val attempts = mutableListOf<String>()
        for (code in codesToTry) {
            val cmdOutput = runServiceCall(code, pkg, targetCategory)
            val actualCategory = readActualCategory(context, pkg)
            if (actualCategory == targetCategory) {
                workedCode = code
                break
            }
            attempts.add("$code: categoria lida=$actualCategory (esperado $targetCategory). Saída: ${cmdOutput.take(100).trim()}")
        }

        if (!originalInstaller.isNullOrBlank() && originalInstaller != SHELL_PKG) {
            reinstallWithInstaller(pkg, originalInstaller)
        }

        return if (workedCode != -1) {
            saveCode(context, workedCode)
            val label = if (targetCategory == CATEGORY_GAME) "forçada" else "removida"
            Result(true, workedCode, "Categoria de jogo $label e VERIFICADA (código $workedCode, category=$targetCategory confirmado)")
        } else {
            Result(
                false, -1,
                "Installer virou com.android.shell, mas em nenhuma tentativa a categoria realmente mudou " +
                    "(verificado via getApplicationInfo().category). Detalhes: " + attempts.joinToString(" | ")
            )
        }
    }

    fun readActualCategory(context: Context, pkg: String): Int {
        return try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            info.category
        } catch (e: Exception) {
            Int.MIN_VALUE
        }
    }

    fun isActuallyGame(context: Context, pkg: String): Boolean {
        return readActualCategory(context, pkg) == CATEGORY_GAME
    }

    fun isForcedByUs(context: Context, pkg: String): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FORCED_SET, emptySet())!!.contains(pkg)
    }

    private fun markForced(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_FORCED_SET, emptySet())!!.toMutableSet()
        set.add(pkg)
        prefs.edit().putStringSet(KEY_FORCED_SET, set).apply()
    }

    private fun unmarkForced(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_FORCED_SET, emptySet())!!.toMutableSet()
        set.remove(pkg)
        prefs.edit().putStringSet(KEY_FORCED_SET, set).apply()
    }

    private fun reinstallWithInstaller(pkg: String, installer: String): String {
        val script = """
            create_out=$(cmd package install-create -r --bypass-low-target-sdk-block -i $installer 2>&1)
            sid=$(echo "${'$'}create_out" | sed -n "s/.*created install session \[\([0-9][0-9]*\)\].*/\1/p" | head -n1)
            if [ -z "${'$'}sid" ]; then
                echo "NO_SESSION: ${'$'}create_out"
            else
                pm path $pkg | sed -n 's/^package://p' | while IFS= read -r path; do
                    size=$(stat -c%s "${'$'}path" 2>/dev/null || ls -l "${'$'}path" | awk '{print ${'$'}5}')
                    split=$(basename "${'$'}path")
                    cmd package install-write -S "${'$'}size" "${'$'}sid" "${'$'}split" "${'$'}path"
                done
                cmd package install-commit "${'$'}sid"
            fi
        """.trimIndent()
        return runAsRootWithOutput(script)
    }

    private fun runServiceCall(code: Int, pkg: String, category: Int): String {
        val cmd = "service call package $code s16 $pkg i32 $category s16 $SHELL_PKG"
        return runAsRootWithOutput(cmd)
    }

    private fun getInstaller(pkg: String): String? {
        val output = runAsRootWithOutput("dumpsys package $pkg 2>/dev/null | grep -m1 installerPackageName")
        val line = output.lineSequence().firstOrNull { it.contains("installerPackageName=") } ?: return null
        val value = line.substringAfter("installerPackageName=", "").trim()
        return value.ifBlank { null }
    }

    private fun getSavedCode(context: Context): Int? {
        val v = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_CODE, -1)
        return if (v == -1) null else v
    }

    private fun saveCode(context: Context, code: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CODE, code).apply()
    }

    private fun runAsRootWithOutput(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "2000"))
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val output = process.inputStream.bufferedReader().readText()
            val errOutput = process.errorStream.bufferedReader().readText()
            process.waitFor()
            (output + errOutput)
        } catch (e: Exception) {
            "EXEC_FAILED: ${e.message}"
        }
    }
}
