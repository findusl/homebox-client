@file:Suppress("DEPRECATION") // needed for Koog

package de.findusl.homebox.client.transcription

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.datetime.Clock

class OpenAITranscriptionClient(
	private val apiKey: String,
	private val settings: OpenAIClientSettings = OpenAIClientSettings(),
	baseClient: HttpClient = HttpClient(),
	clock: Clock = Clock.System,
) : OpenAILLMClient(apiKey, settings, baseClient, clock) {
	private val transcriptionLogger = KotlinLogging.logger {}

	// Access the underlying Ktor client for multipart requests
	private val ktorClient: HttpClient = (httpClient as KtorKoogHttpClient).ktorClient

	override suspend fun execute(
		prompt: Prompt,
		model: LLModel,
		tools: List<ToolDescriptor>,
	): List<Message.Response> {
		// Route transcription models to special handling
		if (model.supports(LLMCapability.Audio)) {
			return executeTranscription(prompt, model)
		}

		// Use normal chat completion for other models
		return super.execute(prompt, model, tools)
	}

	private suspend fun executeTranscription(prompt: Prompt, model: LLModel): List<Message.Response> {
		transcriptionLogger.debug { "Executing transcription with model: $model" }

		// Validate transcription requirements
		validateTranscriptionPrompt(prompt)

		// Extract system message as transcription prompt
		val transcriptionPrompt = extractTranscriptionPrompt(prompt)

		// Extract audio content
		val audioContent = extractAudioContent(prompt)

		// Convert params
		val transcriptionParams = OpenAITranscriptionParams(prompt = transcriptionPrompt)

		// Perform transcription
		val transcribedText = performTranscriptionRequest(
			audio = audioContent,
			params = transcriptionParams,
			model = model,
		)

		// Return as assistant message
		return listOf(
			Message.Assistant(
				content = transcribedText,
				finishReason = "stop",
				metaInfo = createMetaInfo(null), // Transcription doesn't provide usage info
			),
		)
	}

	private fun validateTranscriptionPrompt(prompt: Prompt) {
		require(prompt.messages.isNotEmpty()) { "Prompt cannot be empty for transcription" }

		// Find user message with audio
		val userMessages = prompt.messages.filterIsInstance<Message.User>()
		require(userMessages.size == 1) { "Transcription requires exactly one user message" }

		val userMessage = userMessages.first()
		val audioParts = userMessage.parts.filterIsInstance<ContentPart.Audio>()
		require(audioParts.size == 1) { "Transcription requires exactly one audio part" }

		// Validate audio content
		val audioContent = audioParts.first().content
		require(audioContent is AttachmentContent.Binary) { "Only binary audio content is supported for transcription" }
	}

	private fun extractTranscriptionPrompt(prompt: Prompt): String? {
		// Extract system message content to use as transcription prompt
		val systemMessage = prompt.messages.filterIsInstance<Message.System>().firstOrNull()
		return systemMessage?.content?.takeIf { it.isNotBlank() }
	}

	private fun extractAudioContent(prompt: Prompt): ContentPart.Audio {
		val userMessage = prompt.messages.filterIsInstance<Message.User>().first()
		return userMessage.parts.filterIsInstance<ContentPart.Audio>().first()
	}

	suspend fun transcribe(
		audio: ContentPart.Audio,
		params: OpenAITranscriptionParams = OpenAITranscriptionParams(),
		model: LLModel,
	): String {
		model.requireCapability(LLMCapability.Audio)

		// Validate params against model capabilities
		validateTranscriptionParams(params)

		// Perform transcription request
		return performTranscriptionRequest(audio, params, model)
	}

	private suspend fun performTranscriptionRequest(
		audio: ContentPart.Audio,
		params: OpenAITranscriptionParams,
		model: LLModel,
	): String {
		// Extract audio data and format
		val audioData = when (val content = audio.content) {
			is AttachmentContent.Binary -> content.asBytes()
			else -> throw IllegalArgumentException("Only binary audio content is supported")
		}

		// Use Ktor's submitFormWithBinaryData directly
		val response = ktorClient.submitFormWithBinaryData(
			url = "${settings.baseUrl}/v1/audio/transcriptions",
			formData = formData {
				append(
					"file",
					audioData,
					Headers.build {
						append(HttpHeaders.ContentType, audio.mimeType)
						append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"audio.${audio.format}\"")
					},
				)
				append("model", model.id)
				append("response_format", "text")
				params.prompt?.let { append("prompt", it) }
				params.language?.let { append("language", it) }
				params.temperature?.let { append("temperature", it.toString()) }
			},
		) {
			// Override the default JSON content type from AbstractOpenAILLMClient
			header("Authorization", "Bearer $apiKey")
		}

		// Return text response directly
		return response.bodyAsText()
	}

	private fun validateTranscriptionParams(params: OpenAITranscriptionParams) {
		// Temperature validation
		params.temperature?.let {
			require(it in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
		}
	}
}
