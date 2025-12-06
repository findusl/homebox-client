package de.findusl.homebox.client

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalUuidApi::class)
class HomeboxAiAgent {
	private val homeboxClient = generateHomeboxClient()
	val currentLocation = MutableStateFlow<Uuid?>(null)
	val completedOperations = MutableStateFlow<PersistentList<String>>(persistentListOf())

	suspend fun executeUserCommand(commandRecording: Buffer): String {
		val locationTree = currentLocation.value?.let { homeboxClient.getTreeAtLocation(it) } ?: homeboxClient.getLocationTree()

		val locationList = locationTree.flatMap { it.flatten() }
		val locationsAsName = locationList.joinToString(", ") { it.name }
		val prompt = "Possible locations included in the audio are: $locationsAsName"

		val command = transcribeAudio(commandRecording, prompt)

		val toolWrapper = AiTools(locationTree, locationList, currentLocation, homeboxClient)
		val toolRegistry = ToolRegistry {
			tool(toolWrapper.setCurrentLocationTool)
		}

		val locationTreeJson = locationTree.toMinimalJson()

		var systemPrompt = "You help the user set their current location. The possible locations are: $locationTreeJson."
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

fun TreeItem.flatten(): List<TreeItem> =
	listOf(this) + children.flatMap { it.flatten() }

@OptIn(ExperimentalUuidApi::class)
suspend fun HomeboxClient.getTreeAtLocation(locationId: Uuid): List<TreeItem> =
	getLocationTree(false).filter { it.id == locationId }

private fun generateHomeboxClient(): HomeboxClient {

	val httpClient = HttpClient(CIO) {
		this.install(ContentNegotiation) {
			json()
		}
		defaultRequest {
			contentType(ContentType.Application.Json)
		}
	}

	return HomeboxClient(httpClient, BuildKonfig.HOMEBOX_BASE_URL, BuildKonfig.HOMEBOX_API_TOKEN)
}
