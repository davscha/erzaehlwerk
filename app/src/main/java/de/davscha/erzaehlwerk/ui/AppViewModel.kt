package de.davscha.erzaehlwerk.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.davscha.erzaehlwerk.data.Book
import de.davscha.erzaehlwerk.data.BookRepository
import de.davscha.erzaehlwerk.data.TagLibrary
import de.davscha.erzaehlwerk.engine.Accelerator
import de.davscha.erzaehlwerk.engine.ChatEngine
import de.davscha.erzaehlwerk.engine.ComfyClient
import de.davscha.erzaehlwerk.engine.LiteRtEngine
import de.davscha.erzaehlwerk.engine.OllamaClient
import de.davscha.erzaehlwerk.engine.OllamaEngine
import de.davscha.erzaehlwerk.engine.OllamaModel
import de.davscha.erzaehlwerk.model.ModelCatalog
import de.davscha.erzaehlwerk.model.ModelDownloader
import de.davscha.erzaehlwerk.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Shelf : Screen
    data object Settings : Screen
    data class BookPage(val bookId: String) : Screen
}

sealed interface DownloadState {
    data object Missing : DownloadState
    data class Running(val downloaded: Long, val total: Long) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

sealed interface EngineState {
    data object Idle : EngineState
    data class Loading(val title: String) : EngineState
    data class Ready(val model: ModelInfo, val accelerator: Accelerator, val seconds: Double) : EngineState
    data class Failed(val message: String) : EngineState
}

/** Status einer Verbindung zum PC (Ollama oder ComfyUI) */
sealed interface PcStatus {
    data object Untested : PcStatus
    data object Testing : PcStatus
    data class Ok(val info: String) : PcStatus
    data class Failed(val message: String) : PcStatus
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    val repo = BookRepository(app)

    // ---------- Navigation ----------

    var screen by mutableStateOf<Screen>(Screen.Shelf)
        private set
    private val backStack = mutableListOf<Screen>()

    fun navigate(s: Screen) {
        backStack += screen
        screen = s
    }

    fun back(): Boolean {
        screen = backStack.removeLastOrNull() ?: return false
        return true
    }

    // ---------- Speicher ----------

    /** true = Bücher und Modelle liegen dauerhaft im Gerätespeicher (übersteht Neuinstallation) */
    var sharedStorage by mutableStateOf(Environment.isExternalStorageManager())
        private set

    // ---------- Bücherregal ----------

    val books = mutableStateListOf<Book>()

    fun book(id: String) = books.firstOrNull { it.id == id }

    fun createBook(title: String, idea: String, genre: String): Book {
        val b = Book(title = title.trim().ifBlank { "Neuer Roman" }, idea = idea.trim(), genre = genre, emoji = Book.emojis[genre] ?: "📖")
        saveBook(b)
        return b
    }

    fun saveBook(b: Book) {
        val updated = b.copy(updatedAt = System.currentTimeMillis())
        val i = books.indexOfFirst { it.id == b.id }
        if (i >= 0) books[i] = updated else books.add(0, updated)
        viewModelScope.launch(Dispatchers.IO) { repo.saveBook(updated) }
    }

    fun deleteBook(id: String) {
        books.removeAll { it.id == id }
        backStack.removeAll { it is Screen.BookPage && it.bookId == id }
        if ((screen as? Screen.BookPage)?.bookId == id) back()
        viewModelScope.launch(Dispatchers.IO) { repo.deleteBook(id) }
    }

    // ---------- KI: intern (Gemma auf dem Tablet) oder Ollama (PC) ----------

    private val internalEngine: ChatEngine = LiteRtEngine(app)
    private val ollamaEngine: ChatEngine = OllamaEngine { ollamaHost to ollamaPort }

    /** Ollama bevorzugen (wenn eingeschaltet) – ist der PC nicht erreichbar, springt Gemma auf dem Tablet ein */
    var preferOllama by mutableStateOf(prefs.getBoolean("ollama_bevorzugen", false))
        private set
    var ollamaHost by mutableStateOf(prefs.getString("ollama_host", "") ?: "")
        private set
    var ollamaPort by mutableStateOf(prefs.getInt("ollama_port", 11434))
        private set
    var ollamaModel by mutableStateOf(prefs.getString("ollama_model", "") ?: "")
        private set
    var ollamaStatus by mutableStateOf<PcStatus>(PcStatus.Untested)
        private set
    val ollamaModels = mutableStateListOf<OllamaModel>()

    /** Wird gerade Ollama benutzt? (eingeschaltet UND erreichbar) */
    val usingOllama get() = preferOllama && ollamaStatus is PcStatus.Ok && ollamaModel.isNotBlank()
    private val engine get() = if (usingOllama) ollamaEngine else internalEngine

    var selectedModel by mutableStateOf(ModelCatalog.all.firstOrNull { it.id == prefs.getString("modell", null) } ?: ModelCatalog.E4B)
        private set
    val downloads = mutableStateMapOf<String, DownloadState>()
    private val downloadJobs = mutableMapOf<String, Job>()

    var engineState by mutableStateOf<EngineState>(EngineState.Idle)
        private set

    /** Welche KI gerade dran ist, als Text für die Anzeige */
    val aiLabel: String get() = when {
        usingOllama -> "$ollamaModel · PC (Ollama)"
        preferOllama && ollamaStatus is PcStatus.Failed -> "${selectedModel.title} · Tablet (PC nicht erreichbar)"
        else -> "${selectedModel.title} · Tablet"
    }

    fun changePreferOllama(on: Boolean) {
        preferOllama = on
        prefs.edit().putBoolean("ollama_bevorzugen", on).apply()
        engineState = EngineState.Idle
        if (on) testOllama()
    }

    fun changeOllamaHost(h: String) {
        ollamaHost = h.trim()
        prefs.edit().putString("ollama_host", ollamaHost).apply()
        ollamaStatus = PcStatus.Untested
    }

    fun changeOllamaPort(p: Int) {
        ollamaPort = p
        prefs.edit().putInt("ollama_port", p).apply()
        ollamaStatus = PcStatus.Untested
    }

    fun selectOllamaModel(name: String) {
        ollamaModel = name
        prefs.edit().putString("ollama_model", name).apply()
        if (usingOllama) engineState = EngineState.Idle
    }

    fun testOllama() {
        if (ollamaHost.isBlank() || ollamaStatus is PcStatus.Testing) return
        ollamaStatus = PcStatus.Testing
        viewModelScope.launch {
            ollamaStatus = try {
                val client = OllamaClient(ollamaHost, ollamaPort)
                val version = client.version()
                val models = client.models()
                ollamaModels.clear()
                ollamaModels.addAll(models)
                if (models.isNotEmpty() && models.none { it.name == ollamaModel }) {
                    val pick = models.firstOrNull { it.name.startsWith("gemma4:12b") }
                        ?: models.firstOrNull { it.name.startsWith("gemma4:e4b") }
                        ?: models.firstOrNull { it.name.startsWith("gemma4") }
                        ?: models.minBy { it.sizeBytes }
                    selectOllamaModel(pick.name)
                }
                PcStatus.Ok("Ollama $version · ${models.size} Modelle")
            } catch (e: Exception) {
                PcStatus.Failed("Nicht erreichbar (${e.message ?: e.javaClass.simpleName})")
            }
            engineState = EngineState.Idle
        }
    }

    fun selectModel(m: ModelInfo) {
        selectedModel = m
        prefs.edit().putString("modell", m.id).apply()
        if (!usingOllama) engineState = EngineState.Idle
    }

    fun download(m: ModelInfo) {
        if (downloadJobs[m.id]?.isActive == true) return
        downloads[m.id] = DownloadState.Running(0, m.sizeBytes)
        downloadJobs[m.id] = viewModelScope.launch {
            try {
                ModelDownloader.download(context, m) { done, total -> downloads[m.id] = DownloadState.Running(done, total) }
                downloads[m.id] = DownloadState.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                downloads[m.id] = DownloadState.Missing
            } catch (e: Exception) {
                downloads[m.id] = DownloadState.Failed(e.message ?: "Fehler")
            }
        }
    }

    fun cancelDownload(m: ModelInfo) {
        downloadJobs[m.id]?.cancel()
    }

    fun deleteModel(m: ModelInfo) {
        if ((engineState as? EngineState.Ready)?.model == m) {
            internalEngine.close()
            engineState = EngineState.Idle
        }
        m.file(context).delete()
        m.partFile(context).delete()
        downloads[m.id] = DownloadState.Missing
    }

    /** Lädt die passende KI, falls nötig: Ollama (wenn erreichbar) oder Gemma auf dem Tablet */
    private suspend fun ensureLoaded() {
        val model = if (usingOllama) ModelInfo("ollama:$ollamaModel", ollamaModel, "Ollama", ollamaModel, "ollama", 0) else selectedModel
        val s = engineState
        if (s is EngineState.Ready && s.model == model) return
        if (!usingOllama && downloads[model.id] != DownloadState.Done) {
            throw IllegalStateException("${model.title} ist noch nicht heruntergeladen (Einstellungen).")
        }
        engineState = EngineState.Loading(model.title)
        val start = SystemClock.elapsedRealtime()
        try {
            val acc = engine.load(if (usingOllama) model.fileName else model.file(context).absolutePath)
            engineState = EngineState.Ready(model, acc, (SystemClock.elapsedRealtime() - start) / 1000.0)
        } catch (e: Exception) {
            engineState = EngineState.Failed(e.message ?: "Modell konnte nicht geladen werden")
            throw e
        }
    }

    /** Eine Frage an die KI – Grundlage für Plot und Schreiben in den nächsten Etappen */
    suspend fun ask(system: String, prompt: String, temperature: Double = 0.8, onChunk: (String) -> Unit = {}): String {
        ensureLoaded()
        withContext(Dispatchers.IO) { engine.startConversation(system, emptyList(), temperature) }
        val sb = StringBuilder()
        engine.send(prompt).collect { sb.append(it); onChunk(sb.toString()) }
        return sb.toString().trim()
    }

    /** KI-Test in den Einstellungen */
    var aiTest by mutableStateOf("")
        private set
    var aiTesting by mutableStateOf(false)
        private set

    fun testAi() {
        if (aiTesting) return
        aiTesting = true
        aiTest = ""
        viewModelScope.launch {
            aiTest = try {
                ask(
                    "Du bist ein Erzähler. Antworte auf Deutsch in einem einzigen Satz.",
                    "Beginne eine Geschichte mit einem stimmungsvollen ersten Satz.",
                ) { aiTest = it }
            } catch (e: Exception) {
                "⚠ ${e.message ?: "Fehler"}"
            } finally {
                aiTesting = false
            }
        }
    }

    // ---------- ComfyUI (Bilder) ----------

    var comfyHost by mutableStateOf(prefs.getString("comfy_host", null) ?: "")
        private set
    var comfyPort by mutableStateOf(prefs.getInt("comfy_port", 8080))
        private set
    var comfyStatus by mutableStateOf<PcStatus>(PcStatus.Untested)
        private set
    val comfyReady get() = comfyHost.isNotBlank() && comfyStatus is PcStatus.Ok

    fun changeComfyHost(h: String) {
        comfyHost = h.trim()
        prefs.edit().putString("comfy_host", comfyHost).apply()
        comfyStatus = PcStatus.Untested
    }

    fun changeComfyPort(p: Int) {
        comfyPort = p
        prefs.edit().putInt("comfy_port", p).apply()
        comfyStatus = PcStatus.Untested
    }

    fun testComfy() {
        if (comfyHost.isBlank() || comfyStatus is PcStatus.Testing) return
        comfyStatus = PcStatus.Testing
        viewModelScope.launch {
            comfyStatus = try {
                PcStatus.Ok(ComfyClient(comfyHost, comfyPort).test())
            } catch (e: Exception) {
                PcStatus.Failed("Nicht erreichbar – läuft ComfyUI mit --listen? (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    // ---------- Start / Rückkehr in die App ----------

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) { TagLibrary.load(context) }
    }

    /** Beim Start und bei Rückkehr: Speicher, Bücher, Modelle und PC-Verbindungen prüfen */
    fun refresh() {
        sharedStorage = Environment.isExternalStorageManager()
        ModelCatalog.all.forEach { m ->
            if (downloads[m.id] !is DownloadState.Running) downloads[m.id] = if (m.isDownloaded(context)) DownloadState.Done else DownloadState.Missing
        }
        val loaded = repo.loadBooks()
        books.clear()
        books.addAll(loaded)
        if (preferOllama && ollamaHost.isNotBlank()) testOllama()
        if (comfyHost.isNotBlank()) testComfy()
    }

    override fun onCleared() {
        internalEngine.close()
        ollamaEngine.close()
    }
}
