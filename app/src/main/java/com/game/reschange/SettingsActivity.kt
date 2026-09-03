package com.game.reschange

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class SettingsActivity : AppCompatActivity() {

    private var awaitingShizukuPermission = false
    private var revertingBackendSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.settingsToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Operation Mode"

        val radioGroup    = findViewById<RadioGroup>(R.id.radioGroupMode)
        val radioDefault  = findViewById<RadioButton>(R.id.radioDefault)
        val radioAlt      = findViewById<RadioButton>(R.id.radioAlternative)

        // Marca o modo atual
        val currentMode = ModePrefs.getMode(this)
        if (currentMode == ModePrefs.MODE_ALTERNATIVE) {
            radioAlt.isChecked = true
        } else {
            radioDefault.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioAlternative -> ModePrefs.MODE_ALTERNATIVE
                else                  -> ModePrefs.MODE_DEFAULT
            }

            ModePrefs.saveMode(this, newMode)

            // Salva o modo em arquivo world-readable (referencia legada)
            writeModeFile(newMode)

            if (newMode == ModePrefs.MODE_ALTERNATIVE) {
                // Desabilita a sincronizacao do GMS para o namespace game_overlay
                // Isso resolve o problema de "depois de um tempo tem que reativar tudo"
                // O GMS sobrescreve device_config periodicamente -- isso impede isso
                PrivilegedExecutor.run(this, "device_config set_sync_disabled_for_tests persistent")
                Toast.makeText(this,
                    "Alternative Mode enabled.\nGMS sync disabled for game_overlay.",
                    Toast.LENGTH_LONG).show()
            } else {
                // Reabilita sincronizacao ao voltar pro Default Mode
                PrivilegedExecutor.run(this, "device_config set_sync_disabled_for_tests none")
                Toast.makeText(this,
                    "Default Mode enabled.",
                    Toast.LENGTH_SHORT).show()
            }
        }

        setupBackendSelector()
    }

    /**
     * Segundo seletor da tela: qual backend privilegiado usar (root ou
     * Shizuku). Independente do RadioGroup de Default/Alternative acima --
     * um decide COMO a resolucao e aplicada, o outro decide COM QUE
     * PERMISSAO.
     */
    private fun setupBackendSelector() {
        val radioGroupBackend = findViewById<RadioGroup>(R.id.radioGroupBackend)
        val radioRoot          = findViewById<RadioButton>(R.id.radioRoot)
        val radioShizuku       = findViewById<RadioButton>(R.id.radioShizuku)

        fun applySelectionSilently(backend: String) {
            revertingBackendSelection = true
            if (backend == BackendPrefs.BACKEND_SHIZUKU) radioShizuku.isChecked = true else radioRoot.isChecked = true
            revertingBackendSelection = false
        }

        applySelectionSilently(BackendPrefs.getBackend(this))

        radioGroupBackend.setOnCheckedChangeListener { _, checkedId ->
            if (revertingBackendSelection) return@setOnCheckedChangeListener

            when (checkedId) {
                R.id.radioShizuku -> {
                    when {
                        ShizukuExecutor.hasPermission() -> {
                            BackendPrefs.saveBackend(this, BackendPrefs.BACKEND_SHIZUKU)
                            Toast.makeText(this, "Backend: Shizuku", Toast.LENGTH_SHORT).show()
                        }
                        ShizukuExecutor.isAvailable() -> {
                            awaitingShizukuPermission = true
                            ShizukuExecutor.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                            Toast.makeText(this,
                                "Conceda a permissão do Shizuku na tela que abriu.",
                                Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this,
                                "Shizuku não detectado. Instale e inicie o Shizuku antes de usar esse modo.",
                                Toast.LENGTH_LONG).show()
                            applySelectionSilently(BackendPrefs.BACKEND_ROOT)
                        }
                    }
                }
                else -> {
                    BackendPrefs.saveBackend(this, BackendPrefs.BACKEND_ROOT)
                    Toast.makeText(this, "Backend: Root", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (awaitingShizukuPermission) {
            awaitingShizukuPermission = false
            val radioRoot    = findViewById<RadioButton>(R.id.radioRoot)
            val radioShizuku = findViewById<RadioButton>(R.id.radioShizuku)
            if (ShizukuExecutor.hasPermission()) {
                BackendPrefs.saveBackend(this, BackendPrefs.BACKEND_SHIZUKU)
                revertingBackendSelection = true
                radioShizuku.isChecked = true
                revertingBackendSelection = false
                Toast.makeText(this, "Backend: Shizuku", Toast.LENGTH_SHORT).show()
            } else {
                revertingBackendSelection = true
                radioRoot.isChecked = true
                revertingBackendSelection = false
                Toast.makeText(this, "Permissão do Shizuku não concedida.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * Grava /data/local/tmp/reschange_mode.txt com chmod 644
     * para qualquer processo ler (sem uso atual, mantido por compatibilidade).
     */
    private fun writeModeFile(mode: String) {
        try {
            val tmp = java.io.File(cacheDir, "reschange_mode.txt")
            tmp.writeText(mode)
            PrivilegedExecutor.run(this,
                "cp ${tmp.absolutePath} /data/local/tmp/reschange_mode.txt && chmod 644 /data/local/tmp/reschange_mode.txt")
        } catch (_: Exception) {}
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 8899
    }
}
