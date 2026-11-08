package com.example.fitness.backup

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.*
import androidx.room.withTransaction
import com.example.fitness.data.*
import java.io.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class StagedBackup internal constructor(val token: String, val databaseName: String, internal val database: FitnessDatabase,
    internal val folder: File, internal var settings: JSONObject = JSONObject(), val counts: MutableMap<String, Long> = linkedMapOf()) {
    internal var handedOff = false
    fun discard(context: Context) { database.close(); context.deleteDatabase(databaseName); folder.deleteRecursively() }
}

class BackupManager(private val context: Context, private val database: FitnessDatabase, private val beforeCommit: (() -> Unit)? = null, private val afterCommit: (() -> Unit)? = null) {
    private val journal get() = AtomicFile(File(context.noBackupFilesDir, "restore-journal.json"))
    private val imageFolder get() = File(context.filesDir, "ai_images").apply { mkdirs() }
    private fun imageName(value: String): String { require(value.matches(Regex("[a-fA-F0-9-]+\\.jpg"))); return value }

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) { lock.withLock {
        require(!journal.baseFile.exists()) { "Finish pending restore recovery before exporting." }
        database.withTransaction {
            val sql = database.openHelper.readableDatabase
            val preferences = readSettings(context)
            JsonWriter(output.bufferedWriter(Charsets.UTF_8)).use { writer ->
                writer.beginObject().name("format").value("FitnessBackup").name("formatVersion").value(1)
                    .name("schemaVersion").value(sql.version).name("createdAt").value(Instant.now().toString()).name("tables").beginArray()
                backupTables.forEach { table ->
                    writer.beginObject().name("name").value(table).name("rows").beginArray()
                    sql.query("SELECT * FROM `$table` ORDER BY rowid").use { rows -> while (rows.moveToNext()) { currentCoroutineContext().ensureActive(); writeRow(writer, rows) } }
                    writer.endArray().endObject()
                }
                writer.endArray().name("settings").beginObject()
                preferences.keys().forEach { key -> writer.name(key); when (val value = preferences.get(key)) { is Boolean -> writer.value(value); is Number -> writer.value(value); else -> writer.value(value.toString()) } }
                writer.endObject().name("images").beginArray()
                val images = mutableSetOf<String>()
                sql.query("SELECT imagePath FROM QueuedAiRequest WHERE imagePath IS NOT NULL").use { rows -> while (rows.moveToNext()) images.add(imageName(rows.getString(0))) }
                images.forEach { name ->
                    val image = File(imageFolder, name)
                    require(image.isFile && image.length() <= 3_000_000) { "A queued image is unavailable. Discard that request before backing up." }
                    writer.beginObject().name("name").value(name).name("data").value(Base64.encodeToString(image.readBytes(), Base64.NO_WRAP)).endObject()
                }
                writer.endArray().endObject()
            }
        }
    } }

    suspend fun stage(input: InputStream): StagedBackup {
        var created: StagedBackup? = null
        try { return withContext(Dispatchers.IO) {
            val token = UUID.randomUUID().toString()
            val name = "restore-$token.db"
            val folder = File(context.noBackupFilesDir, "restore-$token").apply { check(mkdirs()) }
            val stage = StagedBackup(token, name, FitnessDatabase.create(context, name), folder).also { created = it }
            stage.database.withTransaction {
                val sql = stage.database.openHelper.writableDatabase
                val seen = mutableSetOf<String>()
                val images = mutableSetOf<String>()
                JsonReader(GuardedJsonReader(input.bufferedReader(Charsets.UTF_8))).use { reader ->
                    reader.isLenient = false; reader.beginObject()
                    while (reader.hasNext()) {
                        currentCoroutineContext().ensureActive()
                        val key = reader.nextName(); require(seen.add(key)) { "Duplicate backup section." }
                        when (key) {
                            "format" -> require(reader.nextString() == "FitnessBackup") { "Not a Fitness backup." }
                            "formatVersion" -> require(reader.nextInt() == 1) { "Unsupported backup format." }
                            "schemaVersion" -> require(reader.nextInt() == sql.version) { "Backup schema does not match this app version." }
                            "createdAt" -> Instant.parse(reader.nextString())
                            "settings" -> stage.settings = readPreferences(reader)
                            "tables" -> {
                                reader.beginArray(); var index = 0
                                while (reader.hasNext()) {
                                    reader.beginObject(); require(reader.nextName() == "name")
                                    val table = reader.nextString(); require(backupTables.getOrNull(index++) == table) { "Invalid backup table order." }
                                    require(reader.nextName() == "rows"); reader.beginArray()
                                    val metadata = columns(sql, table); var count = 0L
                                    while (reader.hasNext()) {
                                        currentCoroutineContext().ensureActive()
                                        sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, readRow(reader, metadata)); count++
                                    }
                                    reader.endArray(); require(!reader.hasNext()); reader.endObject(); stage.counts[table] = count
                                }
                                reader.endArray(); require(index == backupTables.size) { "Backup is missing tables." }
                            }
                            "images" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    reader.beginObject(); require(reader.nextName() == "name"); val image = imageName(reader.nextString()); require(images.add(image))
                                    require(reader.nextName() == "data"); val encoded = reader.nextString(); require(encoded.length <= 4_000_000)
                                    val bytes = Base64.decode(encoded, Base64.DEFAULT); require(bytes.size <= 3_000_000)
                                    File(folder, image).outputStream().use { it.write(bytes); it.fd.sync() }
                                    require(!reader.hasNext()); reader.endObject()
                                }
                                reader.endArray()
                            }
                            else -> error("Unknown backup section.")
                        }
                    }
                    reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
                }
                require(seen == setOf("format", "formatVersion", "schemaVersion", "createdAt", "tables", "settings", "images")) { "Incomplete backup." }
                checkData(sql)
                sql.query("SELECT imagePath FROM QueuedAiRequest WHERE imagePath IS NOT NULL").use { rows -> while (rows.moveToNext()) require(imageName(rows.getString(0)) in images) { "Queued image missing from backup." } }
                require(images.all { name -> sql.query("SELECT id FROM QueuedAiRequest WHERE imagePath = ?", arrayOf(name)).use { it.moveToFirst() } }) { "Unreferenced backup image." }
            }
            currentCoroutineContext().ensureActive()
            stage
        } } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { created?.discard(context) }
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun recordingActive(): Boolean = context.getSystemService(ActivityManager::class.java).getRunningServices(100).any {
        it.service.packageName == context.packageName && it.service.className in setOf("com.example.fitness.activity.StepService", "com.example.fitness.activity.RouteService")
    }

    suspend fun restore(stage: StagedBackup) = withContext(NonCancellable + Dispatchers.IO) { lock.withLock {
        try {
            require(!recordingActive()) { "Stop tracking and wait for it to finish before restoring." }
            require(!journal.baseFile.exists()) { "Finish the previous restore first." }
            val checkpoint = JSONObject().put("token", stage.token).put("oldSettings", readSettings(context)).put("newSettings", stage.settings)
            val stream = journal.startWrite()
            try { stream.write(checkpoint.toString().toByteArray(Charsets.UTF_8)); journal.finishWrite(stream) }
            catch (failure: Throwable) { journal.failWrite(stream); throw failure }
            stage.handedOff = true
            database.withTransaction {
                val target = database.openHelper.writableDatabase
                backupTables.asReversed().forEach { target.execSQL("DELETE FROM `$it`") }
                backupTables.forEach { table ->
                    stage.database.openHelper.readableDatabase.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                        while (cursor.moveToNext()) {
                            val values = android.content.ContentValues()
                            cursor.columnNames.forEachIndexed { index, name -> when (cursor.getType(index)) {
                                android.database.Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                                android.database.Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(index))
                                android.database.Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(index))
                                else -> values.put(name, cursor.getString(index))
                            } }
                            target.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
                        }
                    }
                }
                beforeCommit?.invoke()
                checkData(target)
                target.execSQL("INSERT OR REPLACE INTO RestoreCheckpoint (id, token, createdAt) VALUES (1, ?, ?)", arrayOf<Any>(stage.token, System.currentTimeMillis()))
            }
            afterCommit?.invoke()
            recoverLocked()
        } finally {
            stage.database.close(); context.deleteDatabase(stage.databaseName)
            if (!journal.baseFile.exists()) stage.folder.deleteRecursively()
        }
    } }

    suspend fun recover() = withContext(NonCancellable + Dispatchers.IO) { lock.withLock { recoverLocked() } }
    private suspend fun recoverLocked() {
        if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return
        val record = journal.openRead().bufferedReader().use { JSONObject(it.readText()) }
        val token = record.getString("token"); require(token.matches(Regex("[a-fA-F0-9-]{36}")))
        val committed = database.openHelper.readableDatabase.query("SELECT token FROM RestoreCheckpoint WHERE id = 1").use { it.moveToFirst() && it.getString(0) == token }
        val folder = File(context.noBackupFilesDir, "restore-$token")
        if (committed) {
            val referenced = mutableSetOf<String>()
            database.openHelper.readableDatabase.query("SELECT imagePath FROM QueuedAiRequest WHERE imagePath IS NOT NULL").use { c -> while (c.moveToNext()) referenced.add(imageName(c.getString(0))) }
            referenced.forEach { name ->
                val source = File(folder, name)
                if (source.isFile) {
                    val target = File(imageFolder, name); val temp = File(imageFolder, "$name.tmp")
                    source.inputStream().use { input -> temp.outputStream().use { input.copyTo(it); it.fd.sync() } }
                    check(temp.renameTo(target)) { "Could not restore a queued image." }
                } else require(File(imageFolder, name).isFile) { "Queued image recovery is incomplete." }
            }
            writeSettings(context, record.getJSONObject("newSettings"))
            imageFolder.listFiles()?.filter { it.name !in referenced }?.forEach { it.delete() }
        } else writeSettings(context, record.getJSONObject("oldSettings"))
        journal.delete(); folder.deleteRecursively(); context.deleteDatabase("restore-$token.db")
    }
    companion object { private val lock = Mutex() }
}

/** Bounds individual strings/nesting without loading a complete GPS backup in RAM. */
internal class GuardedJsonReader(private val input: Reader) : Reader() {
    private var quoted = false; private var escaped = false; private var length = 0; private var depth = 0
    override fun read(buffer: CharArray, offset: Int, count: Int): Int {
        val read = input.read(buffer, offset, count)
        for (index in offset until offset + read.coerceAtLeast(0)) {
            val c = buffer[index]
            if (quoted) {
                length++; require(length <= 4_100_000) { "Backup string is too large." }
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else if (c == '"') { quoted = true; length = 0 }
            else if (c == '{' || c == '[') { depth++; require(depth <= 32) { "Backup nesting is too deep." } }
            else if (c == '}' || c == ']') { depth--; require(depth >= 0) }
        }
        return read
    }
    override fun close() = input.close()
}
