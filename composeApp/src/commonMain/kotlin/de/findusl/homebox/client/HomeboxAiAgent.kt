package de.findusl.homebox.client

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val PROMPT = "You help the user extend their inventory. " +
	"The Items are grouped by location and the locations are organized in a tree. " +
	"Each location can have child locations and items in it. " +
	"You can create new location, create new items in those locations or change the amount of items. " +
	"You can also set the users current location for future queries. " +
	"Your commands are transcribed and may contain mistakes. " +
	"If you cannot find a location that should exist or a command does not make sense, ask for clarification."

@OptIn(ExperimentalUuidApi::class)
class HomeboxAiAgent {
	private val homeboxClient = generateHomeboxClient()
	val currentLocation = MutableStateFlow<Uuid?>(null)
	val events = MutableStateFlow<PersistentList<Event>>(persistentListOf())

	suspend fun executeUserCommand(commandRecording: Buffer): String {
		val locationsAsName = homeboxClient.listLocations().joinToString(", ") { it.name }
		val prompt = "Possible locations included in the audio are: $locationsAsName"

		val command = transcribeAudio(commandRecording, prompt)
		Napier.i("Transcribed command: $command")

		val toolWrapper = AiTools(currentLocation, homeboxClient, events)
		val toolRegistry = ToolRegistry {
			tool(toolWrapper.setCurrentLocationTool)
			tool(toolWrapper.createLocationTool)
			tool(toolWrapper.createItemTool)
		}

		val locationTree = homeboxClient.getLocationTree(atLocation = currentLocation.value)
		val locationTreeJson = locationTree.toMinimalJson()

		val currentLocationPromptPart = currentLocation.value?.let { "The users current location is $it." } ?: ""
		var systemPrompt = "$PROMPT $currentLocationPromptPart The existing location tree looks like this: $locationTreeJson"
		if (currentLocation.value != null) {
			systemPrompt += " The current location is ${currentLocation.value}."
		}
		val agent = AIAgent(
			promptExecutor = simpleOpenAIExecutor(BuildKonfig.OPENAI_API_KEY),
			systemPrompt = systemPrompt,
			llmModel = OpenAIModels.Chat.GPT5Mini,
			// Pass your tool registry to the agent
			toolRegistry = toolRegistry
		)

		// TODO keep input and output and use as history for next call. Maybe even include them as events
		return agent.run(command)
	}
}

private fun List<TreeItem>.toMinimalJson(): String {
	val topLevelLocations = associateBy(keySelector = TreeItem::name, valueTransform = TreeItem::toMinimalJsonRecursive)
	return JsonObject(topLevelLocations).toString()
}

private fun TreeItem.toMinimalJsonRecursive(): JsonObject {
	val children = children.associateBy(keySelector = TreeItem::name, valueTransform = TreeItem::toMinimalJsonRecursive)
	return JsonObject(children)
}

private fun generateHomeboxClient(): HomeboxClient {

	val httpClient = HttpClient(CIO) {
		install(ContentNegotiation) {
			json(
				Json {
					ignoreUnknownKeys = true
				}
			)
		}
		install(Logging) {
			logger = object : Logger {
				override fun log(message: String) {
					Napier.d(message)
				}
			}
			level = LogLevel.ALL // This will log headers, body, etc.
		}
		defaultRequest {
			contentType(ContentType.Application.Json)
		}
	}

	return HomeboxClient(httpClient, BuildKonfig.HOMEBOX_BASE_URL, BuildKonfig.HOMEBOX_USERNAME, BuildKonfig.HOMEBOX_PASSWORD)
}
