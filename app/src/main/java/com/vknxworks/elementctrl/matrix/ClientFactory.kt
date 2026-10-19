package com.vknxworks.elementctrl.matrix

import android.content.Context
import android.os.Build
import android.util.Base64
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.data.SessionStore
import com.vknxworks.elementctrl.data.StoredSession
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.ClientSessionDelegate
import org.matrix.rustcomponents.sdk.CrossProcessLockConfig
import org.matrix.rustcomponents.sdk.RequestConfig
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.SqliteStoreBuilder
import uniffi.matrix_sdk_base.DmRoomDefinition
import uniffi.matrix_sdk_crypto.CollectStrategy
import uniffi.matrix_sdk_crypto.DecryptionSettings
import uniffi.matrix_sdk_crypto.TrustRequirement

/** Where one account's databases live, plus the key that encrypts them. */
data class SessionPaths(val directory: String, val dataDir: File, val cacheDir: File, val key: String) {
    fun deleteAll() {
        dataDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }
}

/** Builds Rust SDK clients configured the way Element X configures them. */
class ClientFactory(private val context: Context, private val store: SessionStore) {

    fun newPaths(): SessionPaths {
        val dir = UUID.randomUUID().toString()
        val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return pathsFor(dir, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
    }

    fun pathsFor(directory: String, key: String) = SessionPaths(
        directory = directory,
        dataDir = File(context.filesDir, "sessions/$directory").apply { mkdirs() },
        cacheDir = File(context.cacheDir, "sessions/$directory").apply { mkdirs() },
        key = key,
    )

    val deviceName: String get() = "ElementCtrl on ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    /** The common builder: encrypted SQLite stores, cross-signing and key backup on, sensible network retries. */
    fun builder(paths: SessionPaths, delegate: ClientSessionDelegate): ClientBuilder {
        val store = SqliteStoreBuilder(paths.dataDir.absolutePath, paths.cacheDir.absolutePath)
            .key(Base64.decode(paths.key, Base64.NO_WRAP))
            .journalSizeLimit(25u * 1024u * 1024u)
        return ClientBuilder()
            .sqliteStore(store)
            .setSessionDelegate(delegate)
            .userAgent("ElementCtrl/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})")
            .autoEnableBackups(true)
            // When a message can't be decrypted (its key never reached this device, e.g. it was asleep or offline
            // when it was sent), fetch that key from the key backup straight away, as Element X does.
            .backupDownloadStrategy(uniffi.matrix_sdk.BackupDownloadStrategy.AFTER_DECRYPTION_FAILURE)
            .autoEnableCrossSigning(true)
            .roomKeyRecipientStrategy(CollectStrategy.ERROR_ON_VERIFIED_USER_PROBLEM)
            .decryptionSettings(DecryptionSettings(senderDeviceTrustRequirement = TrustRequirement.UNTRUSTED))
            .enableShareHistoryOnInvite(true)
            .threadsEnabled(true, threadSubscriptions = false)
            .dmRoomDefinition(DmRoomDefinition.TWO_MEMBERS)
            .requestConfig(RequestConfig(retryLimit = 3u, timeout = 30_000u, maxConcurrentRequests = null, maxRetryTime = null))
            .crossProcessLockConfig(CrossProcessLockConfig.SingleProcess)
            // Local full-text index for message search, encrypted with the same key as the databases,
            // so search also works in end-to-end encrypted rooms.
            .withSearchIndexStore(File(paths.dataDir, "search-index").absolutePath, paths.key)
    }

    /** A builder for the sign-in screens: discovers the server and checks it supports native sliding sync. */
    fun loginBuilder(paths: SessionPaths, server: String): ClientBuilder =
        builder(paths, NoOpSessionDelegate)
            .serverNameOrHomeserverUrl(server)
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)

    fun toStored(session: Session, paths: SessionPaths) = StoredSession(
        userId = session.userId,
        deviceId = session.deviceId,
        homeserverUrl = session.homeserverUrl,
        accessToken = session.accessToken,
        refreshToken = session.refreshToken,
        oauthData = session.oauthData,
        databaseKey = paths.key,
        directory = paths.directory,
    )

    fun toSession(s: StoredSession) = Session(
        accessToken = s.accessToken,
        refreshToken = s.refreshToken,
        userId = s.userId,
        deviceId = s.deviceId,
        homeserverUrl = s.homeserverUrl,
        oauthData = s.oauthData,
        slidingSyncVersion = SlidingSyncVersion.NATIVE,
    )
}

/** Used while signing in, before there is a session worth saving. */
object NoOpSessionDelegate : ClientSessionDelegate {
    override fun retrieveSessionFromKeychain(userId: String): Session = error("Not used on Android")
    override fun saveSessionInKeychain(session: Session) = Unit
}

/** Saves refreshed tokens (OAuth servers rotate them) so the next app start can restore the session. */
class StoringSessionDelegate(private val store: SessionStore) : ClientSessionDelegate {
    override fun retrieveSessionFromKeychain(userId: String): Session = error("Not used on Android")

    override fun saveSessionInKeychain(session: Session) {
        val current = store.load() ?: return
        if (current.userId != session.userId) return
        store.save(
            current.copy(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                oauthData = session.oauthData ?: current.oauthData,
                homeserverUrl = session.homeserverUrl,
            )
        )
    }
}
