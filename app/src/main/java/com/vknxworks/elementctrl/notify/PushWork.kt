package com.vknxworks.elementctrl.notify

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.elementctrl.matrix.MatrixHttp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pushed event IDs waiting to be turned into notifications. Kept on disk so that nothing is lost when Android
 * stops the app, or the phone has no network in deep sleep, between the push arriving and the event being fetched.
 */
object PushQueue {
    data class Pending(val roomId: String, val eventId: String, val receivedAt: Long, val attempts: Int)

    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val MAX_ITEMS = 100
    private val lock = Any()

    private fun prefs(context: Context) = context.getSharedPreferences("push_queue", Context.MODE_PRIVATE)

    private fun read(context: Context): MutableList<Pending> {
        val arr = runCatching { JSONArray(prefs(context).getString("q", "[]")) }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Pending(o.optString("r"), o.optString("e"), o.optLong("t"), o.optInt("n"))
        }.filter { it.roomId.isNotEmpty() && it.eventId.isNotEmpty() && now - it.receivedAt < MAX_AGE_MS }.toMutableList()
    }

    private fun write(context: Context, items: List<Pending>) {
        val arr = JSONArray()
        items.takeLast(MAX_ITEMS).forEach { p ->
            arr.put(JSONObject().put("r", p.roomId).put("e", p.eventId).put("t", p.receivedAt).put("n", p.attempts))
        }
        prefs(context).edit().putString("q", arr.toString()).commit()
    }

    fun add(context: Context, roomId: String, eventId: String) = synchronized(lock) {
        val items = read(context)
        if (items.none { it.eventId == eventId }) items += Pending(roomId, eventId, System.currentTimeMillis(), 0)
        write(context, items)
    }

    fun pending(context: Context): List<Pending> = synchronized(lock) { read(context) }

    fun remove(context: Context, eventId: String) = synchronized(lock) {
        write(context, read(context).filter { it.eventId != eventId })
    }

    /** Counts a failed try and returns how many there have been. */
    fun failed(context: Context, eventId: String): Int = synchronized(lock) {
        val items = read(context)
        val i = items.indexOfFirst { it.eventId == eventId }
        if (i < 0) return@synchronized Int.MAX_VALUE
        items[i] = items[i].copy(attempts = items[i].attempts + 1)
        write(context, items)
        items[i].attempts
    }

    fun clear(context: Context) = synchronized(lock) { prefs(context).edit().clear().commit(); Unit }
}

/**
 * Turns queued pushes into notifications, the way Element X does it: as an expedited WorkManager job, which
 * Android starts straight away and lets finish even when the app was asleep or stopped, and which retries
 * when there's no network yet instead of silently dropping the message.
 */
class FetchPushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Push.isEnabled(context)) {
            PushQueue.clear(context)
            return Result.success()
        }
        val session = Push.waitForSession(timeoutMs = 60_000)
        if (session == null) {
            // Still starting up (or no session at all): try again shortly, and after a few tries show what we have.
            if (runAttemptCount < 3) return Result.retry()
            PushQueue.pending(context).forEach { PushNotifications.postFallback(context, it.roomId) }
            PushQueue.clear(context)
            return Result.success()
        }
        // Pushes that arrive while this runs are picked up by the next round.
        val done = HashSet<String>()
        while (true) {
            val batch = PushQueue.pending(context).filter { it.eventId !in done }
            if (batch.isEmpty()) break
            for (p in batch) {
                done += p.eventId
                runCatching { PushNotifications.resolve(context, session, p.roomId, p.eventId) }
                    .onSuccess { outcome ->
                        if (outcome == PushNotifications.Outcome.NOT_FOUND) PushNotifications.postFallback(context, p.roomId)
                        PushQueue.remove(context, p.eventId)
                    }
                    .onFailure {
                        Log.w(TAG, "fetch failed: ${it.javaClass.simpleName}")
                        if (PushQueue.failed(context, p.eventId) >= MAX_TRIES) {
                            PushNotifications.postFallback(context, p.roomId)
                            PushQueue.remove(context, p.eventId)
                        }
                    }
            }
        }
        return if (PushQueue.pending(context).isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "FetchPush"
        private const val WORK = "push-fetch"
        private const val MAX_TRIES = 3

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<FetchPushWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            // A running job loops until the queue is empty, so a new push doesn't need a second one.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

/**
 * Twice a day, and at every start, checks that the homeserver still has our pusher. Synapse quietly deletes a
 * pusher when its gateway reports the push key as unknown (ntfy restarted, topic gone), after which no pushes
 * arrive at all until the app is opened; this puts it back.
 */
class PusherCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Push.isEnabled(context)) return Result.success()
        val session = Push.waitForSession(timeoutMs = 60_000) ?: return Result.retry()
        val endpoint = Push.storedEndpoint(context)
        if (endpoint == null) {
            // Registration was lost: ask the push app for an endpoint again.
            Push.reregister(context)
            return Result.success()
        }
        return runCatching {
            val (_, json) = MatrixHttp.request(session, "GET", "/_matrix/client/v3/pushers")
            val pushers = json.optJSONArray("pushers") ?: JSONArray()
            val present = (0 until pushers.length()).any { i ->
                val p = pushers.optJSONObject(i)
                p?.optString("pushkey") == endpoint && p.optString("app_id") == Push.APP_ID
            }
            if (!present) {
                Log.w(TAG, "pusher missing on the homeserver, adding it again")
                Push.onNewEndpoint(context, endpoint)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val TAG = "PusherCheck"
        private const val PERIODIC = "pusher-check"
        private const val ONCE = "pusher-check-now"

        private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PusherCheckWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build(),
            )
            wm.enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PusherCheckWorker>().setConstraints(constraints).setInitialDelay(20, TimeUnit.SECONDS).build(),
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(ONCE)
        }
    }
}
