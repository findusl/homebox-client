package de.findusl.homebox.client

import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer

interface HomeboxAiAgent {
	val currentLocation: MutableStateFlow<Uuid?>
	val events: MutableStateFlow<PersistentList<Event>>

	suspend fun executeUserCommand(commandRecording: Buffer): String
}
