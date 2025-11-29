package de.findusl.homebox.client.transcription

data class OpenAITranscriptionParams(
	val prompt: String? = null,
	val language: String? = null,
	val temperature: Double? = null,
)
