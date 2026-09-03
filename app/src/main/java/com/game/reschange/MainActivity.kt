package com.game.reschange

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.slider.Slider
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var toggleModified: SwitchMaterial
    private lateinit var allApps: List<AppInfo>
    private var showOnlyModified = false
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Primeira execucao: manda escolher Root ou Shizuku antes de
        // mostrar qualquer outra tela.
        if (!BackendPrefs.isChooserDone(this)) {
            startActivity(Intent(this, BackendChooserActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.myToolbar))
        supportActionBar?.subtitle = BackendPrefs.label(this)

        recyclerView   = findViewById(R.id.appList)
        toggleModified = findViewById(R.id.toggleModified)
        recyclerView.layoutManager = LinearLayoutManager(this)

        allApps = getUserInstalledApps()
        adapter = AppListAdapter(emptyList()) { appInfo ->
            showResolutionDialog(appInfo.packageName)
        }
        recyclerView.adapter = adapter
        filterAppList()

        toggleModified.setOnCheckedChangeListener { _, isChecked ->
            showOnlyModified = isChecked
            filterAppList()
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            val packages = ResChangePrefs.getAllPackages(this)
            for (pkg in packages) {
                PrivilegedExecutor.run(this, buildDisableCommand(pkg))
                PrivilegedExecutor.run(this, "am force-stop $pkg")
            }
            ResChangePrefs.clearAll(this)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "All resolutions reset to default", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reflete aqui caso o usuario tenha trocado o backend na tela de
        // Settings e voltado -- o subtitulo do toolbar sempre mostra o
        // que esta realmente ativo agora.
        supportActionBar?.subtitle = BackendPrefs.label(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText.orEmpty()
                filterAppList()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun filterAppList() {
        var list = allApps
        if (showOnlyModified)
            list = list.filter { ResChangePrefs.getScale(this, it.packageName) < 1.0f }
        if (currentQuery.isNotEmpty()) {
            val q = currentQuery.lowercase(Locale.getDefault())
            list = list.filter {
                it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        // Jogos em cima, outros apps embaixo, cada grupo com seu cabecalho
        val games = list.filter { it.isGame }
        val others = list.filter { !it.isGame }
        val sectioned = mutableListOf<ListItem>()
        if (games.isNotEmpty()) {
            sectioned.add(ListItem.Header("🎮 Jogos"))
            sectioned.addAll(games.map { ListItem.App(it) })
        }
        if (others.isNotEmpty()) {
            sectioned.add(ListItem.Header("📱 Outros apps"))
            sectioned.addAll(others.map { ListItem.App(it) })
        }
        adapter.submitList(sectioned)
    }

    // Lista TODOS os apps instalados -- nao so os com icone de launcher
    private fun getUserInstalledApps(): List<AppInfo> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                app.packageName != packageName
            }
            .distinctBy { it.packageName }
            .map { app ->
                AppInfo(
                    name = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    icon = try { pm.getApplicationIcon(app) }
                           catch (_: Exception) { pm.defaultActivityIcon },
                    isGame = app.category == GameCategoryHint.CATEGORY_GAME
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // Recarrega a lista de apps (usado apos forcar/remover categoria de jogo,
    // pra mover o app pra secao certa imediatamente)
    private fun refreshAppList() {
        allApps = getUserInstalledApps()
        filterAppList()
    }

    private fun showResolutionDialog(packageName: String) {
        val savedScale = ResChangePrefs.getScale(this, packageName)
        val isAlt      = ModePrefs.isAlternative(this)
        val modeLabel  = if (isAlt) "Alternative" else "Default"
        val appName    = allApps.find { it.packageName == packageName }?.name ?: packageName

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val modeText = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, 8)
            text = "Mode: $modeLabel"
            setTextColor(0xFF888888.toInt())
        }

        val scaleText = TextView(this).apply {
            textSize = 18f
            setPadding(0, 0, 0, 20)
            text = "Scale: ${(savedScale * 100).toInt()}%"
        }

        val slider = Slider(this).apply {
            valueFrom    = 0.3f
            valueTo      = 1.0f
            stepSize     = 0.05f
            value        = savedScale
            isTickVisible = true
        }

        slider.addOnChangeListener { _, value, _ ->
            scaleText.text = "Scale: ${(value * 100).toInt()}%"
        }

        // ── Forcar/Remover categoria de jogo (estilo TMPAD) ─────────────────
        // Chama o metodo oculto setApplicationCategoryHint via transacao
        // Binder crua, testando os codigos de transacao conhecidos.
        val categoryDivider = TextView(this).apply {
            setPadding(0, 24, 0, 4)
            text = "— Game Category —"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }

        val categoryStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 10)
        }

        val categoryButton = Button(this)

        // 3 estados possiveis:
        // 1) Ja e jogo nativo (o sistema ja reconhece) -> nao mostra botao
        // 2) Fomos nos que forcamos -> mostra "Remover categoria de jogo"
        // 3) Nao e jogo -> mostra "Forçar categoria de jogo"
        fun refreshCategoryUi() {
            val isGame = GameCategoryHint.isActuallyGame(this, packageName)
            val isForced = GameCategoryHint.isForcedByUs(this, packageName)

            when {
                isGame && !isForced -> {
                    categoryStatus.text = "Este app já é reconhecido como jogo pelo sistema."
                    categoryButton.visibility = View.GONE
                }
                isGame && isForced -> {
                    categoryStatus.text = "Categoria de jogo forçada por este app."
                    categoryButton.visibility = View.VISIBLE
                    categoryButton.text = "Remover categoria de jogo"
                }
                else -> {
                    categoryStatus.text = "Toque em \"Forçar categoria de jogo\" se o app não aplicar a resolução."
                    categoryButton.visibility = View.VISIBLE
                    categoryButton.text = "Forçar categoria de jogo"
                }
            }
        }
        refreshCategoryUi()

        categoryButton.setOnClickListener {
            val willForce = !GameCategoryHint.isForcedByUs(this, packageName)
            categoryButton.isEnabled = false
            categoryButton.text = "Testando…"
            // Trabalho de root/Shizuku/Binder fora da main thread pra nao travar a UI
            Thread {
                val result = if (willForce)
                    GameCategoryHint.forceGameCategory(this, packageName)
                else
                    GameCategoryHint.removeGameCategory(this, packageName)

                if (result.success) {
                    // Agora que a categoria mudou, reaplica a escala (se ja
                    // tiver uma salva) e reinicia o app, pra dar a melhor
                    // chance do cmd game mode pegar a mudanca na proxima
                    // vez que o jogo abrir.
                    val scale = ResChangePrefs.getScale(this, packageName)
                    if (scale < 1.0f) {
                        PrivilegedExecutor.run(this, buildApplyCommand(packageName, scale))
                    }
                    PrivilegedExecutor.run(this, "am force-stop $packageName")
                }

                runOnUiThread {
                    categoryButton.isEnabled = true
                    if (result.success) {
                        refreshCategoryUi()
                        refreshAppList()
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    } else {
                        refreshCategoryUi()
                        categoryStatus.text = "Falhou. Veja o erro completo no diálogo que abriu."
                        showFullErrorDialog("Categoria de jogo — erro", result.message)
                    }
                }
            }.start()
        }

        layout.addView(modeText)
        layout.addView(scaleText)
        layout.addView(slider)
        layout.addView(categoryDivider)
        layout.addView(categoryStatus)
        layout.addView(categoryButton)

        MaterialAlertDialogBuilder(this, R.style.MyRoundedDialog)
            .setTitle("Set Resolution Scale")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                var scale = String.format(Locale.US, "%.2f", slider.value).toFloat()

                if (scale == 0.95f) {
                    scale = 0.9f
                    Toast.makeText(this, "95% not supported. Using 90%.", Toast.LENGTH_SHORT).show()
                }

                if (scale >= 1.0f) {
                    PrivilegedExecutor.run(this, buildDisableCommand(packageName))
                    ResChangePrefs.removeScale(this, packageName)
                    Toast.makeText(this, "Resolution reset to 100% for $appName", Toast.LENGTH_SHORT).show()
                } else {
                    PrivilegedExecutor.run(this, buildApplyCommand(packageName, scale))
                    ResChangePrefs.saveScale(this, packageName, scale)
                    Toast.makeText(this,
                        "${(scale * 100).toInt()}% applied for $appName [$modeLabel Mode]",
                        Toast.LENGTH_SHORT).show()
                }

                adapter.notifyDataSetChanged()
                PrivilegedExecutor.run(this, "am force-stop $packageName")
                Toast.makeText(this, "$appName stopped. Relaunch to apply.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset") { _, _ ->
                PrivilegedExecutor.run(this, buildDisableCommand(packageName))
                ResChangePrefs.removeScale(this, packageName)
                adapter.notifyDataSetChanged()
                PrivilegedExecutor.run(this, "am force-stop $packageName")
                Toast.makeText(this, "$appName reset to 100%. Relaunch to apply.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    // Fluxo oficial confirmado na documentacao Android Developers
    // (developer.android.com/games/optimize/adpf/gamemode/gamemode-interventions):
    // 1) device_config put game_overlay configura o downscale para os modos
    //    2 (Performance) e 3 (Battery Saver)
    // 2) cmd game mode <performance|standard> ATIVA o modo pro pacote --
    //    sem esse passo a configuracao fica salva mas nunca e aplicada.
    // Sem Xposed reaplicando a config a cada abertura do app, o GMS pode
    // sobrescrever o device_config periodicamente com o tempo. Desabilitar
    // a sincronizacao do namespace game_overlay evita isso.
    private fun buildApplyCommand(pkg: String, scale: Float): String {
        val scaleStr = String.format(Locale.US, "%.2f", scale)
        val configCmd = "device_config put game_overlay $pkg " +
            "mode=2,downscaleFactor=$scaleStr:mode=3,downscaleFactor=$scaleStr"
        val activateCmd = "cmd game mode performance $pkg"
        val syncCmd = "device_config set_sync_disabled_for_tests persistent"
        return "$configCmd; $activateCmd; $syncCmd"
    }

    // Volta o pacote para o modo "standard" (sem downscale) e remove a config
    private fun buildDisableCommand(pkg: String): String {
        return "cmd game mode standard $pkg; device_config delete game_overlay $pkg"
    }

    // Mostra o texto de erro completo (o Toast corta mensagens longas).
    // TextView selecionável + botão de copiar, pra facilitar mandar o erro.
    private fun showFullErrorDialog(title: String, message: String) {
        val textView = TextView(this).apply {
            text = message
            setPadding(50, 30, 50, 30)
            setTextIsSelectable(true)
            textSize = 13f
        }
        val scroll = android.widget.ScrollView(this).apply { addView(textView) }

        MaterialAlertDialogBuilder(this, R.style.MyRoundedDialog)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Copiar") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Erro", message))
                Toast.makeText(this, "Erro copiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }
}
