package de.findusl.homebox.client

import de.findusl.wavrecorder.WavRecorderEventHandler
import io.github.aakira.napier.Napier

fun setupWavRecorderLogging() {
	WavRecorderEventHandler.INSTANCE = object : WavRecorderEventHandler {
		override fun errorWhileRecording(e: Exception) {
			Napier.e("Error while recording", e)
		}

		override fun failedToInitializeOnWindows(e: Exception) {
			Napier.e("Failed to initialize on Windows", e)
		}
	}
}
