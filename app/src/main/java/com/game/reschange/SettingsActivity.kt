package com.game.reschange

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class SettingsActivity : AppCompatActivity() {

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
                // O GMS sobrescreve device_config periodicamente — isso impede isso
                runAsRoot("device_config set_sync_disabled_for_tests persistent")
                Toast.makeText(this,
                    "Alternative Mode enabled.\nGMS sync disabled for game_overlay.",
                    Toast.LENGTH_LONG).show()
            } else {
                // Reabilita sincronizacao ao voltar pro Default Mode
                runAsRoot("device_config set_sync_disabled_for_tests none")
                Toast.makeText(this,
                    "Default Mode enabled.",
                    Toast.LENGTH_SHORT).show()
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
            val process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)
            os.writeBytes("cp ${tmp.absolutePath} /data/local/tmp/reschange_mode.txt\n")
            os.writeBytes("chmod 644 /data/local/tmp/reschange_mode.txt\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (_: Exception) {}
    }

    private fun runAsRoot(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (_: Exception) {}
    }
}
