package com.game.reschange

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Ponte para o Shizuku. Mantem uma UserService vinculada (processo
 * separado, mesma ideia de exec que o app ja usa via su, so que sem
 * precisar de root) e expoe chamadas bloqueantes equivalentes ao antigo
 * runAsRoot()/runAsRootWithOutput(), pra encaixar sem reescrever
 * WatcherManager/GameCategoryHint/MainActivity por completo.
 */
object ShizukuExecutor {

    // Versao do CONTRATO do servico (metodos da AIDL), nao a versionCode
    // do app -- so precisa mudar se a interface IShellService mudar.
    private const val SERVICE_VERSION = 1
    private const val BIND_TIMEOUT_SECONDS = 10L

    @Volatile private var service: IShellService? = null
    private val bindLock = Object()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder?.isBinderAlive == true) IShellService.Stub.asInterface(binder) else null
            synchronized(bindLock) { bindLock.notifyAll() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private fun args(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellUserService::class.java.name)
    )
        .processNameSuffix("shell_service")
        .debuggable(false)
        .version(SERVICE_VERSION)
        .daemon(true)

    /** Shizuku instalado e respondendo (nao diz nada sobre permissao). */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.getVersion() >= 11
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Dispara o dialogo de permissao do Shizuku. O resultado nao vem por
     * callback aqui -- quem chama deve reconferir hasPermission() no
     * onResume() da Activity depois que o usuario voltar da tela do
     * Shizuku (ver BackendChooserActivity / SettingsActivity).
     */
    fun requestPermission(requestCode: Int) {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    @Synchronized
    private fun ensureBound(context: Context): IShellService? {
        service?.let { if (it.asBinder().isBinderAlive) return it }
        if (!hasPermission()) return null
        synchronized(bindLock) {
            Shizuku.bindUserService(args(context.applicationContext), connection)
            if (service == null) {
                bindLock.wait(TimeUnit.SECONDS.toMillis(BIND_TIMEOUT_SECONDS))
            }
        }
        return service
    }

    fun runCommand(context: Context, command: String) {
        try {
            ensureBound(context)?.exec(command)
        } catch (_: Exception) {
        }
    }

    fun runCommandWithOutput(context: Context, command: String): String {
        return try {
            ensureBound(context)?.exec(command) ?: "SHIZUKU_UNAVAILABLE"
        } catch (e: Exception) {
            "EXEC_FAILED: ${e.message}"
        }
    }
}
