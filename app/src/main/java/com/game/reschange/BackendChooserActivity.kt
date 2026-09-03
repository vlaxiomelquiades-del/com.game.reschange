package com.game.reschange

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat

/**
 * Tela mostrada apenas na primeira execucao do app, antes da MainActivity,
 * para escolher o backend de execucao privilegiada (root ou Shizuku).
 * Pode ser trocado depois em Settings -- ver BackendPrefs/SettingsActivity.
 */
class BackendChooserActivity : AppCompatActivity() {

    private var awaitingShizukuPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_backend_chooser)

        findViewById<CardView>(R.id.cardRoot).setOnClickListener {
            BackendPrefs.saveBackend(this, BackendPrefs.BACKEND_ROOT)
            finishChooser()
        }

        findViewById<CardView>(R.id.cardShizuku).setOnClickListener {
            when {
                ShizukuExecutor.hasPermission() -> {
                    BackendPrefs.saveBackend(this, BackendPrefs.BACKEND_SHIZUKU)
                    finishChooser()
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
                        "Shizuku não detectado. Instale e inicie o Shizuku, ou escolha Root.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (awaitingShizukuPermission) {
            awaitingShizukuPermission = false
            if (ShizukuExecutor.hasPermission()) {
                BackendPrefs.saveBackend(this, BackendPrefs.BACKEND_SHIZUKU)
                finishChooser()
            } else {
                Toast.makeText(this, "Permissão do Shizuku não concedida.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishChooser() {
        BackendPrefs.setChooserDone(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 7788
    }
}
