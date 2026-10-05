package rocks.myburgh.bokyqr.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rocks.myburgh.bokyqr.R
import rocks.myburgh.bokyqr.core.galleryImportAllowed
import rocks.myburgh.bokyqr.gallery.GalleryLoader
import rocks.myburgh.bokyqr.scan.QrAnalyzer
import rocks.myburgh.bokyqr.scan.ScanViewModel
import rocks.myburgh.bokyqr.scanner.BarcodeDecoder
import rocks.myburgh.bokyqr.scanner.createBarcodeDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * The first screen. If the camera permission is already granted the camera binds immediately;
 * otherwise a single runtime request is made. There is no splash and no artificial delay.
 *
 * Nothing on this screen touches the network. Decoding a frame produces a string, and that string
 * is handed to `:core.PayloadPolicy`; the resulting decision is what the result sheet acts on.
 */
@Composable
fun CameraScreen(viewModel: ScanViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) permissionPermanentlyDenied = isPermanentlyDenied(context, asked = true)
    }

    // Re-check on every resume. The permission launcher is not the only way the answer can change:
    // the user can grant Camera from the system Settings app while BokyQR is in the background, and
    // without this the screen would keep showing "permission denied" with a Grant button that
    // now fails silently, because the system will not prompt a second time.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            hasPermission = granted
            if (!granted) {
                permissionPermanentlyDenied = isPermanentlyDenied(context, permissionRequested)
            } else {
                permissionPermanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val analyzer = remember { QrAnalyzer { payload -> viewModel.onPayload(payload) } }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var decoder by remember { mutableStateOf<BarcodeDecoder?>(null) }

    // The decoder is built off the main thread. On the play flavor BarcodeScanning.getClient() does
    // real work, and a decoder that throws while the model is unavailable would otherwise take the
    // whole composition down with it.
    LaunchedEffect(context) {
        val created = withContext(Dispatchers.Default) {
            runCatching { createBarcodeDecoder(context) }
        }.getOrElse { error ->
            cameraError = "The barcode scanner could not be started on this device."
            null
        }
        decoder = created
        analyzer.setDecoder(created)
    }

    DisposableEffect(Unit) {
        onDispose {
            // Order matters: stop frames, drain the frames already queued, then release the decoder.
            // Closing it while an analyze() is still inside Tasks.await(...) races the scanner.
            runCatching { cameraProvider?.unbindAll() }
            camera = null
            cameraProvider = null
            torchAvailable = false
            torchOn = false
            analyzer.setDecoder(null)
            val closing = decoder
            executor.shutdown()
            // shutdown() lets queued work finish; anything submitted after it runs last, so
            // close() cannot overtake an in-flight decode. Blocking the main thread on
            // awaitTermination instead would freeze the frame we are disposing on.
            val queued = runCatching {
                executor.execute { runCatching { closing?.close() } }
            }.isSuccess
            if (!queued) {
                // The executor was already shut down, so nothing can be in flight; close inline.
                runCatching { closing?.close() }
            }
        }
    }

    LaunchedEffect(hasPermission, lifecycleOwner, decoder) {
        // Nothing binds until a decoder exists: binding an analysis stream whose decoder is null
        // would burn frames decoding nothing.
        if (decoder == null || !hasPermission) return@LaunchedEffect
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.cancel(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
        cameraProvider = provider
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            // Replaces the deprecated setTargetResolution(Size(1280, 720)): the selector asks for
            // 16:9 where the camera can and falls back to its best other ratio where it cannot,
            // instead of asking for an exact pixel size some devices never produce.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, analyzer) }

        // A device with only a front camera still installs this app (camera is required="false"),
        // so fall back rather than reporting a generic bind failure on it.
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> {
                cameraError = "This device has no camera that BokyQR can use."
                return@LaunchedEffect
            }
        }
        try {
            provider.unbindAll()
            val bound = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            camera = bound
            torchAvailable = bound.cameraInfo.hasFlashUnit()
            bound.cameraControl.enableTorch(torchOn)
            cameraError = null
        } catch (_: Exception) {
            cameraError = "The camera could not be started on this device."
        }
    }

    // Pause the analyzer while a result sheet is up so a code in front of the camera is not decoded
    // over and over.
    LaunchedEffect(viewModel.sheetVisible) {
        analyzer.paused = viewModel.sheetVisible
    }

    // Resolved here, not inside the picker callback: a launcher result is not a composable scope.
    val galleryDisabledMessage = stringResource(R.string.gallery_disabled)
    val galleryUnreadableMessage = stringResource(R.string.gallery_unreadable)
    val galleryNoCodeMessage = stringResource(R.string.gallery_no_code)

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The picker can hand back a result even after the setting was turned off (a rotation, or a
        // stale callback). :core.galleryImportAllowed is the enforcement point, not the button.
        if (!galleryImportAllowed(viewModel.galleryImportEnabled)) {
            viewModel.showNotice(galleryDisabledMessage)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bitmap = GalleryLoader.load(context, uri)
            if (bitmap == null) {
                viewModel.showNotice(galleryUnreadableMessage)
                return@launch
            }
            val payload = try {
                withContext(Dispatchers.Default) { decoder?.decodeStill(bitmap) }
            } finally {
                // Recycled whatever happened: an OutOfMemoryError here is the one case where
                // holding on to a full-size bitmap makes the next frame worse.
                bitmap.recycle()
            }
            if (payload == null) {
                viewModel.showNotice(galleryNoCodeMessage)
            } else {
                viewModel.onPayload(payload)
            }
        }
    }

    var galleryEnabled by remember { mutableStateOf(viewModel.galleryImportEnabled) }
    LaunchedEffect(viewModel.sheetVisible) {
        galleryEnabled = viewModel.galleryImportEnabled
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.camera_permission_rationale),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (permissionPermanentlyDenied) {
                    Text(
                        text = stringResource(R.string.camera_permission_permanently_denied_body),
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Button(
                        onClick = { context.openAppDetailsSettings() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.camera_permission_open_settings))
                    }
                } else if (permissionRequested) {
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.camera_permission_grant))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasPermission && torchAvailable) {
                    OutlinedButton(onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    }) {
                        Text(if (torchOn) "Torch off" else "Torch on")
                    }
                }
                OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            }
            if (hasPermission) {
                Text(
                    text = "Point the camera at a QR code",
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (galleryEnabled && hasPermission) {
            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
            ) {
                Text("Scan image from gallery")
            }
        }

        cameraError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (viewModel.sheetVisible && viewModel.decision != null) {
        ResultSheet(
            viewModel = viewModel,
            onDismiss = {
                viewModel.dismissSheet()
                analyzer.reset()
            },
            onOpenSettings = onOpenSettings,
        )
    }

    viewModel.notice?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearNotice() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearNotice() }) { Text("OK") }
            },
            text = { Text(message) },
        )
    }
}

/**
 * Whether the system will still prompt for the camera permission.
 *
 * `shouldShowRequestPermissionRationale` is false both before the first ask and after the user has
 * chosen "don't ask again", so [asked] distinguishes them: false once the user has actually been
 * prompted means the answer will never come from the dialog again, and the only way forward is the
 * app's settings page.
 */
private fun isPermanentlyDenied(context: android.content.Context, asked: Boolean): Boolean {
    val activity = context.findActivity() ?: return false
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return asked && !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.CAMERA,
    )
}

/** Opens this app's entry in system settings, where a permanently denied permission can be granted. */
private fun android.content.Context.openAppDetailsSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}