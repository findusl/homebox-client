package de.findusl.homebox.client

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import de.findusl.homebox.client.transcription.OpenAITranscriptionClient
import de.findusl.homebox.client.transcription.OpenAITranscriptionParams
import io.ktor.utils.io.core.readBytes
import kotlinx.io.Buffer

suspend fun transcribeAudio(buffer: Buffer, prompt: String): String {
	val apiKey = BuildKonfig.OPENAI_API_KEY

	val llmClient = OpenAITranscriptionClient(apiKey)
	val transcriptionModel = LLModel(
		provider = LLMProvider.OpenAI,
		id = "gpt-4o-mini-transcribe",
		capabilities = listOf(LLMCapability.Audio),
		contextLength = 16_000,
		maxOutputTokens = 2_000,
	)

	val content = AttachmentContent.Binary.Bytes(buffer.readBytes())
	val audio = ContentPart.Audio(content, "wav")
	val params = OpenAITranscriptionParams(prompt = prompt)
	return llmClient.transcribe(audio, params = params, transcriptionModel)
}
