package rocks.myburgh.bokyqr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rocks.myburgh.bokyqr.gallery.GalleryLoader
import rocks.myburgh.bokyqr.scan.QrAnalyzer
import rocks.myburgh.bokyqr.scan.ScanViewModel
import rocks.myburgh.bokyqr.scanner.createBarcodeDecoder
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val decoder = remember { createBarcodeDecoder(context) }
    val analyzer = remember { QrAnalyzer(decoder) { payload -> viewModel.onPayload(payload) } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            executor.shutdown()
            decoder.close()
        }
    }

    LaunchedEffect(hasPermission, lifecycleOwner) {
        if (!hasPermission) return@LaunchedEffect
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
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, analyzer) }
        try {
            provider.unbindAll()
            val bound = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
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

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = GalleryLoader.load(context, uri)
            if (bitmap == null) {
                viewModel.showNotice("That image could not be read.")
                return@launch
            }
            val payload = withContext(Dispatchers.Default) { decoder.decodeStill(bitmap) }
            bitmap.recycle()
            if (payload == null) {
                viewModel.showNotice("No QR code was found in that image.")
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
                    text = "BokyQR needs the camera to scan a QR code.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (permissionRequested && !hasPermission) {
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant camera permission")
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
