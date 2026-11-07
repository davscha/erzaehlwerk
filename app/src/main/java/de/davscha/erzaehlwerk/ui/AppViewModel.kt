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
import de.davscha.erzaehlwerk.data.TagField
import de.davscha.erzaehlwerk.data.World
import de.davscha.erzaehlwerk.data.Person
import de.davscha.erzaehlwerk.data.Role
import de.davscha.erzaehlwerk.data.Gender
import de.davscha.erzaehlwerk.data.Place
import de.davscha.erzaehlwerk.data.Fundus
import de.davscha.erzaehlwerk.data.Outfit
import de.davscha.erzaehlwerk.data.Style
import de.davscha.erzaehlwerk.data.ImagePrompt
import de.davscha.erzaehlwerk.data.CharacterFunImport
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
    data object FundusPage : Screen
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
                PcStatus.Ok(ComfyClient(comfyHost, comfyPort).test()).also { loadLoras() }
            } catch (e: Exception) {
                PcStatus.Failed("Nicht erreichbar – läuft ComfyUI mit --listen? (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    // ---------- Bilder malen (ComfyUI) ----------

    var basePositive by mutableStateOf(prefs.getString("bild_positiv", null) ?: "masterpiece, highres")
        private set
    var baseNegative by mutableStateOf(prefs.getString("bild_negativ", null) ?: "lowres, photorealistic, photo background, multiple views")
        private set
    val loras = mutableStateListOf<String>()

    fun changeBasePositive(t: String) { basePositive = t; prefs.edit().putString("bild_positiv", t).apply() }
    fun changeBaseNegative(t: String) { baseNegative = t; prefs.edit().putString("bild_negativ", t).apply() }

    fun loadLoras() {
        if (comfyHost.isBlank()) return
        viewModelScope.launch {
            runCatching { ComfyClient(comfyHost, comfyPort).loras() }.onSuccess { loras.clear(); loras.addAll(it) }
        }
    }

    /** Malt ein Bild: Basis + Stil-Trigger + Tags; gibt PNG-Bytes zurück. PC weg → Knöpfe ausgrauen. */
    suspend fun paint(tags: String, style: Style? = fundus.defaultStyle, seed: Long = (1..999_999_999L).random(), extraNegative: String = "", onProgress: (Int) -> Unit = {}): ByteArray {
        require(comfyHost.isNotBlank()) { "ComfyUI ist noch nicht eingerichtet (Einstellungen)." }
        try {
            return ComfyClient(comfyHost, comfyPort).generate(
                ComfyClient.DEFAULT_WORKFLOW, ImagePrompt.join(basePositive, style?.trigger, tags), ImagePrompt.join(baseNegative, extraNegative),
                seed, style?.lora, style?.strength ?: 1f, onProgress = onProgress,
            )
        } catch (e: java.io.IOException) {
            if (e is java.net.ConnectException || e is java.net.SocketTimeoutException || e is java.net.NoRouteToHostException) {
                comfyStatus = PcStatus.Failed("Nicht erreichbar (${e.message})")
            }
            throw e
        }
    }

    /** Ids, deren Bild gerade gemalt wird, und letzte Fehler */
    val painting = mutableStateMapOf<String, Int>()
    val paintError = mutableStateMapOf<String, String>()

    private fun paintJob(id: String, block: suspend ((Int) -> Unit) -> Unit) {
        if (painting.containsKey(id)) return
        painting[id] = 0
        paintError.remove(id)
        viewModelScope.launch {
            try {
                block { painting[id] = it }
            } catch (e: Exception) {
                paintError[id] = e.message ?: "Fehler"
            } finally {
                painting.remove(id)
            }
        }
    }

    // ---------- Fundus (alle Bücher): Kleidung und Stile ----------

    var fundus by mutableStateOf(Fundus())
        private set

    private fun updateFundus(f: Fundus) {
        fundus = f
        viewModelScope.launch(Dispatchers.IO) { repo.saveFundus(f) }
    }

    fun outfit(id: String) = fundus.clothing.firstOrNull { it.id == id }
    fun fundusImage(name: String) = repo.fundusImage(name)

    fun saveOutfit(o: Outfit) {
        val l = fundus.clothing.toMutableList(); val i = l.indexOfFirst { it.id == o.id }
        if (i >= 0) l[i] = o else l.add(o)
        updateFundus(fundus.copy(clothing = l))
    }

    fun deleteOutfit(id: String) = updateFundus(fundus.copy(clothing = fundus.clothing.filter { it.id != id }))

    fun saveStyle(st: Style, makeDefault: Boolean) {
        val l = fundus.styles.toMutableList(); val i = l.indexOfFirst { it.id == st.id }
        if (i >= 0) l[i] = st else l.add(st)
        updateFundus(fundus.copy(styles = l, defaultStyleId = if (makeDefault || fundus.defaultStyleId.isBlank()) st.id else fundus.defaultStyleId))
    }

    fun deleteStyle(id: String) {
        val l = fundus.styles.filter { it.id != id }
        updateFundus(fundus.copy(styles = l, defaultStyleId = if (fundus.defaultStyleId == id) l.firstOrNull()?.id.orEmpty() else fundus.defaultStyleId))
    }

    fun paintOutfit(o: Outfit) {
        saveOutfit(o)
        paintJob(o.id) { prog ->
            val png = paint(ImagePrompt.outfit(o), extraNegative = ImagePrompt.SINGLE_NEGATIVE, onProgress = prog)
            val name = withContext(Dispatchers.IO) { repo.saveFundusImage("kleidung", png) }
            saveOutfit((outfit(o.id) ?: o).copy(preview = name))
        }
    }

    // ---------- Welt des geöffneten Buchs: Figuren und Orte ----------

    var worldBookId by mutableStateOf("")
        private set
    var world by mutableStateOf(World())
        private set

    fun openWorld(bookId: String) {
        if (worldBookId == bookId) return
        worldBookId = bookId
        world = repo.loadWorld(bookId)
    }

    private fun updateWorld(w: World) {
        world = w
        val id = worldBookId
        viewModelScope.launch(Dispatchers.IO) { repo.saveWorld(id, w) }
    }

    fun image(name: String) = repo.image(worldBookId, name)

    fun savePerson(p: Person) {
        val l = world.people.toMutableList(); val i = l.indexOfFirst { it.id == p.id }
        if (i >= 0) l[i] = p else l.add(p)
        updateWorld(world.copy(people = l))
    }

    fun deletePerson(id: String) = updateWorld(world.copy(people = world.people.filter { it.id != id }))

    fun savePlace(p: Place) {
        val l = world.places.toMutableList(); val i = l.indexOfFirst { it.id == p.id }
        if (i >= 0) l[i] = p else l.add(p)
        updateWorld(world.copy(places = l))
    }

    fun deletePlace(id: String) = updateWorld(world.copy(places = world.places.filter { it.id != id }))

    fun paintPerson(p: Person) {
        savePerson(p)
        val bookId = worldBookId
        paintJob(p.id) { prog ->
            val seed = p.seed.takeIf { it > 0 } ?: (1..999_999_999L).random()
            val png = paint(ImagePrompt.person(p, outfit(p.outfitId)), seed = seed, extraNegative = ImagePrompt.SINGLE_NEGATIVE, onProgress = prog)
            val name = withContext(Dispatchers.IO) { repo.saveImage(bookId, "figur", png) }
            if (worldBookId == bookId) savePerson((world.people.firstOrNull { it.id == p.id } ?: p).copy(preview = name))
        }
    }

    fun paintPlace(p: Place) {
        savePlace(p)
        val bookId = worldBookId
        paintJob(p.id) { prog ->
            val png = paint(ImagePrompt.place(p), onProgress = prog)
            val name = withContext(Dispatchers.IO) { repo.saveImage(bookId, "ort", png) }
            if (worldBookId == bookId) savePlace((world.places.firstOrNull { it.id == p.id } ?: p).copy(preview = name))
        }
    }

    // ---------- Gemma hilft ----------

    private fun cleanTags(answer: String, field: TagField) =
        TagLibrary.clean(answer.lines().joinToString(",") { it.trim().trimStart('-', '*', ' ') }.replace("\"", ""), field)

    /** Aus einer Beschreibung Bild-Tags machen (Aussehen, Ort oder Kleidung) */
    suspend fun tagsFor(description: String, field: TagField): String {
        val what = when (field) {
            TagField.APPEARANCE -> "das Aussehen einer Figur (Haare, Augen, Körper, Merkmale – keine Kleidung)"
            TagField.LOCATION -> "einen Ort (Hintergrund, keine Personen)"
            TagField.CLOTHING -> "ein Outfit (nur Kleidung und Accessoires)"
            else -> "ein Bild"
        }
        val a = ask(
            "Du schreibst Prompts für einen Anime-Bildgenerator im Danbooru-Tag-Stil. Du antwortest nur mit einer Komma-Liste englischer Tags.",
            "Beschreibung: ${description.trim()}\n\nSchreibe 6 bis 15 englische Danbooru-Tags für $what. Nur die Tags.", 0.4,
        )
        return cleanTags(a, field)
    }

    private fun jsonArray(answer: String): org.json.JSONArray? {
        val a = answer.indexOf('['); val b = answer.lastIndexOf(']')
        if (a < 0 || b <= a) return null
        return runCatching { org.json.JSONArray(answer.substring(a, b + 1)) }.getOrNull()
    }

    /** Gemma schlägt aus der Buch-Idee Figuren vor */
    suspend fun suggestPeople(book: Book): List<Person> {
        val known = world.people.joinToString { it.name }
        val a = ask(
            "Du bist Autor und entwirfst Figuren für Romane. Du antwortest ausschließlich mit einem JSON-Array, ohne Erklärung, ohne Markdown.",
            """
            Roman: "${book.title}" (${book.genre.ifBlank { "Genre frei" }})
            Idee: ${book.idea.ifBlank { "(noch keine – denk dir etwas Passendes aus)" }}
            ${if (known.isNotBlank()) "Schon vorhanden (nicht wiederholen): $known" else ""}

            Schlage 4 Figuren vor, die die Geschichte tragen. Für jede ein Objekt:
            {"name": "…", "emoji": "ein Emoji", "rolle": "Hauptfigur|Nebenfigur|Gegenspieler", "geschlecht": "männlich|weiblich|divers",
             "beschreibung": "3–4 Sätze auf Deutsch: Persönlichkeit, Hintergrund, Ziel, Sprechweise",
             "aussehen": "8–12 englische Danbooru-Tags zum Aussehen ohne Kleidung, z. B. long hair, red hair, green eyes"}
            Gib nur das JSON-Array zurück.
            """.trimIndent(), 0.9,
        )
        val arr = jsonArray(a) ?: throw IllegalStateException("Die KI hat keine Figuren geliefert. Einfach nochmal versuchen.")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Person(
                name = o.optString("name").ifBlank { "Figur ${i + 1}" },
                emoji = o.optString("emoji").ifBlank { "🙂" }.take(8),
                role = when { o.optString("rolle").startsWith("Haupt") -> Role.HAUPT; o.optString("rolle").startsWith("Gegen") -> Role.GEGEN; else -> Role.NEBEN },
                gender = when (o.optString("geschlecht").lowercase().take(1)) { "m" -> Gender.MALE; "w" -> Gender.FEMALE; "d" -> Gender.DIVERSE; else -> Gender.UNKNOWN },
                description = o.optString("beschreibung"),
                appearance = cleanTags(o.optString("aussehen"), TagField.APPEARANCE),
            )
        }
    }

    /** Gemma schlägt aus der Buch-Idee Orte vor */
    suspend fun suggestPlaces(book: Book): List<Place> {
        val known = world.places.joinToString { it.name }
        val a = ask(
            "Du bist Autor und entwirfst Schauplätze für Romane. Du antwortest ausschließlich mit einem JSON-Array, ohne Erklärung, ohne Markdown.",
            """
            Roman: "${book.title}" (${book.genre.ifBlank { "Genre frei" }})
            Idee: ${book.idea.ifBlank { "(noch keine)" }}
            ${if (known.isNotBlank()) "Schon vorhanden (nicht wiederholen): $known" else ""}

            Schlage 4 Schauplätze vor. Für jeden ein Objekt:
            {"name": "…", "beschreibung": "2–3 Sätze auf Deutsch: wie es dort aussieht, riecht, klingt", "tags": "6–10 englische Danbooru-Tags für den Hintergrund"}
            Gib nur das JSON-Array zurück.
            """.trimIndent(), 0.9,
        )
        val arr = jsonArray(a) ?: throw IllegalStateException("Die KI hat keine Orte geliefert. Einfach nochmal versuchen.")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Place(name = o.optString("name").ifBlank { "Ort ${i + 1}" }, description = o.optString("beschreibung"), tags = cleanTags(o.optString("tags"), TagField.LOCATION))
        }
    }

    // ---------- Import aus Character Fun ----------

    val canImportCf get() = CharacterFunImport.available

    fun cfPeople() = CharacterFunImport.people()
    fun cfPlaces() = CharacterFunImport.places()

    /** Figuren übernehmen (Bild wird in den Buchordner kopiert) */
    fun importPeople(list: List<CharacterFunImport.CfPerson>) {
        val bookId = worldBookId
        list.forEach { cf ->
            val pic = cf.image?.let { f -> runCatching { repo.saveImage(bookId, "figur", f.readBytes()) }.getOrNull() }.orEmpty()
            savePerson(cf.person.copy(preview = pic))
        }
        importFundusFromCf(quiet = true) // damit Standard-Outfits passen
    }

    fun importPlaces(list: List<Pair<Place, java.io.File?>>) {
        val bookId = worldBookId
        list.forEach { (pl, f) ->
            val pic = f?.let { runCatching { repo.saveImage(bookId, "ort", it.readBytes()) }.getOrNull() }.orEmpty()
            savePlace(pl.copy(preview = pic))
        }
    }

    /** Kleidung und Stile aus Character Fun in den Fundus übernehmen (Gleiches wird nicht doppelt angelegt) */
    fun importFundusFromCf(quiet: Boolean = false): Int {
        val (clothing, styles, def) = CharacterFunImport.clothingAndStyles()
        var count = 0
        var f = fundus
        clothing.forEach { (o, img) ->
            if (f.clothing.any { it.id == o.id || it.name == o.name }) return@forEach
            val pic = img?.let { runCatching { repo.saveFundusImage("kleidung", it.readBytes()) }.getOrNull() }.orEmpty()
            f = f.copy(clothing = f.clothing + o.copy(preview = pic)); count++
        }
        styles.forEach { st -> if (f.styles.none { it.lora == st.lora }) { f = f.copy(styles = f.styles + st); count++ } }
        if (!quiet && def.isNotBlank() && f.styles.any { it.id == def }) f = f.copy(defaultStyleId = def)
        updateFundus(f)
        return count
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
        fundus = repo.loadFundus()
        if (worldBookId.isNotBlank()) world = repo.loadWorld(worldBookId)
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
