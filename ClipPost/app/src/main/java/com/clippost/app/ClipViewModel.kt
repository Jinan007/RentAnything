package com.clippost.app

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

/** Holds the list of clips. It survives screen rotation. (Room will make it survive app restarts in step 6.) */
class ClipViewModel : ViewModel() {

    private val _clips = mutableStateListOf<Clip>()
    val clips: List<Clip> get() = _clips

    fun addClips(newClips: List<Clip>) {
        val known = _clips.map { it.uri }.toSet()
        _clips.addAll(newClips.filter { it.uri !in known })   // ignore clips already in the list
    }

    fun setTitle(id: String, title: String) = change(id) { it.copy(title = title.take(MAX_TITLE)) }

    fun setCaption(id: String, caption: String) = change(id) { it.copy(caption = caption.take(MAX_CAPTION)) }

    fun setPrivacy(id: String, privacy: Privacy) = change(id) { it.copy(privacy = privacy) }

    fun remove(id: String) {
        _clips.removeAll { it.id == id }
    }

    private fun change(id: String, transform: (Clip) -> Clip) {
        val index = _clips.indexOfFirst { it.id == id }
        if (index >= 0) _clips[index] = transform(_clips[index])
    }
}
