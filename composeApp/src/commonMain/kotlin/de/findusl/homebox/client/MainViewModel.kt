package de.findusl.homebox.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.findusl.wavrecorder.Recorder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainViewModel(
	private val recorder: Recorder,
	private val coroutineScope: CoroutineScope,
	val homeboxAiAgent: HomeboxAiAgent = HomeboxAiAgent()
) {
	var isRecording by mutableStateOf(false)
		private set
	var isTranscribing by mutableStateOf(false)
		private set
	var transcription by mutableStateOf<String?>(null)
		private set
	var errorMessage by mutableStateOf<String?>(null)
		private set

	fun onRecordButtonClick() {
		if (!isRecording) {
			startRecording()
		} else {
			stopRecording()
		}
	}

	private fun startRecording() {
		errorMessage = null
		transcription = null
		try {
			recorder.startRecording()
			isRecording = true
		} catch (throwable: Throwable) {
			Napier.e("Failed to start recording", throwable)
			errorMessage = throwable.message ?: "Failed to start recording"
		}
	}

	private fun stopRecording() {
		coroutineScope.launch {
			isRecording = false
			isTranscribing = true
			val stopResult = recorder.stopRecording()
			transcription =
				stopResult
					.mapCatching { buffer -> homeboxAiAgent.executeUserCommand(buffer) }
					.getOrElse { throwable ->
						Napier.e("Failed to finish recording", throwable)
						errorMessage = throwable.message ?: "Failed to stop recording"
						null
					}
			isTranscribing = false
		}
	}
}
