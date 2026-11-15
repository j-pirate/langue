package org.langue.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.langue.engine.Entry
import org.langue.engine.Category
import org.langue.engine.LanguagePack
import org.langue.engine.Manifest
import org.langue.engine.PackArchive
import org.langue.engine.PackException
import org.langue.engine.PackJson

private val Context.languagePreferences by preferencesDataStore("language_preferences")

data class QuizStats(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
)

/** Local-only repository. Imported files are immutable generations; Room selects the active one. */
class AppRepository(
    context: Context,
    databaseName: String = "langue.db",
    private val storageDirectory: File = File(context.filesDir, "language-packs"),
    private val preferences: DataStore<Preferences> = context.applicationContext.languagePreferences,
) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, databaseName).build()
    private val dao = database.packs()
    private val mutations = Mutex()
    private var initialized = false

    val packs: Flow<List<LanguagePack>> = dao.observePacks().map { rows ->
        rows.map { decode(it) }.sortedBy { it.manifest.name }
    }.flowOn(Dispatchers.IO)

    val activePack: Flow<LanguagePack?> = combine(packs, preferences.data) { installed, prefs ->
        installed.firstOrNull { it.manifest.id == prefs[ACTIVE_PACK] } ?: installed.firstOrNull()
    }

    val progress: Flow<QuizStats> = combine(activePack, dao.observeQuiz()) { pack, rows ->
        rows.firstOrNull { it.packId == pack?.manifest?.id }?.stats() ?: QuizStats()
    }

    val viewedEntryIds: Flow<Set<String>> = combine(activePack, dao.observeViewed()) { pack, rows ->
        rows.asSequence().filter { it.packId == pack?.manifest?.id }.map { it.entryId }.toSet()
    }

    /** Import bundled packs once, including across explicit deletion by the user. */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutations.withLock {
            if (initialized) return@withLock
            check(storageDirectory.isDirectory || storageDirectory.mkdirs()) { "Cannot create pack storage" }
            removeUnreferencedGenerations()
            val names = appContext.assets.list("packs")?.filter { it.endsWith(".zip", true) }.orEmpty().sorted()
            for (name in names) {
                // Content identity permits a newer APK to upgrade its embedded pack, while a deleted
                // unchanged bundle stays deleted. Filenames alone cannot distinguish APK updates.
                val fingerprint = appContext.assets.open("packs/$name").use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(16 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) digest.update(buffer, 0, bytes)
                    digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                }
                val marker = "$name:$fingerprint"
                if (marker in preferences.data.first()[BUNDLED_INSTALLED].orEmpty()) continue
                appContext.assets.open("packs/$name").use { input ->
                    importLocked(input, allowExistingNewer = true)
                }
                preferences.edit { prefs ->
                    prefs[BUNDLED_INSTALLED] = prefs[BUNDLED_INSTALLED].orEmpty() + marker
                }
            }
            initialized = true
        }
    }

    suspend fun importPack(input: InputStream): LanguagePack = withContext(Dispatchers.IO) {
        mutations.withLock { importLocked(input) }
    }

    private suspend fun importLocked(input: InputStream, allowExistingNewer: Boolean = false): LanguagePack {
        check(storageDirectory.isDirectory || storageDirectory.mkdirs()) { "Cannot create pack storage" }
        val destination = File(storageDirectory, UUID.randomUUID().toString())
        var committed = false
        try {
            val pack = PackArchive.extract(input, destination)
            val old = dao.pack(pack.manifest.id)
            if (old != null) {
                val previous = decode(old)
                if (!previous.manifest.sourceLanguage.tag.equals(pack.manifest.sourceLanguage.tag, true) ||
                    !previous.manifest.targetLanguage.tag.equals(pack.manifest.targetLanguage.tag, true)) {
                    throw PackException("A pack update must preserve its source and target languages")
                }
                if (pack.manifest.version <= previous.manifest.version) {
                    if (allowExistingNewer) return previous
                    throw PackException("A pack update must have a greater version number")
                }
            }
            database.withTransaction {
                dao.savePack(PackEntity(pack.manifest.id, PackJson.json.encodeToString(pack.manifest), destination.name))
                dao.deleteCategories(pack.manifest.id)
                dao.deleteEntries(pack.manifest.id)
                dao.saveCategories(pack.categories.mapIndexed { position, category ->
                    CategoryEntity(pack.manifest.id, category.id, position, PackJson.json.encodeToString(category))
                })
                dao.saveEntries(pack.entries.mapIndexed { position, entry ->
                    EntryEntity(pack.manifest.id, entry.id, position, PackJson.json.encodeToString(entry))
                })
                // Stable entry IDs retain viewed state. Removed IDs must not reappear after a later update.
                val entryIds = pack.entries.map { it.id }.toSet()
                val retained = dao.viewed(pack.manifest.id).filter { row -> row.entryId in entryIds }
                dao.deleteViewed(pack.manifest.id)
                dao.saveViewed(retained)
            }
            committed = true
            installedDirectories[pack.manifest.id] = destination.name
            // A crash here leaves an orphan generation, removed by initialize() on the next launch.
            if (old != null) File(storageDirectory, old.directory).deleteRecursively()
            preferences.edit { it[ACTIVE_PACK] = pack.manifest.id }
            return pack
        } finally {
            if (!committed) destination.deleteRecursively()
        }
    }

    suspend fun selectPack(id: String) = withContext(Dispatchers.IO) {
        mutations.withLock {
            requireNotNull(dao.metadata(id)) { "Pack is not installed" }
            preferences.edit { it[ACTIVE_PACK] = id }
        }
    }

    suspend fun deletePack(id: String) = withContext(Dispatchers.IO) {
        mutations.withLock {
            val pack = dao.metadata(id) ?: return@withLock
            database.withTransaction {
                dao.deleteViewed(id)
                dao.deleteQuiz(id)
                dao.deleteCategories(id)
                dao.deleteEntries(id)
                dao.deletePack(id)
            }
            installedDirectories.remove(id)
            if (preferences.data.first()[ACTIVE_PACK] == id) preferences.edit { it.remove(ACTIVE_PACK) }
            File(storageDirectory, pack.directory).deleteRecursively()
        }
    }

    suspend fun markViewed(entryId: String, packId: String? = null) = withContext(Dispatchers.IO) {
        mutations.withLock {
            val id = packId ?: activeId() ?: return@withLock
            require(dao.entryExists(id, entryId)) { "Entry is not installed" }
            dao.saveViewed(listOf(ViewedEntity(id, entryId)))
        }
    }

    suspend fun recordAnswer(correct: Boolean, packId: String? = null) = withContext(Dispatchers.IO) {
        mutations.withLock {
            val id = packId ?: activeId() ?: return@withLock
            requireNotNull(dao.metadata(id)) { "Pack is not installed" }
            database.withTransaction {
                val before = dao.quiz(id) ?: QuizEntity(id)
                require(before.answered < Int.MAX_VALUE) { "Statistics limit reached; export and reset progression" }
                val current = if (correct) before.currentStreak + 1 else 0
                dao.saveQuiz(before.copy(
                    currentStreak = current,
                    bestStreak = maxOf(before.bestStreak, current),
                    answered = before.answered + 1,
                    correct = before.correct + if (correct) 1 else 0,
                ))
            }
        }
    }

    /** Reset the currently selected pack; installed content is preserved. */
    suspend fun resetProgress() = withContext(Dispatchers.IO) {
        mutations.withLock {
            val id = activeId() ?: return@withLock
            database.withTransaction {
                dao.deleteQuiz(id)
                dao.deleteViewed(id)
            }
        }
    }

    /** Export all installed packs' progression, without copying educational content or audio. */
    suspend fun exportProgress(): String = withContext(Dispatchers.IO) {
        mutations.withLock {
            val snapshot = database.withTransaction {
                ProgressBackup(packs = dao.metadata().map { row ->
                    val quiz = dao.quiz(row.id) ?: QuizEntity(row.id)
                    PackProgress(row.id, quiz.currentStreak, quiz.bestStreak, quiz.answered, quiz.correct,
                        dao.viewed(row.id).map { it.entryId }.sorted())
                })
            }
            snapshot.validate()
            PackJson.json.encodeToString(snapshot).also {
                require(it.length <= MAX_BACKUP_CHARS) { "Progress backup exceeds the supported restore size" }
            }
        }
    }

    /** Strict, atomic restore. All referenced packs must be installed; removed entry IDs are ignored. */
    suspend fun restoreProgress(json: String) = withContext(Dispatchers.IO) {
        require(json.length <= MAX_BACKUP_CHARS) { "Progress backup is too large" }
        val backup = PackJson.json.decodeFromString<ProgressBackup>(json)
        backup.validate()
        mutations.withLock {
            val installed = dao.metadata().map { it.id }.toSet()
            require(backup.packs.all { it.packId in installed }) { "Install all packs referenced by the backup before restoring" }
            database.withTransaction {
                backup.packs.forEach { progress ->
                    val entries = dao.entryIds(progress.packId).toSet()
                    dao.saveQuiz(QuizEntity(progress.packId, progress.currentStreak, progress.bestStreak,
                        progress.answered, progress.correct))
                    dao.deleteViewed(progress.packId)
                    dao.saveViewed(progress.viewedEntryIds.filter { it in entries }.map { ViewedEntity(progress.packId, it) })
                }
            }
        }
    }

    /** Resolves only files inside the current immutable generation, never arbitrary paths. */
    suspend fun audioFileForEntry(packId: String, entry: Entry): File? = withContext(Dispatchers.IO) {
        val row = dao.metadata(packId) ?: return@withContext null
        resolveAudio(row, entry)
    }

    // Keep the synchronous player API free of database calls on the main thread.
    private val installedDirectories = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun audioFile(packId: String, entry: Entry): File? {
        val directory = installedDirectories[packId] ?: return null
        return resolveAudio(PackEntity(packId, "", directory), entry)
    }

    private fun resolveAudio(row: PackEntity, entry: Entry): File? {
        val relative = entry.audio ?: return null
        val base = File(storageDirectory, row.directory).canonicalFile
        val audio = File(base, relative).canonicalFile
        return audio.takeIf { it.toPath().startsWith(base.toPath()) && it.isFile }
    }

    private fun decode(row: StoredPack): LanguagePack {
        installedDirectories[row.id] = row.directory
        return LanguagePack(
            manifest = PackJson.json.decodeFromString<Manifest>(row.json),
            categories = row.categories.sortedBy { it.position }.map { PackJson.json.decodeFromString<Category>(it.json) },
            entries = row.entries.sortedBy { it.position }.map { PackJson.json.decodeFromString<Entry>(it.json) },
        )
    }

    private suspend fun activeId(): String? {
        val available = dao.metadata()
        val selected = preferences.data.first()[ACTIVE_PACK]
        return available.firstOrNull { it.id == selected }?.id
            ?: available.minByOrNull { PackJson.json.decodeFromString<Manifest>(it.json).name }?.id
    }

    private suspend fun removeUnreferencedGenerations() {
        val known = dao.metadata().also { rows -> rows.forEach { installedDirectories[it.id] = it.directory } }
            .map { it.directory }.toSet()
        storageDirectory.listFiles()?.filter { it.name !in known }?.forEach { it.deleteRecursively() }
    }

    /** For instrumentation tests and orderly ownership; application repositories live for the process. */
    fun close() = database.close()

    companion object {
        private val ACTIVE_PACK = stringPreferencesKey("active_pack")
        private val BUNDLED_INSTALLED = stringSetPreferencesKey("bundled_installed")
        const val MAX_BACKUP_CHARS = 8 * 1024 * 1024
    }
}

private fun QuizEntity.stats() = QuizStats(currentStreak, bestStreak, answered, correct)

@Serializable
internal data class ProgressBackup(val schemaVersion: Int = 1, val packs: List<PackProgress>) {
    fun validate() {
        require(schemaVersion == 1) { "Unsupported progress schema" }
        require(packs.size <= 1000 && packs.map { it.packId }.toSet().size == packs.size) { "Duplicate or excessive pack records" }
        packs.forEach { it.validate() }
    }
}

@Serializable
internal data class PackProgress(
    val packId: String,
    val currentStreak: Int,
    val bestStreak: Int,
    val answered: Int,
    val correct: Int,
    val viewedEntryIds: List<String> = emptyList(),
) {
    fun validate() {
        require(packId.isNotBlank() && packId.length <= 200) { "Invalid pack ID" }
        require(answered >= 0 && correct in 0..answered && currentStreak in 0..correct &&
            bestStreak in currentStreak..correct) { "Inconsistent quiz statistics" }
        require(viewedEntryIds.size <= 100_000 && viewedEntryIds.toSet().size == viewedEntryIds.size &&
            viewedEntryIds.all { it.isNotBlank() && it.length <= 200 }) { "Invalid viewed entries" }
    }
}
