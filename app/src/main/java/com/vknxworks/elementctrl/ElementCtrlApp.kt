package com.vknxworks.elementctrl

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.vknxworks.elementctrl.matrix.AppGraph
import com.vknxworks.elementctrl.matrix.MediaFetcher
import com.vknxworks.elementctrl.matrix.MediaFiles
import com.vknxworks.elementctrl.notify.Notifier
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

class ElementCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // The restart helper process only relaunches the app; nothing else may start there.
        if (AppRestart.isRestartProcess()) return
        instance = this
        com.vknxworks.elementctrl.matrix.ConnectionLog.init(this)
        // Native SDK first: it needs the Context for TLS checks, then logging/runtime setup.
        runCatching { org.matrix.rustsdk.Android.init(this) }
            .onFailure { Log.e("ElementCtrl", "Rust SDK Android init failed", it) }
        runCatching {
            initPlatform(
                config = TracingConfiguration(
                    logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR,
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    // Release builds don't send the SDK's logs to logcat at all (they can name rooms, users,
                    // servers and media URLs), the same as Element X.
                    writeToStdoutOrSystem = BuildConfig.DEBUG,
                    writeToFiles = null,
                    sentryConfig = null,
                ),
                useLightweightTokioRuntime = false,
            )
        }.onFailure { Log.e("ElementCtrl", "Rust SDK init failed", it) }

        graph = AppGraph(this)
        // Decrypted attachments only live in the cache for a day.
        graph.scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { MediaFiles.purgeOld(this@ElementCtrlApp) } }
        Notifier.createChannels(this)
        com.vknxworks.elementctrl.call.CallNotifications.createChannels(this)
        graph.boot()

        // Sync while the app is open; the background worker takes over (if enabled) when it isn't.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                com.vknxworks.elementctrl.ui.lock.AppLock.onForeground(graph.lockEnabled, graph.lock.lockTimeout.millis)
                // The session's sync orchestrator starts syncing (or restarts it if it died meanwhile).
                graph.session?.inForeground = true
            }
            override fun onStop(owner: LifecycleOwner) {
                com.vknxworks.elementctrl.ui.lock.AppLock.onBackground(graph.lockEnabled, graph.lock.lockTimeout.millis)
                graph.session?.inForeground = false
                Notifier.schedule(
                    this@ElementCtrlApp,
                    // Also with push through ntfy: the 15-minute check catches anything a push didn't bring.
                    graph.prefs.notifications.value && graph.session != null,
                )
            }
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaFetcher.Factory { graph.session })
                add(MediaFetcher.keyer)
                // Plays animated GIFs and stickers.
                add(coil3.gif.AnimatedImageDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            // Pictures (often decrypted from encrypted rooms) are only ever kept in memory, never written to disk.
            .diskCache { null }
            .build()

    companion object {
        lateinit var instance: ElementCtrlApp
            private set
        val graph: AppGraph get() = instance.graph
    }
}
