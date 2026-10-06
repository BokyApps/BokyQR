package rocks.myburgh.bokyqr.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import rocks.myburgh.bokyqr.scanner.DecodedQr
import rocks.myburgh.bokyqr.scanner.createBarcodeDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

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

    // Geometry of the most recent decoded frame, held only so the overlay can draw a border
    // around the code. The analyzer runs on a camera-owned thread, so every update lands here
    // through the main-thread Handler.
    var tracked by remember { mutableStateOf<DecodedQr?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analyzer = remember {
        QrAnalyzer(
            onPayload = { payload -> viewModel.onPayload(payload) },
            onFrameDecoded = { decoded ->
                mainHandler.post {
                    // A stale frame that raced the sheet opening is dropped: while the sheet is
                    // up, the border belongs to the paused analyzer, not the camera.
                    if (!viewModel.sheetVisible) tracked = decoded
                }
            },
        )
    }
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
    // over and over. Pausing also drops the tracking border: the sheet is what is on screen now.
    LaunchedEffect(viewModel.sheetVisible) {
        analyzer.paused = viewModel.sheetVisible
        if (viewModel.sheetVisible) tracked = null
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
            val decoded = try {
                withContext(Dispatchers.Default) { decoder?.decodeStill(bitmap) }
            } finally {
                // Recycled whatever happened: an OutOfMemoryError here is the one case where
                // holding on to a full-size bitmap makes the next frame worse.
                bitmap.recycle()
            }
            val payload = decoded?.payload
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

        // Drawn above the preview and below the controls. The Canvas takes no pointer input, so
        // the buttons still receive their taps.
        if (hasPermission) {
            ScanOverlay(tracked)
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
                    IconButton(
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
                    ) {
                        Icon(
                            imageVector = if (torchOn) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                            contentDescription = if (torchOn) "Torch off" else "Torch on",
                            tint = Color.White,
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
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
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * Draws the tracking border over the camera preview.
 *
 * The decoder reports corners in the coordinate space of the decoded image, and the preview is
 * FILL_CENTER: the image is scaled uniformly until it covers the view and centred, so the same
 * transform is applied to each corner here. Geometry is drawn and nothing else; the payload text
 * never reaches this function.
 *
 * With no live track, a centred viewfinder is drawn instead, so the screen keeps saying "point
 * the camera at a code" rather than showing a stale border.
 */
@Composable
private fun ScanOverlay(decoded: DecodedQr?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val image = decoded
        if (image != null && image.imageWidth > 0 && image.imageHeight > 0) {
            val scale = max(size.width / image.imageWidth, size.height / image.imageHeight)
            val dx = (size.width - image.imageWidth * scale) / 2f
            val dy = (size.height - image.imageHeight * scale) / 2f
            val points = image.corners.map { Offset(dx + it.x * scale, dy + it.y * scale) }
            val path = when {
                points.size >= 3 -> Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (point in points.drop(1)) lineTo(point.x, point.y)
                    close()
                }
                // Two points or fewer is not a polygon; the caller maps a bounding box to four,
                // but a decoder is free to report fewer, so box whatever did come back.
                points.isNotEmpty() -> Path().apply {
                    addRect(
                        Rect(
                            points.minOf { it.x },
                            points.minOf { it.y },
                            points.maxOf { it.x },
                            points.maxOf { it.y },
                        ),
                    )
                }
                else -> null
            }
            if (path != null) {
                drawPath(
                    path = path,
                    color = Color(0xFF00E676),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                return@Canvas
            }
        }
        drawViewfinder()
    }
}

/**
 * A centred square of corner brackets, drawn while there is no track to outline. White rather
 * than green: it is an invitation to scan, not a result.
 */
private fun DrawScope.drawViewfinder() {
    val side = min(size.width, size.height) * 0.6f
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    val right = left + side
    val bottom = top + side
    val arm = side * 0.2f
    val path = Path().apply {
        moveTo(left, top + arm)
        lineTo(left, top)
        lineTo(left + arm, top)

        moveTo(right - arm, top)
        lineTo(right, top)
        lineTo(right, top + arm)

        moveTo(right, bottom - arm)
        lineTo(right, bottom)
        lineTo(right - arm, bottom)

        moveTo(left + arm, bottom)
        lineTo(left, bottom)
        lineTo(left, bottom - arm)
    }
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
    )
}