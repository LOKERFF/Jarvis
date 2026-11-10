package com.jarvis.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Safety Rule 6.
 *
 * One flag that every agent loop checks. In Phase 1 the stop is triggered by
 * the on-screen STOP button or the voice command "Jarvis stop"; Phase 2 adds
 * the persistent notification action on top of the same object.
 */
object EmergencyStop {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    fun engage(note: String = "All actions stopped.") {
        _note.value = note
        _active.value = true
    }

    fun release() {
        _note.value = ""
        _active.value = false
    }
}
