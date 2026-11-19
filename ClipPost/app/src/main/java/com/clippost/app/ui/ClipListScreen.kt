package com.clippost.app.ui

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clippost.app.AuthState
import com.clippost.app.Clip
import com.clippost.app.ClipViewModel
import com.clippost.app.CrashReporter
import com.clippost.app.MAX_CAPTION
import com.clippost.app.MAX_TITLE
import com.clippost.app.Privacy
import com.clippost.app.Quota
import com.clippost.app.UploadState
import com.clippost.app.YouTubeErrors
import com.clippost.app.captions.FacecamRegion
import com.clippost.app.displayName
import com.clippost.app.keepReadAccess
import com.clippost.app.signingSha1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipListScreen(viewModel: ClipViewModel = viewModel()) {
    val context = LocalContext.current

    // System file picker that lets us choose several videos at once.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val picked = uris.map { uri ->
            context.keepReadAccess(uri)
            val name = context.displayName(uri)
            Clip(uri = uri, fileName = name, title = name.substringBeforeLast('.').take(MAX_TITLE))
        }
        viewModel.addClips(picked)
    }

    // Google's "Allow ClipPost to upload videos?" screen.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK, result.data)
    }
    LaunchedEffect(viewModel.consentRequest) {
        viewModel.consentRequest?.let { pending ->
            consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            viewModel.consentRequestHandled()
        }
    }

    // Android 13+ needs permission to show notifications. Ask once, when the screen opens.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the uploads work either way; without it you just don't get notifications */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }       // 0 = Queue, 1 = History
    val queue = viewModel.clips.filter { it.upload !is UploadState.Done }
    val history = viewModel.clips
        .filter { it.upload is UploadState.Done }
        .sortedByDescending { it.completedAt ?: it.createdAt }
    val connected = viewModel.authState is AuthState.Connected

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("ClipPost") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Queue (${queue.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History (${history.size})") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                    Text("Add clips")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) {
                // If the app crashed last time, show why (and let the user copy it).
                var crash by remember { mutableStateOf(CrashReporter.lastCrash(context)) }
                crash?.let { report ->
                    CrashCard(
                        report = report,
                        onDismiss = {
                            CrashReporter.clear(context)
                            crash = null
                        }
                    )
                }

                val appInfo = remember { "Package: ${context.packageName}\nSHA-1: ${context.signingSha1()}" }
                ConnectCard(state = viewModel.authState, appInfo = appInfo, onConnect = viewModel::connect)
                QuotaNote(left = Quota.leftToday(viewModel.clips))

                if (queue.isEmpty()) {
                    EmptyMessage("No clips in the queue.\nTap \"Add clips\" to pick videos from your gallery.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(queue, key = { it.id }) { clip ->
                            ClipCard(
                                clip = clip,
                                canUpload = connected && clip.title.isNotBlank(),
                                onTitleChange = { viewModel.setTitle(clip.id, it) },
                                onCaptionChange = { viewModel.setCaption(clip.id, it) },
                                onPrivacyChange = { viewModel.setPrivacy(clip.id, it) },
                                onShortsChange = { viewModel.setAddShorts(clip.id, it) },
                                onCaptionsChange = { viewModel.setCaptions(clip.id, it) },
                                onVerticalChange = { viewModel.setVertical(clip.id, it) },
                                onFacecamChange = { viewModel.setFacecam(clip.id, it) },
                                onTopTextChange = { viewModel.setTopText(clip.id, it) },
                                onScheduleChange = { viewModel.setPublishAt(clip.id, it) },
                                onUpload = { viewModel.upload(clip.id) },
                                onCancel = { viewModel.cancel(clip.id) },
                                onRemove = { viewModel.remove(clip.id) }
                            )
                        }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    EmptyMessage("No uploads yet.\nVideos you upload will be listed here.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(history, key = { it.id }) { clip ->
                            HistoryCard(clip = clip, onRemove = { viewModel.remove(clip.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyMessage(text: String) {
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text)
    }
}

/** An estimate of how many uploads fit in today's YouTube quota. */
@Composable
private fun QuotaNote(left: Int) {
    val text = if (left > 0) {
        "Daily quota: about $left of ${Quota.UPLOADS_PER_DAY} uploads left today. Resets ${Quota.resetText()}."
    } else {
        "Daily quota: probably used up. It resets ${Quota.resetText()}."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (left > 0) Color.Gray else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun CrashCard(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ClipPost crashed last time", style = MaterialTheme.typography.titleSmall)
            // The first lines name the exception and its cause; Copy gives the whole report.
            Text(
                report,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy report") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun ConnectCard(state: AuthState, appInfo: String, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("YouTube", style = MaterialTheme.typography.titleSmall)
                    when (state) {
                        AuthState.Connected -> Text("Connected ✓")
                        AuthState.Connecting -> Text("Connecting…")
                        AuthState.Disconnected -> Text("Not connected")
                        is AuthState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (state != AuthState.Connected) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConnect, enabled = state != AuthState.Connecting) { Text("Connect") }
                }
            }
            if (state != AuthState.Connected) {
                // What Google sees. These two lines must match the Android OAuth client in Google Cloud.
                Text(appInfo, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipCard(
    clip: Clip,
    canUpload: Boolean,
    onTitleChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onPrivacyChange: (Privacy) -> Unit,
    onShortsChange: (Boolean) -> Unit,
    onCaptionsChange: (Boolean) -> Unit,
    onVerticalChange: (Boolean) -> Unit,
    onFacecamChange: (FacecamRegion) -> Unit,
    onTopTextChange: (String) -> Unit,
    onScheduleChange: (Long?) -> Unit,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    // Fields are locked while an upload is queued or running.
    val busy = clip.upload is UploadState.Uploading || clip.upload is UploadState.Queued ||
        clip.upload is UploadState.Processing
    val editable = !busy
    val scheduledAt = clip.publishAtMillis
    var showSchedule by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRemove, enabled = editable) { Text("Remove") }
            }

            OutlinedTextField(
                value = clip.title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                enabled = editable,
                supportingText = { Text("${clip.title.length}/$MAX_TITLE") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = clip.caption,
                onValueChange = onCaptionChange,
                label = { Text("Caption") },
                minLines = 2,
                maxLines = 5,
                enabled = editable,
                supportingText = { Text("${clip.caption.length}/$MAX_CAPTION") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Visibility", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Privacy.entries.forEach { privacy ->
                    FilterChip(
                        selected = clip.privacy == privacy,
                        onClick = { onPrivacyChange(privacy) },
                        // A scheduled video is always uploaded as Private and goes public at its time.
                        enabled = editable && scheduledAt == null,
                        label = { Text(privacy.label) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = scheduledAt != null,
                    onCheckedChange = { on -> if (on) showSchedule = true else onScheduleChange(null) },
                    enabled = editable
                )
                Spacer(Modifier.width(8.dp))
                Text(if (scheduledAt != null) "Goes public: ${formatTime(scheduledAt)}" else "Schedule publishing")
            }
            if (scheduledAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showSchedule = true }, enabled = editable) { Text("Change time") }
                }
                Text(
                    "Uploads as Private, then YouTube makes it public at that time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = clip.addShorts, onCheckedChange = onShortsChange, enabled = editable)
                Spacer(Modifier.width(8.dp))
                Text("Add #Shorts to description")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = clip.captions, onCheckedChange = onCaptionsChange, enabled = editable)
                Spacer(Modifier.width(8.dp))
                Text("Add auto captions")
            }
            if (clip.captions) {
                Text(
                    "Speech is turned into text on your phone and drawn onto the video. English only. " +
                        "The first time it downloads a speech model (about 40 MB). Takes extra time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = clip.vertical, onCheckedChange = onVerticalChange, enabled = editable)
                Spacer(Modifier.width(8.dp))
                Text("Make vertical split-screen")
            }
            if (clip.vertical) {
                Text(
                    "Rebuilds the clip as 1080 x 1920: the face cam on top, the gameplay below. " +
                        "Videos up to 3 minutes can appear in Shorts, so turn on #Shorts too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text("Where is the face cam in your clip?", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FacecamRegion.entries.forEach { region ->
                        FilterChip(
                            selected = clip.facecam == region,
                            onClick = { onFacecamChange(region) },
                            enabled = editable,
                            label = { Text(region.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = clip.topText,
                    onValueChange = onTopTextChange,
                    label = { Text("Text on the split line (empty = use the title)") },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (val state = clip.upload) {
                UploadState.Idle -> {
                    Button(onClick = onUpload, enabled = canUpload) {
                        Text(if (scheduledAt != null) "Upload and schedule" else "Upload to YouTube")
                    }
                    if (!canUpload) {
                        Text(
                            "Connect to YouTube first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                UploadState.Queued -> {
                    Text("Waiting to start… (it will wait for internet if needed)")
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }

                is UploadState.Processing -> {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${state.stage}… ${state.percent}%")
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }

                is UploadState.Uploading -> {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Uploading… ${state.percent}%")
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }

                is UploadState.Done -> {
                    // Done clips move to the History tab; this only shows for a moment.
                    Text("Uploaded ✓", style = MaterialTheme.typography.titleSmall)
                }

                is UploadState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onUpload, enabled = canUpload) { Text("Retry") }
                }
            }
        }
    }

    if (showSchedule) {
        ScheduleDialog(
            initialMillis = scheduledAt,
            onConfirm = {
                onScheduleChange(it)
                showSchedule = false
            },
            onDismiss = { showSchedule = false }
        )
    }
}

@Composable
private fun HistoryCard(clip: Clip, onRemove: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val videoId = (clip.upload as? UploadState.Done)?.videoId.orEmpty()
    val url = "https://youtu.be/$videoId"
    val scheduledAt = clip.publishAtMillis

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(clip.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            clip.completedAt?.let { Text("Uploaded ${formatTime(it)}", style = MaterialTheme.typography.bodySmall) }
            Text(
                if (scheduledAt != null) "Scheduled to go public: ${formatTime(scheduledAt)}"
                else "Visibility: ${clip.privacy.label}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(url, style = MaterialTheme.typography.bodySmall)
            // A warning, e.g. YouTube forced the video to Private because the project is not audited.
            val note = (clip.upload as? UploadState.Done)?.note
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { uriHandler.openUri(YouTubeErrors.AUDIT_HELP_URL) }) { Text("How to fix") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { uriHandler.openUri(url) }) { Text("Open on YouTube") }
                TextButton(onClick = onRemove) { Text("Remove from list") }
            }
        }
    }
}
