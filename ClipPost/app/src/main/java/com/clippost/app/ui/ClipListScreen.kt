package com.clippost.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clippost.app.Clip
import com.clippost.app.ClipViewModel
import com.clippost.app.MAX_CAPTION
import com.clippost.app.MAX_TITLE
import com.clippost.app.Privacy
import com.clippost.app.displayName
import com.clippost.app.keepReadAccess

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("ClipPost") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                Text("Add clips")
            }
        }
    ) { padding ->
        if (viewModel.clips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No clips yet.\nTap \"Add clips\" to pick videos from your gallery.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.clips, key = { it.id }) { clip ->
                    ClipCard(
                        clip = clip,
                        onTitleChange = { viewModel.setTitle(clip.id, it) },
                        onCaptionChange = { viewModel.setCaption(clip.id, it) },
                        onPrivacyChange = { viewModel.setPrivacy(clip.id, it) },
                        onRemove = { viewModel.remove(clip.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipCard(
    clip: Clip,
    onTitleChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onPrivacyChange: (Privacy) -> Unit,
    onRemove: () -> Unit
) {
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
                TextButton(onClick = onRemove) { Text("Remove") }
            }

            OutlinedTextField(
                value = clip.title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                supportingText = { Text("${clip.title.length}/$MAX_TITLE") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = clip.caption,
                onValueChange = onCaptionChange,
                label = { Text("Caption") },
                minLines = 2,
                maxLines = 5,
                supportingText = { Text("${clip.caption.length}/$MAX_CAPTION") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Visibility", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Privacy.entries.forEach { privacy ->
                    FilterChip(
                        selected = clip.privacy == privacy,
                        onClick = { onPrivacyChange(privacy) },
                        label = { Text(privacy.label) }
                    )
                }
            }
        }
    }
}
