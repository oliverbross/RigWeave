package app.rigweave.mobile.groupsio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val GROUPS_IO_DATABASE_NAME = "rigweave-groupsio.sqlite"

data class GroupsIoGroup(
    val id: Long,
    val name: String,
    val title: String,
    val summary: String,
    val status: String,
    val archivesVisible: Boolean,
    val active: Boolean,
    val lastSyncMillis: Long,
)

data class GroupsIoTopic(
    val id: Long,
    val groupId: Long,
    val subject: String,
    val updatedMillis: Long,
    val messageCount: Int,
    val closed: Boolean,
    val firstMessageNumber: Long?,
    val latestMessageNumber: Long?,
)

data class GroupsIoMessage(
    val apiId: Long?,
    val groupId: Long,
    val topicId: Long,
    val number: Long,
    val replyToNumber: Long?,
    val subject: String,
    val author: String,
    val createdMillis: Long,
    val body: String,
    val moderated: Boolean,
    val deleted: Boolean,
    val hasAttachments: Boolean,
)

data class GroupsIoSearchResult(
    val groupId: Long,
    val topicId: Long,
    val messageNumber: Long,
    val groupName: String,
    val topicSubject: String,
    val author: String,
    val createdMillis: Long,
    val snippet: String,
)

internal data class GroupsIoPage<T>(val values: List<T>, val nextPageToken: String?, val hasMore: Boolean)
internal fun groupsIoDestinationVisible(enabled: Boolean, compact: Boolean): Boolean = enabled && !compact
internal fun groupsIoPagination(root: JSONObject): Pair<String?, Boolean> {
    val hasMore = root.optBoolean("has_more", false)
    val next = root.opt("next_page_token")?.toString()?.takeUnless { it == "null" || it == "0" || it.isBlank() }
    if (hasMore && next == null) throw GroupsIoApiException("compatibility", "Groups.io pagination omitted the next page token")
    return next to hasMore
}

internal class GroupsIoDatabase(private val appContext: Context, private val databaseName: String = GROUPS_IO_DATABASE_NAME) :
    SQLiteOpenHelper(appContext, databaseName, null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE groups(
            group_id INTEGER PRIMARY KEY, name TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL DEFAULT '',
            parent_group_id INTEGER, membership_status TEXT NOT NULL DEFAULT '', archive_visibility TEXT NOT NULL DEFAULT '',
            can_read INTEGER NOT NULL DEFAULT 0, can_post INTEGER NOT NULL DEFAULT 0, last_activity INTEGER,
            active INTEGER NOT NULL DEFAULT 1, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL,
            last_successful_sync INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE topics(
            topic_id INTEGER PRIMARY KEY, group_id INTEGER NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
            subject TEXT NOT NULL, created INTEGER, updated INTEGER NOT NULL, message_count INTEGER NOT NULL DEFAULT 0,
            closed INTEGER NOT NULL DEFAULT 0, followed INTEGER, muted INTEGER, first_message_number INTEGER,
            latest_message_number INTEGER, last_successful_sync INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE messages(
            row_id INTEGER PRIMARY KEY AUTOINCREMENT, api_message_id INTEGER, group_id INTEGER NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
            topic_id INTEGER NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE, message_number INTEGER NOT NULL,
            reply_to_number INTEGER, subject TEXT NOT NULL, author_name TEXT NOT NULL, created INTEGER NOT NULL,
            updated INTEGER, body_plain TEXT NOT NULL, display_body TEXT NOT NULL, moderated INTEGER NOT NULL DEFAULT 0,
            deleted INTEGER NOT NULL DEFAULT 0, has_attachments INTEGER NOT NULL DEFAULT 0, last_successful_sync INTEGER NOT NULL,
            UNIQUE(group_id, message_number))""")
        db.execSQL("""CREATE TABLE sync_state(
            scope TEXT NOT NULL, scope_id TEXT NOT NULL DEFAULT '', last_attempt INTEGER, last_success INTEGER,
            current_cursor TEXT, completed_cursor TEXT, last_error_category TEXT, last_error_text TEXT,
            has_more INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(scope, scope_id))""")
        db.execSQL("CREATE INDEX topics_group_latest ON topics(group_id, updated DESC)")
        db.execSQL("CREATE INDEX messages_topic_number ON messages(topic_id, message_number)")
        db.execSQL("CREATE INDEX messages_group_created ON messages(group_id, created DESC)")
        db.execSQL("CREATE INDEX sync_state_scope ON sync_state(scope, scope_id)")
        db.execSQL("CREATE VIRTUAL TABLE message_search USING fts5(group_name, topic_subject, message_subject, author_name, body_plain)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun applyMemberships(values: List<GroupsIoGroup>, completed: Boolean, syncedAt: Long) = writableDatabase.transaction {
        values.forEach { group ->
            val row = ContentValues().apply {
                put("group_id", group.id); put("name", group.name); put("title", group.title); put("summary", group.summary)
                put("membership_status", group.status); put("archive_visibility", if (group.archivesVisible) "visible" else "restricted")
                put("can_read", group.archivesVisible); put("active", true); put("last_seen", syncedAt); put("last_successful_sync", syncedAt)
                put("first_seen", syncedAt)
            }
            insertWithOnConflict("groups", null, row, SQLiteDatabase.CONFLICT_IGNORE)
            row.remove("first_seen")
            update("groups", row, "group_id=?", arrayOf(group.id.toString()))
            refreshSearchForGroup(this, group.id)
        }
        if (completed) {
            val ids = values.map { it.id }.toSet()
            rawQuery("SELECT group_id FROM groups WHERE active=1", null).use { cursor ->
                while (cursor.moveToNext()) if (cursor.getLong(0) !in ids) {
                    execSQL("UPDATE groups SET active=0 WHERE group_id=?", arrayOf(cursor.getLong(0)))
                }
            }
        }
        recordSuccess(this, "memberships", "", syncedAt, false, null)
    }

    fun applyTopics(groupId: Long, values: List<GroupsIoTopic>, next: String?, hasMore: Boolean, syncedAt: Long) = writableDatabase.transaction {
        values.forEach { topic ->
            val row = ContentValues().apply {
                put("topic_id", topic.id); put("group_id", groupId); put("subject", topic.subject)
                put("updated", topic.updatedMillis); put("message_count", topic.messageCount); put("closed", topic.closed)
                topic.firstMessageNumber?.let { put("first_message_number", it) }
                topic.latestMessageNumber?.let { put("latest_message_number", it) }
                put("last_successful_sync", syncedAt)
            }
            insertWithOnConflict("topics", null, row, SQLiteDatabase.CONFLICT_REPLACE)
            refreshSearchForTopic(this, topic.id)
        }
        recordSuccess(this, "topics", groupId.toString(), syncedAt, hasMore, next)
    }

    fun applyMessages(groupId: Long, topicId: Long, values: List<GroupsIoMessage>, next: String?, hasMore: Boolean, syncedAt: Long) = writableDatabase.transaction {
        values.forEach { message ->
            val existing = rawQuery("SELECT row_id FROM messages WHERE group_id=? AND message_number=?", arrayOf(groupId.toString(), message.number.toString()))
                .use { if (it.moveToFirst()) it.getLong(0) else null }
            val row = ContentValues().apply {
                message.apiId?.let { put("api_message_id", it) }; put("group_id", groupId); put("topic_id", topicId)
                put("message_number", message.number); message.replyToNumber?.let { put("reply_to_number", it) }
                put("subject", message.subject); put("author_name", message.author); put("created", message.createdMillis)
                put("body_plain", message.body); put("display_body", message.body); put("moderated", message.moderated)
                put("deleted", message.deleted); put("has_attachments", message.hasAttachments); put("last_successful_sync", syncedAt)
            }
            val rowId = if (existing == null) insertOrThrow("messages", null, row) else { update("messages", row, "row_id=?", arrayOf(existing.toString())); existing }
            refreshSearchRow(this, rowId)
        }
        recordSuccess(this, "messages", topicId.toString(), syncedAt, hasMore, next)
    }

    fun groups(limit: Int = 100): List<GroupsIoGroup> = readableDatabase.rawQuery(
        "SELECT group_id,name,title,summary,membership_status,can_read,active,last_successful_sync FROM groups ORDER BY active DESC,title COLLATE NOCASE LIMIT ?",
        arrayOf(limit.coerceIn(1, 100).toString())
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(GroupsIoGroup(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5) != 0, cursor.getInt(6) != 0, cursor.getLong(7))) } }

    fun topics(groupId: Long, limit: Int = 50, offset: Int = 0): List<GroupsIoTopic> = readableDatabase.rawQuery(
        "SELECT topic_id,group_id,subject,updated,message_count,closed,first_message_number,latest_message_number FROM topics WHERE group_id=? ORDER BY updated DESC LIMIT ? OFFSET ?",
        arrayOf(groupId.toString(), limit.coerceIn(1, 100).toString(), offset.coerceAtLeast(0).toString())
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(GroupsIoTopic(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getLong(3), cursor.getInt(4), cursor.getInt(5) != 0, cursor.longOrNull(6), cursor.longOrNull(7))) } }

    fun messages(topicId: Long, limit: Int = 100, offset: Int = 0): List<GroupsIoMessage> = readableDatabase.rawQuery(
        "SELECT api_message_id,group_id,topic_id,message_number,reply_to_number,subject,author_name,created,body_plain,moderated,deleted,has_attachments FROM messages WHERE topic_id=? ORDER BY message_number LIMIT ? OFFSET ?",
        arrayOf(topicId.toString(), limit.coerceIn(1, 100).toString(), offset.coerceAtLeast(0).toString())
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(GroupsIoMessage(cursor.longOrNull(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3), cursor.longOrNull(4), cursor.getString(5), cursor.getString(6), cursor.getLong(7), cursor.getString(8), cursor.getInt(9) != 0, cursor.getInt(10) != 0, cursor.getInt(11) != 0)) } }

    fun search(query: String, groupId: Long? = null, topicId: Long? = null, limit: Int = 40): List<GroupsIoSearchResult> {
        val match = query.trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
        if (match.isBlank()) return emptyList()
        val where = buildString { append("message_search MATCH ?"); if (groupId != null) append(" AND m.group_id=?"); if (topicId != null) append(" AND m.topic_id=?") }
        val args = mutableListOf(match); groupId?.let { args += it.toString() }; topicId?.let { args += it.toString() }; args += limit.coerceIn(1, 100).toString()
        return readableDatabase.rawQuery("""SELECT m.group_id,m.topic_id,m.message_number,g.title,t.subject,m.author_name,m.created,
            snippet(message_search,4,'[',']',' … ',18) FROM message_search JOIN messages m ON m.row_id=message_search.rowid
            JOIN groups g ON g.group_id=m.group_id JOIN topics t ON t.topic_id=m.topic_id WHERE $where ORDER BY bm25(message_search) LIMIT ?""", args.toTypedArray())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(GroupsIoSearchResult(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getLong(6), cursor.getString(7))) } }
    }

    fun sizeBytes(): Long = listOf("", "-wal", "-shm").sumOf { suffix -> appContext.getDatabasePath(databaseName + suffix).takeIf { it.isFile }?.length() ?: 0L }

    fun deleteDownloadedData() {
        close()
        appContext.deleteDatabase(databaseName)
    }

    fun recordFailure(scope: String, scopeId: String, category: String, text: String) = writableDatabase.transaction {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply { put("scope", scope); put("scope_id", scopeId); put("last_attempt", now); put("last_error_category", category); put("last_error_text", text.take(160)) }
        insertWithOnConflict("sync_state", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        update("sync_state", values, "scope=? AND scope_id=?", arrayOf(scope, scopeId))
    }

    private fun refreshSearchForGroup(db: SQLiteDatabase, groupId: Long) {
        db.rawQuery("SELECT row_id FROM messages WHERE group_id=?", arrayOf(groupId.toString())).use { while (it.moveToNext()) refreshSearchRow(db, it.getLong(0)) }
    }

    private fun refreshSearchForTopic(db: SQLiteDatabase, topicId: Long) {
        db.rawQuery("SELECT row_id FROM messages WHERE topic_id=?", arrayOf(topicId.toString())).use { while (it.moveToNext()) refreshSearchRow(db, it.getLong(0)) }
    }

    private fun refreshSearchRow(db: SQLiteDatabase, rowId: Long) {
        db.execSQL("DELETE FROM message_search WHERE rowid=?", arrayOf(rowId))
        db.execSQL("""INSERT INTO message_search(rowid,group_name,topic_subject,message_subject,author_name,body_plain)
            SELECT m.row_id,g.title,t.subject,m.subject,m.author_name,m.body_plain FROM messages m
            JOIN groups g ON g.group_id=m.group_id JOIN topics t ON t.topic_id=m.topic_id WHERE m.row_id=?""", arrayOf(rowId))
    }

    private fun recordSuccess(db: SQLiteDatabase, scope: String, scopeId: String, at: Long, hasMore: Boolean, cursor: String?) {
        val values = ContentValues().apply { put("scope", scope); put("scope_id", scopeId); put("last_attempt", at); put("last_success", at); put("current_cursor", cursor); put("has_more", hasMore); putNull("last_error_category"); putNull("last_error_text") }
        db.insertWithOnConflict("sync_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try { val value = block(); setTransactionSuccessful(); value } finally { endTransaction() }
}

private fun android.database.Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

internal class GroupsIoCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("rigweave-groupsio-credentials", Context.MODE_PRIVATE)
    fun exists(): Boolean = prefs.contains("api_key")
    fun load(): String = decrypt(prefs.getString("api_key", "").orEmpty())
    fun save(value: String) { prefs.edit().putString("api_key", encrypt(value)).apply() }
    fun clear() { prefs.edit().remove("api_key").apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = runCatching {
        if (value.isEmpty()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrDefault("")

    private companion object { const val ALIAS = "app.rigweave.mobile.groupsio.api-key" }
}

internal class GroupsIoLiveApi {
    fun groups(key: String, pageToken: String? = null): GroupsIoPage<GroupsIoGroup> = page("groups", key, pageToken) { value ->
        val id = value.requiredLong("id", "group_id")
        val name = value.requiredString("name", "group_name")
        val perms = value.optJSONObject("perms") ?: value.optJSONObject("permissions")
        GroupsIoGroup(id, name, value.firstString("title", "display_name").ifBlank { name }, value.firstString("description", "desc", "summary"),
            value.firstString("status", "subscription_status"), perms?.optBoolean("archives_visible", value.optBoolean("archives_visible", false)) ?: value.optBoolean("archives_visible", false), true, System.currentTimeMillis())
    }

    fun topics(key: String, groupId: Long, pageToken: String? = null): GroupsIoPage<GroupsIoTopic> = page("gettopics", key, pageToken, mapOf("group_id" to groupId.toString(), "sort_dir" to "desc")) { value ->
        GroupsIoTopic(value.requiredLong("id", "topic_id"), groupId, value.requiredString("subject", "title"), value.instantMillis("updated", "last_message_time", "created"),
            value.firstInt("message_count", "num_messages", "message_cnt"), value.optBoolean("closed", value.optBoolean("locked", false)), value.firstLongOrNull("first_msg_num", "first_message_number"), value.firstLongOrNull("last_msg_num", "latest_message_number"))
    }

    fun messages(key: String, groupId: Long, topicId: Long, pageToken: String? = null): GroupsIoPage<GroupsIoMessage> = page("gettopic", key, pageToken, mapOf("topic_id" to topicId.toString(), "sort_dir" to "asc")) { value ->
        val number = value.requiredLong("msg_num", "message_number", "num")
        val rawBody = value.firstString("body", "html_body", "text", "snippet")
        GroupsIoMessage(value.firstLongOrNull("id", "message_id"), groupId, topicId, number, value.firstLongOrNull("reply_to", "reply_to_msg_num"),
            value.firstString("subject"), value.firstString("name", "author_name", "sender_name", "from_name").ifBlank { "Unknown author" },
            value.instantMillis("created", "date"), normaliseBody(rawBody), value.optBoolean("moderated", false), value.optBoolean("deleted", false),
            value.optBoolean("has_attachments", false) || value.optJSONArray("attachments")?.length()?.let { it > 0 } == true)
    }

    private fun <T> page(path: String, key: String, token: String?, extra: Map<String, String> = emptyMap(), map: (JSONObject) -> T): GroupsIoPage<T> {
        val query = linkedMapOf("limit" to "50").apply { putAll(extra); token?.let { put("page_token", it) } }
        val encoded = query.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }
        val connection = URL("https://groups.io/api/v1/$path?$encoded").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"; connection.connectTimeout = 15_000; connection.readTimeout = 25_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $key")
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw GroupsIoApiException.from(status, body)
            val root = JSONObject(body)
            if (root.optString("object") == "error") throw GroupsIoApiException.from(status, body)
            val data = root.optJSONArray("data") ?: throw GroupsIoApiException("compatibility", "Groups.io returned an incompatible list response")
            val values = buildList { for (index in 0 until data.length()) add(map(data.getJSONObject(index))) }
            val (next, hasMore) = groupsIoPagination(root)
            return GroupsIoPage(values, next, hasMore)
        } catch (error: GroupsIoApiException) { throw error }
        catch (error: UnknownHostException) { throw GroupsIoApiException("network", "No network connection") }
        catch (error: SocketTimeoutException) { throw GroupsIoApiException("temporary", "Groups.io request timed out") }
        catch (error: IOException) { throw GroupsIoApiException("network", "Network request failed") }
        catch (error: org.json.JSONException) { throw GroupsIoApiException("compatibility", "Groups.io response format changed") }
        finally { connection.disconnect() }
    }
}

internal class GroupsIoApiException(val category: String, override val message: String) : IOException(message) {
    companion object {
        fun from(status: Int, body: String): GroupsIoApiException {
            val type = runCatching { JSONObject(body).optString("type") }.getOrDefault("")
            return when {
                status == 401 || type == "unauthorized_error" -> GroupsIoApiException("credential", "API key is invalid or revoked")
                status == 403 || type == "inadequate_permissions" -> GroupsIoApiException("permission", "Groups.io permission is inadequate")
                status == 429 -> GroupsIoApiException("rate_limited", "Groups.io rate limit reached; try later")
                status >= 500 -> GroupsIoApiException("temporary", "Groups.io is temporarily unavailable")
                else -> GroupsIoApiException("server", "Groups.io request failed (HTTP $status)")
            }
        }
    }
}

internal fun normaliseBody(raw: String): String = raw
    .replace(Regex("(?is)<(script|style|iframe|form)[^>]*>.*?</\\1>"), "")
    .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</blockquote>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace('\u00a0', ' ')
    .replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()

private fun JSONObject.firstString(vararg names: String): String = names.firstNotNullOfOrNull { name -> optString(name).takeIf { it.isNotBlank() && it != "null" } }.orEmpty()
private fun JSONObject.requiredString(vararg names: String): String = firstString(*names).ifBlank { throw GroupsIoApiException("compatibility", "Groups.io response omitted ${names.first()}") }
private fun JSONObject.firstLongOrNull(vararg names: String): Long? = names.firstNotNullOfOrNull { name -> if (has(name) && !isNull(name)) optLong(name).takeIf { it != 0L } else null }
private fun JSONObject.requiredLong(vararg names: String): Long = firstLongOrNull(*names) ?: throw GroupsIoApiException("compatibility", "Groups.io response omitted ${names.first()}")
private fun JSONObject.firstInt(vararg names: String): Int = names.firstNotNullOfOrNull { name -> if (has(name)) optInt(name) else null } ?: 0
private fun JSONObject.instantMillis(vararg names: String): Long = names.firstNotNullOfOrNull { name -> firstString(name).takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } } ?: System.currentTimeMillis()

class GroupsIoController(context: Context) {
    private val appContext = context.applicationContext
    private val settings = appContext.getSharedPreferences("rigweave-groupsio", Context.MODE_PRIVATE)
    private val credentials = GroupsIoCredentialStore(appContext)
    private val api = GroupsIoLiveApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: GroupsIoDatabase? = null
    private var operation: Job? = null
    var enabled by mutableStateOf(settings.getBoolean("enabled", false)); private set
    var connected by mutableStateOf(credentials.exists()); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(if (connected) "Connected · cached content available" else "Not connected"); private set
    var lastSyncMillis by mutableLongStateOf(settings.getLong("last_sync", 0L)); private set
    var groups by mutableStateOf<List<GroupsIoGroup>>(emptyList()); private set
    var topics by mutableStateOf<List<GroupsIoTopic>>(emptyList()); private set
    var messages by mutableStateOf<List<GroupsIoMessage>>(emptyList()); private set
    var searchResults by mutableStateOf<List<GroupsIoSearchResult>>(emptyList()); private set
    var selectedGroupId by mutableStateOf<Long?>(null); private set
    var selectedTopicId by mutableStateOf<Long?>(null); private set
    var storageBytes by mutableLongStateOf(0L); private set
    var topicsHaveMore by mutableStateOf(false); private set
    var messagesHaveMore by mutableStateOf(false); private set
    private var topicNextToken: String? = null
    private var messageNextToken: String? = null

    fun updateEnabled(value: Boolean) {
        enabled = value; settings.edit().putBoolean("enabled", value).apply()
        if (!value) { operation?.cancel(); busy = false; status = "Groups.io disabled · downloaded data preserved" }
        else loadCachedGroups()
    }

    fun loadCachedGroups() {
        if (!enabled) return
        scope.launch { val db = db(); val loaded = db.groups(); withContext(Dispatchers.Main) { groups = loaded; storageBytes = db.sizeBytes() } }
    }

    fun connectAndVerify(candidate: String) = replaceOperation {
        val key = candidate.trim()
        require(key.isNotEmpty()) { "Enter a Groups.io API key" }
        val first = api.groups(key)
        val all = first.values.toMutableList(); var next = first.nextPageToken; var more = first.hasMore
        while (more) { ensureActive(); val page = api.groups(key, next); all += page.values; more = page.hasMore; next = page.nextPageToken }
        val now = System.currentTimeMillis(); db().applyMemberships(all, true, now); credentials.save(key)
        settings.edit().putLong("last_sync", now).apply()
        withContext(Dispatchers.Main) { connected = true; groups = db().groups(); lastSyncMillis = now; status = "Connected · memberships synced"; storageBytes = db().sizeBytes() }
    }

    fun syncMemberships() = replaceOperation {
        val key = credentials.load().takeIf(String::isNotBlank) ?: throw GroupsIoApiException("credential", "Connect Groups.io first")
        val all = mutableListOf<GroupsIoGroup>(); var next: String? = null; var more: Boolean
        do { ensureActive(); val page = api.groups(key, next); all += page.values; more = page.hasMore; next = page.nextPageToken } while (more)
        val now = System.currentTimeMillis(); db().applyMemberships(all, true, now); settings.edit().putLong("last_sync", now).apply()
        withContext(Dispatchers.Main) { groups = db().groups(); lastSyncMillis = now; status = "Memberships synced"; storageBytes = db().sizeBytes() }
    }

    fun selectGroup(groupId: Long) {
        selectedGroupId = groupId; selectedTopicId = null; messages = emptyList(); topicsHaveMore = false; topicNextToken = null
        operation?.cancel(); operation = scope.launch {
            val cached = db().topics(groupId)
            withContext(Dispatchers.Main) { topics = cached; status = if (cached.isEmpty()) "No downloaded topics" else "Showing downloaded topics" }
            if (!connected) return@launch
            runCatching { api.topics(credentials.load(), groupId) }.onSuccess { page ->
                val now = System.currentTimeMillis(); db().applyTopics(groupId, page.values, page.nextPageToken, page.hasMore, now)
                withContext(Dispatchers.Main) { topics = db().topics(groupId); topicNextToken = page.nextPageToken; topicsHaveMore = page.hasMore; status = "Newest topics synced"; storageBytes = db().sizeBytes() }
            }.onFailure { publishFailure("topics", groupId.toString(), it) }
        }
    }

    fun selectTopic(topicId: Long) {
        val groupId = selectedGroupId ?: return
        selectedTopicId = topicId; messagesHaveMore = false; messageNextToken = null; operation?.cancel(); operation = scope.launch {
            val cached = db().messages(topicId)
            withContext(Dispatchers.Main) { messages = cached; status = if (cached.isEmpty()) "No downloaded messages" else "Showing downloaded messages" }
            if (!connected) return@launch
            runCatching { api.messages(credentials.load(), groupId, topicId) }.onSuccess { page ->
                val now = System.currentTimeMillis(); db().applyMessages(groupId, topicId, page.values, page.nextPageToken, page.hasMore, now)
                withContext(Dispatchers.Main) { messages = db().messages(topicId); messageNextToken = page.nextPageToken; messagesHaveMore = page.hasMore; status = "Thread synced"; storageBytes = db().sizeBytes() }
            }.onFailure { publishFailure("messages", topicId.toString(), it) }
        }
    }

    fun loadOlderTopics() {
        val groupId = selectedGroupId ?: return; val token = topicNextToken ?: return
        replaceOperation {
            val page = api.topics(credentials.load(), groupId, token); val now = System.currentTimeMillis()
            db().applyTopics(groupId, page.values, page.nextPageToken, page.hasMore, now)
            withContext(Dispatchers.Main) { topics = db().topics(groupId, 100); topicNextToken = page.nextPageToken; topicsHaveMore = page.hasMore; status = "Older topic page downloaded"; storageBytes = db().sizeBytes() }
        }
    }

    fun loadMoreMessages() {
        val groupId = selectedGroupId ?: return; val topicId = selectedTopicId ?: return; val token = messageNextToken ?: return
        replaceOperation {
            val page = api.messages(credentials.load(), groupId, topicId, token); val now = System.currentTimeMillis()
            db().applyMessages(groupId, topicId, page.values, page.nextPageToken, page.hasMore, now)
            withContext(Dispatchers.Main) { messages = db().messages(topicId, 100); messageNextToken = page.nextPageToken; messagesHaveMore = page.hasMore; status = "Additional message page downloaded"; storageBytes = db().sizeBytes() }
        }
    }

    fun search(query: String, groupOnly: Boolean = false) {
        operation?.cancel(); operation = scope.launch {
            delay(250)
            val results = db().search(query, if (groupOnly) selectedGroupId else null, null)
            withContext(Dispatchers.Main) { searchResults = results; status = "Searching downloaded content · ${results.size} results" }
        }
    }

    fun openSearchResult(result: GroupsIoSearchResult) {
        selectedGroupId = result.groupId; selectedTopicId = result.topicId
        scope.launch { val loadedTopics = db().topics(result.groupId); val loadedMessages = db().messages(result.topicId); withContext(Dispatchers.Main) { topics = loadedTopics; messages = loadedMessages; searchResults = emptyList() } }
    }

    fun disconnect() {
        operation?.cancel(); credentials.clear(); connected = false; busy = false; status = "Disconnected · downloaded data preserved"
    }

    fun deleteDownloadedData() {
        operation?.cancel(); database?.deleteDownloadedData(); database = null
        groups = emptyList(); topics = emptyList(); messages = emptyList(); searchResults = emptyList(); selectedGroupId = null; selectedTopicId = null; storageBytes = 0
        status = "Downloaded Groups.io data deleted · credential preserved"
    }

    fun close() { operation?.cancel(); scope.cancel(); database?.close() }
    private fun db(): GroupsIoDatabase = database ?: GroupsIoDatabase(appContext).also { database = it }

    private fun replaceOperation(block: suspend CoroutineScope.() -> Unit) {
        operation?.cancel(); operation = scope.launch {
            withContext(Dispatchers.Main) { busy = true; status = "Contacting Groups.io…" }
            try { block() }
            catch (_: CancellationException) { withContext(Dispatchers.Main) { status = "Operation cancelled" } }
            catch (error: Throwable) { publishFailure("memberships", "", error) }
            finally { withContext(Dispatchers.Main) { busy = false } }
        }
    }

    private suspend fun publishFailure(scopeName: String, scopeId: String, error: Throwable) {
        val apiError = error as? GroupsIoApiException
        val category = apiError?.category ?: "server"
        val message = apiError?.message ?: error.message?.take(120).orEmpty().ifBlank { "Groups.io request failed" }
        runCatching { db().recordFailure(scopeName, scopeId, category, message) }
        withContext(Dispatchers.Main) { status = "$message · downloaded content preserved" }
    }
}

@Composable
fun GroupsIoScreen(controller: GroupsIoController, compact: Boolean) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { controller.loadCachedGroups() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Groups.io", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(controller.status, color = Color(0xFFA5ADB2), style = MaterialTheme.typography.bodySmall) }
            TextButton({ controller.syncMemberships() }, enabled = controller.connected && !controller.busy) { Text("Sync Now") }
        }
        if (!controller.connected) {
            Text("Connect an API key in Settings → Integrations. Downloaded content remains available offline.")
        }
        OutlinedTextField(query, { query = it; controller.search(it) }, label = { Text("Search downloaded content") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (controller.searchResults.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(controller.searchResults, key = { "${it.groupId}:${it.messageNumber}" }) { result ->
                ListItem(headlineContent = { Text(result.topicSubject) }, supportingContent = { Text("${result.groupName} · ${result.author}\n${result.snippet}", maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { query = ""; controller.openSearchResult(result) })
            } }
        } else if (!compact) {
            Row(Modifier.fillMaxSize()) {
                GroupsList(controller, Modifier.widthIn(min = 260.dp, max = 340.dp).fillMaxHeight())
                VerticalDivider()
                GroupsDetail(controller, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            when {
                controller.selectedTopicId != null -> GroupsMessages(controller, Modifier.fillMaxSize())
                controller.selectedGroupId != null -> GroupsTopics(controller, Modifier.fillMaxSize())
                else -> GroupsList(controller, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable private fun GroupsList(controller: GroupsIoController, modifier: Modifier) = LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (controller.groups.isEmpty()) item { Text("No downloaded memberships. Connect and sync, or reconnect when online.", color = Color(0xFFA5ADB2), modifier = Modifier.padding(12.dp)) }
    items(controller.groups, key = { it.id }) { group ->
        ListItem(headlineContent = { Text(group.title) }, supportingContent = { Text(if (group.active) group.name else "${group.name} · cached/inactive") },
            trailingContent = { if (!group.archivesVisible) Text("Restricted", color = Color(0xFFF4C94E)) },
            modifier = Modifier.clickable { controller.selectGroup(group.id) })
    }
}

@Composable private fun GroupsDetail(controller: GroupsIoController, modifier: Modifier) = Row(modifier) {
    GroupsTopics(controller, Modifier.widthIn(min = 280.dp, max = 380.dp).fillMaxHeight()); VerticalDivider(); GroupsMessages(controller, Modifier.weight(1f).fillMaxHeight())
}

@Composable private fun GroupsTopics(controller: GroupsIoController, modifier: Modifier) = LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (controller.selectedGroupId == null) item { Text("Select a group", color = Color(0xFFA5ADB2), modifier = Modifier.padding(16.dp)) }
    else if (controller.topics.isEmpty()) item { Text("No downloaded topics for this group", color = Color(0xFFA5ADB2), modifier = Modifier.padding(16.dp)) }
    items(controller.topics, key = { it.id }) { topic ->
        ListItem(headlineContent = { Text(topic.subject) }, supportingContent = { Text("${topic.messageCount} messages${if (topic.closed) " · closed" else ""}") }, modifier = Modifier.clickable { controller.selectTopic(topic.id) })
    }
    if (controller.topicsHaveMore) item { OutlinedButton(controller::loadOlderTopics, enabled = !controller.busy, modifier = Modifier.padding(12.dp)) { Text("Load Older Topics") } }
}

@Composable private fun GroupsMessages(controller: GroupsIoController, modifier: Modifier) = LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (controller.selectedTopicId == null) item { Text("Select a topic", color = Color(0xFFA5ADB2)) }
    else if (controller.messages.isEmpty()) item { Text("No downloaded messages in this thread", color = Color(0xFFA5ADB2)) }
    items(controller.messages, key = { "${it.groupId}:${it.number}" }) { message ->
        Surface(color = Color(0xFF1B2228), shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(message.author, fontWeight = FontWeight.Bold); Text("#${message.number}", color = Color(0xFFA5ADB2)) }
            if (message.subject.isNotBlank()) Text(message.subject, color = Color(0xFFE9A72B))
            Text(if (message.deleted) "Message unavailable or deleted" else message.body)
            if (message.hasAttachments) Text("Attachments present · not downloaded in Phase 1", color = Color(0xFFF4C94E), style = MaterialTheme.typography.bodySmall)
        } }
    }
    if (controller.messagesHaveMore) item { OutlinedButton(controller::loadMoreMessages, enabled = !controller.busy) { Text("Load More Messages") } }
}

@Composable
fun GroupsIoSettingsPanel(controller: GroupsIoController, openGroupsIo: () -> Unit) {
    var candidate by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Groups.io enabled", fontWeight = FontWeight.Bold); Text("Disabled by default; no sync or startup work when off", style = MaterialTheme.typography.bodySmall) }
            Switch(controller.enabled, controller::updateEnabled)
        }
        Text("Use a Groups.io API key, not your password. The key carries your account permissions; revoke it at Groups.io if compromised.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(candidate, { candidate = it }, label = { Text("Groups.io API key") }, visualTransformation = PasswordVisualTransformation(), enabled = controller.enabled && !controller.connected, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ controller.connectAndVerify(candidate); candidate = "" }, enabled = controller.enabled && !controller.connected && candidate.isNotBlank() && !controller.busy) { Text("Connect and Verify") }
            OutlinedButton({ controller.syncMemberships() }, enabled = controller.enabled && controller.connected && !controller.busy) { Text("Sync Now") }
            OutlinedButton(openGroupsIo, enabled = controller.enabled) { Text("Open Groups.io") }
        }
        Text(controller.status, color = if (controller.connected) Color(0xFF42C77B) else Color(0xFFA5ADB2))
        Text("Downloaded storage: ${controller.storageBytes / 1024} KiB", style = MaterialTheme.typography.bodySmall)
        TextButton({ controller.disconnect() }, enabled = controller.connected) { Text("Disconnect Groups.io") }
        TextButton({ confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete Downloaded Groups.io Data") }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete downloaded Groups.io data?") },
        text = { Text("This deletes only the separate Groups.io database. Your API key remains stored.") },
        confirmButton = { Button({ controller.deleteDownloadedData(); confirmDelete = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}
