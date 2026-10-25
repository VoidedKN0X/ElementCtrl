package com.vknxworks.elementctrl.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.components.CtrlButton
import io.nayuki.qrcodegen.QrCode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import zxingcpp.BarcodeReader

/**
 * Full-screen camera that reads one QR code and hands back its raw bytes (sign-in codes are binary). Decoded on
 * the phone with the open-source zxing-cpp library; nothing leaves the phone. Asks for the camera when it opens.
 */
@Composable
fun QrScanDialog(prompt: String, onResult: (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
            ask.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The camera view stays dark in both themes, like the call screen and the photo viewer.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) QrCamera(Modifier.fillMaxSize(), onResult)
            // A square to aim at.
            Box(
                Modifier.align(Alignment.Center).size(240.dp)
                    .border(3.dp, com.vknxworks.elementctrl.ui.CtrlColors.Accent, RoundedCornerShape(26.dp)),
            )
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (denied) stringResource(R.string.qr_scan_no_camera) else prompt,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                CtrlButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) }
            }
        }
    }
}

@Composable
private fun QrCamera(modifier: Modifier, onCode: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val reader = BarcodeReader(BarcodeReader.Options(formats = setOf(BarcodeReader.Format.QR_CODE), tryHarder = true))
        val done = AtomicBoolean(false)
        // Closed before the camera was ready: don't bind it afterwards (it would stay on with nothing to read it).
        val disposed = AtomicBoolean(false)
        providerFuture.addListener({
            if (disposed.get()) return@addListener
            val p = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                val bytes = if (disposed.get()) null else decode(reader, image)
                image.close()
                if (bytes != null && done.compareAndSet(false, true)) {
                    ContextCompat.getMainExecutor(context).execute { if (!disposed.get()) callback.value(bytes) }
                }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true)
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun decode(reader: BarcodeReader, image: ImageProxy): ByteArray? = try {
    reader.read(image).firstNotNullOfOrNull { r -> r.bytes?.takeIf { it.isNotEmpty() } }
} catch (e: Exception) {
    null
}

/** A QR code for [bytes], drawn black on white (whatever the theme) with a quiet zone so any camera can read it. */
@Composable
fun QrImage(bytes: ByteArray, contentDescription: String, modifier: Modifier = Modifier) {
    val label = contentDescription
    val qr = remember(bytes) { runCatching { QrCode.encodeBinary(bytes, QrCode.Ecc.LOW) }.getOrNull() }
    Box(
        modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp)
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (qr != null) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = floor(size.minDimension / qr.size)
                val origin = (size.minDimension - cell * qr.size) / 2f
                val square = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f)
                for (y in 0 until qr.size) {
                    for (x in 0 until qr.size) {
                        if (qr.getModule(x, y)) drawRect(Color.Black, topLeft = Offset(origin + x * cell, origin + y * cell), size = square)
                    }
                }
            }
        }
    }
}
