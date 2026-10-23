package dev.kyouzo.imageconverter.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kyouzo.imageconverter.engine.ImagePickerManager
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.vm.ConverterViewModel
import dev.kyouzo.imageconverter.vm.OutputOption
import dev.kyouzo.imageconverter.vm.Phase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(vm: ConverterViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> vm.onImagePicked(uri) }

    // Legacy (API < 29) storage permission used only for saving on old devices.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.save() }

    // "All files access" settings screen (Android 11+) for deleting the original.
    val manageAllFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (!vm.needsAllFilesAccess()) vm.performOriginalDeletion() else vm.onDeletePermissionDenied()
    }

    // WRITE permission for deleting on API <= 29.
    val deleteWritePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.performOriginalDeletion() else vm.onDeletePermissionDenied() }

    fun startDeletionFlow() {
        when {
            vm.needsAllFilesAccess() -> manageAllFilesLauncher.launch(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
            )
            else -> {
                val wp = vm.writePermissionForDelete()
                if (wp != null && ContextCompat.checkSelfPermission(context, wp) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    deleteWritePermLauncher.launch(wp)
                } else {
                    vm.performOriginalDeletion()
                }
            }
        }
    }

    fun launchPicker() = pickLauncher.launch(ImagePickerManager.imageOnlyRequest())

    fun onSaveClicked() {
        if (vm.requiresLegacyStoragePermission()) {
            val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
            val granted = ContextCompat.checkSelfPermission(context, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) vm.save() else permissionLauncher.launch(perm)
        } else {
            vm.save()
        }
    }

    // Surface transient messages via the snackbar.
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { scope.launch { snackbar.showSnackbar(it) }; vm.consumeError() }
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { scope.launch { snackbar.showSnackbar(it) }; vm.consumeSavedMessage() }
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { scope.launch { snackbar.showSnackbar(it) }; vm.consumeInfo() }
    }
    // After a successful save, ensure file-delete access (All files access on R+,
    // or WRITE on older), then delete the original.
    LaunchedEffect(state.requestDeletionAfterSave) {
        if (state.requestDeletionAfterSave) {
            vm.consumeDeletionTrigger()
            startDeletionFlow()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Converter", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.phase) {
                Phase.EMPTY -> EmptyState(onSelect = ::launchPicker)
                Phase.LOADING -> LoadingState("Reading image…")
                else -> {
                    SourceCard(
                        state = state,
                        onReplace = ::launchPicker,
                        onClear = vm::clearSelection,
                    )
                    if (state.phase == Phase.RESULT) {
                        ResultCard(
                            state = state,
                            onSave = ::onSaveClicked,
                            onShare = {
                                vm.buildShareIntent()?.let { context.startActivity(it) }
                            },
                            onConvertAnother = vm::convertAnother,
                        )
                    } else {
                        OutputSettings(
                            state = state,
                            onTarget = vm::onTargetSelected,
                            onQuality = vm::onQualityChanged,
                            onBackground = vm::onBackgroundColorChanged,
                            onUnavailable = { opt ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        opt.capability.reason ?: "${opt.format.displayName} is not available"
                                    )
                                }
                            },
                        )
                        ConvertButton(
                            enabled = state.canConvert && state.phase == Phase.READY,
                            converting = state.phase == Phase.CONVERTING,
                            onClick = vm::convert,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmptyState(onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Convert images on your device",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Pick a photo, choose an output format, and convert — everything stays on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Select Image", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun LoadingState(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(label, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SourceCard(
    state: dev.kyouzo.imageconverter.vm.ConverterUiState,
    onReplace: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val preview = state.previewBitmap
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "Selected image preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("No preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                state.source?.displayName ?: "image",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            MetaRow("Format", state.source?.detectedFormat?.displayName ?: "Unknown")
            MetaRow("Dimensions", formatDimensions(state.sourceWidth, state.sourceHeight))
            MetaRow("File size", formatFileSize(state.source?.sizeBytes ?: 0))
            if (state.sourceHasAlpha) MetaRow("Transparency", "Yes")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReplace, modifier = Modifier.weight(1f)) { Text("Replace") }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutputSettings(
    state: dev.kyouzo.imageconverter.vm.ConverterUiState,
    onTarget: (ImageFormat) -> Unit,
    onQuality: (Int) -> Unit,
    onBackground: (Int) -> Unit,
    onUnavailable: (OutputOption) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Output format", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.outputOptions.forEach { opt ->
                    FilterChip(
                        selected = state.target == opt.format && opt.enabled,
                        onClick = { if (opt.enabled) onTarget(opt.format) else onUnavailable(opt) },
                        enabled = true, // keep tappable so we can explain why it's unavailable
                        label = {
                            Text(
                                opt.format.displayName + if (!opt.enabled) " ✕" else "",
                                color = if (opt.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
            Text(
                "Formats marked ✕ cannot be encoded on this device — tap to see why.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.qualityApplicable) {
                HorizontalDivider()
                QualityControl(state.quality, onQuality)
            }

            if (state.needsBackgroundChoice) {
                HorizontalDivider()
                BackgroundControl(state.backgroundColor, onBackground)
            }
        }
    }
}

@Composable
private fun QualityControl(quality: Int, onQuality: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Quality", style = MaterialTheme.typography.titleMedium)
            Text("$quality", fontWeight = FontWeight.Medium)
        }
        androidx.compose.material3.Slider(
            value = quality.toFloat(),
            onValueChange = { onQuality(it.toInt()) },
            valueRange = 1f..100f,
        )
    }
}

@Composable
private fun BackgroundControl(selected: Int, onBackground: (Int) -> Unit) {
    val swatches = listOf(
        "White" to 0xFFFFFFFF.toInt(),
        "Black" to 0xFF000000.toInt(),
        "Gray" to 0xFFBDBDBD.toInt(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Background for transparency (JPEG has no alpha)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            swatches.forEach { (name, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected == color) 3.dp else 1.dp,
                                color = if (selected == color) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { onBackground(color) },
                    )
                    Text(name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ConvertButton(enabled: Boolean, converting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !converting,
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        if (converting) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(12.dp))
            Text("Converting…", style = MaterialTheme.typography.titleMedium)
        } else {
            Text("Convert Image", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ResultCard(
    state: dev.kyouzo.imageconverter.vm.ConverterUiState,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onConvertAnother: () -> Unit,
) {
    val result = state.result ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Conversion complete",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val rp = state.resultPreview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (rp != null) {
                    Image(rp, "Converted image preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text(
                        "${result.format.displayName} preview not available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            MetaRowOn("File", result.fileName)
            MetaRowOn("Format", result.format.displayName)
            MetaRowOn("Dimensions", formatDimensions(result.width, result.height))
            MetaRowOn("Size", formatFileSize(result.sizeBytes))

            Text(
                state.deleteStatus
                    ?: "Saving will delete the original photo from your device.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSave, enabled = !state.originalDeleted, modifier = Modifier.weight(1f)) { Text("Save") }
                FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
            }
            OutlinedButton(onClick = onConvertAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Convert Another")
            }
        }
    }
}

@Composable
private fun MetaRowOn(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
