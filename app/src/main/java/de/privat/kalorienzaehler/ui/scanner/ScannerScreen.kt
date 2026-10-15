package de.privat.kalorienzaehler.ui.scanner

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.barcode.Barcode
import de.privat.kalorienzaehler.domain.barcode.BarcodeError
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.ui.diary.FullScreenScaffold
import de.privat.kalorienzaehler.ui.diary.dateLabel
import de.privat.kalorienzaehler.ui.diary.label
import de.privat.kalorienzaehler.ui.navigation.ScannerRoute
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.SectionTitle

/** Whether the scanner may use the camera (PROMPT.md 2.4: asked for in context, with a rationale; the app works without). */
enum class CameraAccess {
    /** Permission granted: the camera runs. */
    AVAILABLE,

    /** Not granted yet (or denied once): explain, then ask. */
    ASK,

    /** Denied for good: only the system settings can change that. */
    DENIED,

    /** No camera in this device. */
    NO_CAMERA,
}

@Composable
fun ScannerScreen(
    route: ScannerRoute,
    onBack: () -> Unit,
    onFoodFound: (FoodKey) -> Unit,
    onCreateCustom: (barcode: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel<ScannerViewModel, ScannerViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    ),
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ScannerEvent.Detected -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                is ScannerEvent.FoodFound -> onFoodFound(event.key)
            }
        }
    }
    val permission = rememberCameraPermission()
    ScannerContent(
        subtitle = stringResource(R.string.add_subtitle, viewModel.meal.label(), dateLabel(viewModel.date, viewModel.today)),
        access = permission.access,
        phase = viewModel.phase,
        torchOn = viewModel.torchOn,
        torchAvailable = viewModel.torchAvailable,
        manualText = viewModel.manualText,
        manualError = viewModel.manualError,
        onBack = onBack,
        onRequestPermission = permission.request,
        onOpenSettings = permission.openSettings,
        onCameraCode = viewModel::onCameraCode,
        onTorchAvailable = viewModel::onTorchAvailable,
        onToggleTorch = viewModel::toggleTorch,
        onManualTextChange = viewModel::onManualTextChange,
        onManualSearch = viewModel::submitManual,
        onScanAgain = viewModel::scanAgain,
        onCreateCustom = onCreateCustom,
        modifier = modifier,
    )
}

/** The camera permission as the screen needs it. */
class CameraPermission(val access: CameraAccess, val request: () -> Unit, val openSettings: () -> Unit)

@Composable
private fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        asked = true
    }
    // Coming back from the system settings, where the permission may have been switched on.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose { }
    }
    val activity = context.findActivity()
    val access = when {
        !hasCamera -> CameraAccess.NO_CAMERA
        granted -> CameraAccess.AVAILABLE
        // After a refusal Android only shows the dialog again if it wants a rationale shown; if not, it is final.
        asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
            CameraAccess.DENIED
        else -> CameraAccess.ASK
    }
    return CameraPermission(
        access = access,
        request = { launcher.launch(Manifest.permission.CAMERA) },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ScannerContent(
    subtitle: String,
    access: CameraAccess,
    phase: ScannerPhase,
    torchOn: Boolean,
    torchAvailable: Boolean,
    manualText: String,
    manualError: BarcodeError?,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCameraCode: (text: String, format: de.privat.kalorienzaehler.domain.barcode.BarcodeFormat) -> Unit,
    onTorchAvailable: (Boolean) -> Unit,
    onToggleTorch: () -> Unit,
    onManualTextChange: (String) -> Unit,
    onManualSearch: () -> Unit,
    onScanAgain: () -> Unit,
    onCreateCustom: (barcode: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FullScreenScaffold(
        title = stringResource(R.string.scanner_title),
        subtitle = subtitle,
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (access) {
                CameraAccess.AVAILABLE -> CameraArea(
                    scanning = phase == ScannerPhase.Scanning,
                    torchOn = torchOn,
                    torchAvailable = torchAvailable,
                    onCameraCode = onCameraCode,
                    onTorchAvailable = onTorchAvailable,
                    onToggleTorch = onToggleTorch,
                )
                CameraAccess.ASK -> InfoCard(
                    title = stringResource(R.string.scanner_permission_title),
                    text = stringResource(R.string.scanner_permission_rationale),
                    actionLabel = stringResource(R.string.scanner_permission_allow),
                    onAction = onRequestPermission,
                )
                CameraAccess.DENIED -> InfoCard(
                    title = null,
                    text = stringResource(R.string.scanner_permission_denied),
                    actionLabel = stringResource(R.string.scanner_permission_settings),
                    onAction = onOpenSettings,
                )
                CameraAccess.NO_CAMERA -> InfoCard(title = null, text = stringResource(R.string.scanner_no_camera))
            }

            when (phase) {
                ScannerPhase.Scanning -> Unit
                is ScannerPhase.Looking -> LookingRow(phase.barcode)
                is ScannerPhase.NotFound -> NotFoundCard(
                    phase = phase,
                    showScanAgain = access == CameraAccess.AVAILABLE,
                    onScanAgain = onScanAgain,
                    onCreateCustom = { onCreateCustom(phase.barcode.code) },
                )
            }

            ManualEntry(
                text = manualText,
                error = manualError,
                enabled = phase !is ScannerPhase.Looking,
                onTextChange = onManualTextChange,
                onSearch = onManualSearch,
            )
        }
    }
}

@Composable
private fun CameraArea(
    scanning: Boolean,
    torchOn: Boolean,
    torchAvailable: Boolean,
    onCameraCode: (String, de.privat.kalorienzaehler.domain.barcode.BarcodeFormat) -> Unit,
    onTorchAvailable: (Boolean) -> Unit,
    onToggleTorch: () -> Unit,
) {
    val cameraDescription = stringResource(R.string.scanner_camera_description)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = cameraDescription },
    ) {
        LocalBarcodeCamera.current.Preview(
            scanning = scanning,
            torchOn = torchOn,
            onTorchAvailable = onTorchAvailable,
            onCode = onCameraCode,
            modifier = Modifier.fillMaxSize(),
        )
        ScanFrame(modifier = Modifier.fillMaxSize())
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.scanner_aim_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (torchAvailable) {
            FilledTonalIconButton(
                onClick = onToggleTorch,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(if (torchOn) R.drawable.ic_flashlight_on_24 else R.drawable.ic_flashlight_off_24),
                    // The label says what a tap does.
                    contentDescription = stringResource(if (torchOn) R.string.scanner_torch_off else R.string.scanner_torch_on),
                )
            }
        }
    }
}

/** A dimmed picture with a clear window for the barcode and corner marks: purely a visual aid, the whole frame is read. */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        modifier = modifier
            .clearAndSetSemantics { }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val frameWidth = size.width * 0.82f
        val frameHeight = frameWidth * 0.55f
        val topLeft = Offset((size.width - frameWidth) / 2f, (size.height - frameHeight) / 2f)
        val frameSize = Size(frameWidth, frameHeight)
        val corner = CornerRadius(16.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.45f))
        drawRoundRect(Color.Black, topLeft, frameSize, corner, blendMode = BlendMode.Clear)
        drawRoundRect(accent, topLeft, frameSize, corner, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun InfoCard(title: String?, text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun LookingRow(barcode: Barcode) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(stringResource(R.string.scanner_looking, barcode.code), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NotFoundCard(
    phase: ScannerPhase.NotFound,
    showScanAgain: Boolean,
    onScanAgain: () -> Unit,
    onCreateCustom: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.scanner_not_found_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.scanner_not_found_body, phase.barcode.code), style = MaterialTheme.typography.bodyLarge)
            if (phase.barcode.isInStoreCode) HintText(stringResource(R.string.scanner_in_store_hint))
            if (phase.databaseUnavailable) HintText(stringResource(R.string.scanner_database_unavailable))
            Button(onClick = onCreateCustom, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_create_custom))
            }
            if (showScanAgain) {
                OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scanner_scan_again))
                }
            }
        }
    }
}

@Composable
private fun ManualEntry(
    text: String,
    error: BarcodeError?,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.scanner_manual_title))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.scanner_manual_label)) },
            isError = error != null,
            supportingText = error?.let { { Text(errorText(it)) } },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSearch, enabled = enabled && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scanner_manual_search))
        }
    }
}

@Composable
private fun errorText(error: BarcodeError): String = stringResource(
    when (error) {
        BarcodeError.NOT_DIGITS -> R.string.scanner_error_digits
        BarcodeError.WRONG_LENGTH -> R.string.scanner_error_length
        // An empty field has no message: the button stays disabled.
        BarcodeError.WRONG_CHECK_DIGIT, BarcodeError.EMPTY -> R.string.scanner_error_check_digit
    },
)
