package com.game.reschange

/**
 * Roda no processo separado que o Shizuku cria (nao e um processo de
 * app normal -- Context aqui nao se comporta como numa Activity/Service
 * comuns). Todo comando chega aqui ja com privilegio de shell ou root,
 * entao nao precisa de "su" -- e so ProcessBuilder direto.
 */
class ShellUserService : IShellService.Stub() {

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "EXEC_FAILED: ${e.message}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
