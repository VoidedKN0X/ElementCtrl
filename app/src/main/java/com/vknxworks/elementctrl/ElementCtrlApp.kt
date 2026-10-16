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
        instance = this
        // Native SDK first: it needs the Context for TLS checks, then logging/runtime setup.
        runCatching { org.matrix.rustsdk.Android.init(this) }
            .onFailure { Log.e("ElementCtrl", "Rust SDK Android init failed", it) }
        runCatching {
            initPlatform(
                config = TracingConfiguration(
                    logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN,
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    writeToStdoutOrSystem = true,
                    writeToFiles = null,
                    sentryConfig = null,
                ),
                useLightweightTokioRuntime = false,
            )
        }.onFailure { Log.e("ElementCtrl", "Rust SDK init failed", it) }

        graph = AppGraph(this)
        Notifier.createChannels(this)
        graph.boot()

        // Sync while the app is open; the background worker takes over (if enabled) when it isn't.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                graph.session?.let { s ->
                    s.inForeground = true
                    s.scope.launch { s.startSync() }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                graph.session?.let { s ->
                    s.inForeground = false
                    s.scope.launch { s.stopSync() }
                }
                Notifier.schedule(
                    this@ElementCtrlApp,
                    graph.prefs.notifications.value && graph.session != null && !com.vknxworks.elementctrl.notify.Push.isEnabled(this@ElementCtrlApp),
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
            .build()

    companion object {
        lateinit var instance: ElementCtrlApp
            private set
        val graph: AppGraph get() = instance.graph
    }
}
