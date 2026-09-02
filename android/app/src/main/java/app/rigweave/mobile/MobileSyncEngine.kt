package app.rigweave.mobile

import android.content.Context
import android.util.Base64
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val MOBILE_SYNC_PERIODIC = "rigweave.m9.mobile-sync.periodic"
private const val MOBILE_SYNC_MANUAL = "rigweave.m9.mobile-sync.manual"
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/** Bounded Android scheduling. WorkManager timing is opportunistic, never presented as exact. */
object MobileSyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true).build()

    fun install(context: Context) {
        val work = PeriodicWorkRequestBuilder<MobileSyncWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
            .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(MOBILE_SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    fun syncNow(context: Context) {
        val work = OneTimeWorkRequestBuilder<MobileSyncWorker>().setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MOBILE_SYNC_MANUAL, ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MOBILE_SYNC_MANUAL)
        WorkManager.getInstance(context).cancelUniqueWork(MOBILE_SYNC_PERIODIC)
    }
}

class MobileSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val result = AndroidMobileSyncEngine(applicationContext).runOnce()
        Result.success(workDataOf("state" to result.state, "pushed" to result.pushed, "pulled" to result.pulled))
    }.getOrElse { error ->
        val safe = when (error) {
            is MobileSyncPermanentFailure -> return Result.failure(workDataOf("state" to error.message.orEmpty()))
            else -> error.javaClass.simpleName.take(80)
        }
        if (runAttemptCount >= 5) Result.failure(workDataOf("state" to safe)) else Result.retry()
    }
}

data class MobileSyncRunResult(val state: String, val pushed: Int, val pulled: Int)
private class MobileSyncPermanentFailure(message: String) : Exception(message)
private data class AndroidPeer(val spaceId:String,val stationId:String,val accountId:String,val authority:String,val keyVersion:Int,val endpoint:String,val cursor:String?)

/**
 * Direct Station Sync transport. It uses one canonical QSO owner, signed bounded HTTPS
 * requests, batches of at most 200, no permanent socket, and no hardware authority.
 */
class AndroidMobileSyncEngine(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
) {
    private val database = QsoDatabase.shared(context)
    private val identity = MobileDeviceIdentity()
    private val settings = context.getSharedPreferences("rigweave.m9.sync-peers", Context.MODE_PRIVATE)

    fun configureDirectPeer(spaceId:String, endpoint:String, accountId:String, stationId:String) {
        validateEndpoint(endpoint)
        settings.edit().putString("$spaceId.endpoint", endpoint.trimEnd('/')).putString("$spaceId.account", accountId)
            .putString("$spaceId.station", stationId).apply()
    }

    fun removePeer(spaceId:String) {
        settings.edit().remove("$spaceId.endpoint").remove("$spaceId.account").remove("$spaceId.station").apply()
    }

    fun runOnce(): MobileSyncRunResult {
        val peers = peers()
        if (peers.isEmpty()) return MobileSyncRunResult("NO_CONFIGURED_PEER", 0, 0)
        var pushed = 0; var pulled = 0
        for (peer in peers) {
            hello(peer)
            pushed += push(peer)
            pulled += pull(peer)
        }
        return MobileSyncRunResult("COMPLETE", pushed, pulled)
    }

    private fun peers():List<AndroidPeer> = database.readableDatabase.rawQuery(
        "SELECT s.id,s.station_id,COALESCE(d.account_id,''),s.authority,s.key_version,c.accepted_order FROM sync_spaces s JOIN sync_devices d ON d.sync_space_id=s.id AND d.id=? AND d.state='APPROVED' LEFT JOIN sync_cursors c ON c.sync_space_id=s.id AND c.peer_id='LOCAL_HUB' WHERE s.state='ACTIVE' AND s.mode='DIRECT_STATION_SYNC'",
        arrayOf(identity.deviceId),
    ).use { cursor -> buildList { while (cursor.moveToNext()) {
        val id=cursor.getString(0); val endpoint=settings.getString("$id.endpoint",null) ?: continue
        add(AndroidPeer(id,cursor.getString(1),settings.getString("$id.account",cursor.getString(2)).orEmpty(),cursor.getString(3),cursor.getInt(4),endpoint,if(cursor.isNull(5))null else cursor.getLong(5).toString()))
    } } }

    private fun hello(peer:AndroidPeer) {
        val domains=JSONObject(); listOf("QSO","QSO_TOMBSTONE","QSO_CONFIRMATION","QSO_CONFLICT_RESOLUTION","STATION_LOGBOOK_MAPPING","GOAL","WATCHLIST").forEach{domains.put(it,1)}
        val body=JSONObject().put("kind","sync.hello").put("protocol",JSONObject().put("major",1).put("minor",0))
            .put("deviceId",identity.deviceId).put("deviceKeyVersion",1).put("devicePublicIdentity",identity.publicKeyPem())
            .put("accountId",peer.accountId.ifBlank{"local-account"}).put("stationId",peer.stationId).put("syncSpaceId",peer.spaceId)
            .put("appVersion",BuildConfig.VERSION_NAME).put("platform","ANDROID").put("domainSchemaVersions",domains)
            .put("lastCursor",peer.cursor ?: JSONObject.NULL).put("lastCheckpointId",JSONObject.NULL)
            .put("encryptionSuites",JSONArray().put("XCHACHA20_POLY1305_IETF+CRYPTO_BOX_SEAL"))
            .put("maxBatchEvents",200).put("maxFrameBytes",4*1024*1024).put("authority",peer.authority).toString()
        val response=request(peer,"POST","/api/v1/mobile-sync/hello",body)
        if(response.optString("compatibility")!="COMPATIBLE")throw MobileSyncPermanentFailure("SYNC_PROTOCOL_INCOMPATIBLE")
    }

    private fun push(peer:AndroidPeer):Int {
        val events=JSONArray(); val ids=mutableListOf<String>()
        database.readableDatabase.rawQuery("SELECT id,domain,entity_id,entity_revision,operation,device_sequence,created_at FROM sync_outbox WHERE sync_space_id=? AND state IN ('PENDING','RETRY') AND (next_attempt_at IS NULL OR next_attempt_at<=?) ORDER BY device_sequence LIMIT 200",arrayOf(peer.spaceId,(System.currentTimeMillis()/1000).toString())).use{cursor->
            while(cursor.moveToNext()){
                val id=cursor.getString(0); val entityId=cursor.getString(2); val qso=database.qso(entityId) ?: if(cursor.getString(4)=="TOMBSTONE") null else continue
                val payload=qso?.let(::qsoPayload) ?: JSONObject(); val contentHash=sha256(payload.toString().toByteArray())
                val event=JSONObject().put("protocol","rigweave.sync.v1").put("eventId",id).put("syncSpaceId",peer.spaceId)
                    .put("originDeviceId",identity.deviceId).put("deviceSequence",cursor.getLong(5)).put("domain",cursor.getString(1))
                    .put("entityId",entityId).put("operation",cursor.getString(4)).put("baseRevision",cursor.getLong(3)-1)
                    .put("proposedRevision",cursor.getLong(3)).put("tombstone",cursor.getString(4)=="TOMBSTONE")
                    .put("createdUtc",Instant.ofEpochSecond(cursor.getLong(6)).toString()).put("clientOrder",JSONObject.NULL)
                    .put("idempotencyKey","android:$id").put("contentHashSha256",contentHash).put("keyVersion",peer.keyVersion)
                    .put("ciphertextBase64",JSONObject.NULL).put("nonceBase64",JSONObject.NULL).put("directPayload",payload)
                    .put("associatedDataHashSha256",sha256("${peer.spaceId}|$id|${identity.deviceId}|${cursor.getLong(5)}".toByteArray()))
                    .put("payloadSchemaVersion",1)
                events.put(event);ids+=id
            }
        }
        if(ids.isEmpty())return 0
        database.writableDatabase.execSQL("UPDATE sync_outbox SET state='UPLOADING',attempt_count=attempt_count+1,updated_at=? WHERE id IN (${ids.joinToString(","){"?"}})",arrayOf(System.currentTimeMillis()/1000,*ids.toTypedArray()))
        val result=request(peer,"POST","/api/v1/mobile-sync/spaces/${peer.spaceId}/push",JSONObject().put("events",events).toString())
        val rows=result.getJSONArray("results");for(index in 0 until rows.length()){val row=rows.getJSONObject(index);val state=when(row.getString("code")){"ACCEPTED","ALREADY_APPLIED"->"STATION_ACCEPTED";"CONFLICT"->"CONFLICT";"WAVELOG_PENDING"->"WAVELOG_PENDING";else->"REJECTED"};database.writableDatabase.execSQL("UPDATE sync_outbox SET state=?,safe_error=?,updated_at=? WHERE id=?",arrayOf(state,row.optString("safeMessage"),System.currentTimeMillis()/1000,row.getString("eventId")))}
        return rows.length()
    }

    private fun pull(peer:AndroidPeer):Int {
        var cursor=peer.cursor;var applied=0;var more:Boolean
        do {
            val suffix=if(cursor==null)"" else "?cursor=$cursor&limit=200"
            val response=request(peer,"GET","/api/v1/mobile-sync/spaces/${peer.spaceId}/pull$suffix","")
            val events=response.getJSONArray("events")
            for(index in 0 until events.length())if(applyIncoming(peer,events.getJSONObject(index)))applied++
            cursor=response.getString("cursor");more=response.optBoolean("hasMore",false)
            database.writableDatabase.execSQL("INSERT OR REPLACE INTO sync_cursors(sync_space_id,peer_id,accepted_order,updated_at) VALUES(?,?,?,?)",arrayOf(peer.spaceId,"LOCAL_HUB",cursor.toLong(),System.currentTimeMillis()/1000))
        } while(more)
        return applied
    }

    private fun applyIncoming(peer:AndroidPeer,event:JSONObject):Boolean {
        val eventId=event.getString("eventId")
        if(database.readableDatabase.rawQuery("SELECT 1 FROM sync_inbox WHERE event_id=?",arrayOf(eventId)).use{it.moveToFirst()})return false
        val domain=event.getString("domain");if(domain!="QSO"&&domain!="QSO_TOMBSTONE")return false
        val id=event.getString("entityId");val operation=event.getString("operation");val payload=event.optJSONObject("directPayload") ?: JSONObject()
        val coordinator=QsoMutationCoordinator(database)
        when(operation){
            "CREATE","RESTORE"->{if(database.qso(id)==null)coordinator.save(qso(payload,id),QsoOrigin.REMOTE_SYNC)else coordinator.update(qso(payload,id),QsoOrigin.REMOTE_SYNC)}
            "UPDATE"->coordinator.update(qso(payload,id),QsoOrigin.REMOTE_SYNC)
            "TOMBSTONE"->if(database.qso(id)!=null)coordinator.delete(id,QsoDeleteIntent.REMOTE_SYNC,QsoOrigin.REMOTE_SYNC)
            else->throw MobileSyncPermanentFailure("SYNC_OPERATION_REQUIRES_REVIEW")
        }
        val now=System.currentTimeMillis()/1000
        database.writableDatabase.execSQL("INSERT INTO sync_inbox(event_id,sync_space_id,origin_device_id,device_sequence,domain,entity_id,operation,key_version,content_hash_sha256,received_at,applied_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",arrayOf(eventId,peer.spaceId,event.getString("originDeviceId"),event.getLong("deviceSequence"),domain,id,operation,event.getInt("keyVersion"),event.getString("contentHashSha256"),now,now))
        return true
    }

    private fun request(peer:AndroidPeer,method:String,pathAndQuery:String,body:String):JSONObject {
        validateEndpoint(peer.endpoint);val path=pathAndQuery.substringBefore('?');val timestamp=Instant.now().toString();val nonce=UUID.randomUUID().toString().replace("-","")
        val signature=Base64.encodeToString(identity.sign("$method\n$path\n$timestamp\n$nonce\n${sha256(body.toByteArray())}".toByteArray()),Base64.NO_WRAP)
        val builder=Request.Builder().url(peer.endpoint+pathAndQuery).header("x-rigweave-device-id",identity.deviceId).header("x-rigweave-timestamp",timestamp).header("x-rigweave-nonce",nonce).header("x-rigweave-signature",signature)
        if(method=="POST")builder.post(body.toRequestBody(JSON_MEDIA))else builder.get()
        client.newCall(builder.build()).execute().use{response->val text=response.body.string();if(!response.isSuccessful)throw if(response.code in 400..499)MobileSyncPermanentFailure("SYNC_HTTP_${response.code}")else Exception("SYNC_HTTP_${response.code}");if(text.toByteArray().size>4*1024*1024)throw MobileSyncPermanentFailure("SYNC_RESPONSE_TOO_LARGE");return JSONObject(text)}
    }

    private fun validateEndpoint(value:String){val uri=URI(value);val loopback=uri.host in setOf("127.0.0.1","localhost","::1");if(uri.scheme!="https"&&!(uri.scheme=="http"&&loopback))throw MobileSyncPermanentFailure("SYNC_ENDPOINT_REQUIRES_HTTPS")}
    private fun qsoPayload(q:Qso)=JSONObject().put("id",q.id).put("callsign",q.callsign).put("contactStartUtc",Instant.ofEpochSecond(q.createdAt).toString()).put("frequencyHz",q.frequencyHz.toString()).put("band",q.band).put("mode",q.mode).put("rstSent",q.rstSent).put("rstReceived",q.rstReceived).put("grid",q.grid).put("logbookId","mobile-local").put("stationProfileId",q.stationProfileId.ifBlank{DEFAULT_LOCAL_STATION})
    private fun qso(p:JSONObject,id:String)=Qso(id=id,callsign=p.getString("callsign"),frequencyHz=p.getString("frequencyHz").toLong(),mode=p.getString("mode"),rstSent=p.optString("rstSent"),rstReceived=p.optString("rstReceived"),createdAt=Instant.parse(p.getString("contactStartUtc")).epochSecond,band=p.optString("band"),grid=p.optString("grid"),stationProfileId=p.optString("stationProfileId").takeUnless{it==DEFAULT_LOCAL_STATION}.orEmpty())
}
