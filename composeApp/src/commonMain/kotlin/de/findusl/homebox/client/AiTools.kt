package de.findusl.homebox.client

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@OptIn(ExperimentalUuidApi::class)
class AiTools(
	val locationTree: List<TreeItem>,
	val locationsAsList: List<TreeItem>,
	val currentLocation: MutableStateFlow<Uuid?>,
	val homeboxClient: HomeboxClient,
) {

	val setCurrentLocationTool: Tool<String, String> = object : SimpleTool<String>() {
		override suspend fun doExecute(args: String): String {
			val location = resolveLocation(args) ?: return "Could not find location $args"
			currentLocation.value = location
			Napier.i("Set current location to $location based on $args")
			return "Current location set to $args"
		}

		override val argsSerializer = String.serializer()
		override val description = "Set the current location of the user for subsequent commands. " +
			"Parameter is the location as slash separated path from the root 'Home/Basement/First Rack' or if the name is unique, just the location name." +
			"Will be matched case insensitive and whitespace will be ignored."
		override val name = "setCurrentLocation"
	}

	val createLocationTool: Tool<CreateLocationArgs, String> = object : SimpleTool<CreateLocationArgs>() {
		override suspend fun doExecute(args: CreateLocationArgs): String {
			if (args.newLocationName.contains('/')) return "New location name must not contain slashes"
			val location = resolveLocation(args.parent) ?: return "Could not find location $args"
			val result = homeboxClient.createLocation(args.newLocationName, location, args.description)
			Napier.i("Created location $result below $location (${args.parent}")
			return "Location ${args.newLocationName} created below $location"
		}

		override val argsSerializer = CreateLocationArgs.serializer()
		override val description = "Create a new location below the provided parent location."
		override val name = "createLocation"
	}

	/**
	 * Matches location case and whitespace insensitive and returns the ID of the first matching location.
	 */
	private fun resolveLocation(location: String): Uuid? {
		Napier.i("Resolving location $location")
		val normalized = location.filter(Char::isWhitespace).lowercase()
		val segments = normalized
			.split('/')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
		if (segments.isEmpty()) return null
		if (segments.size == 1) {
			Napier.i("Only one segment, trying to match location name")
			val matches = locationsAsList.filter { it.nameNormalized.equals(segments.first(), ignoreCase = true) }
			if (matches.size == 1) return matches.first().id
		}
		return resolveLocationRecursive(segments, locationTree)
	}

	private fun resolveLocationRecursive(segments: List<String>, currentNodes: List<TreeItem>): Uuid? {
		val node = currentNodes
			.filter { it.type == TreeItemType.LOCATION }
			.find { it.nameNormalized.equals(segments.first(), ignoreCase = true) }
		if (node == null) {
			Napier.i("Could not find location ${segments.first()}")
			return null
		}

		if (segments.size == 1) {
			return node.id
		}

		return resolveLocationRecursive(segments.subList(1, segments.size), node.children)
	}

	private val TreeItem.nameNormalized
		get() = name.filter(Char::isWhitespace).lowercase()
}

@Serializable
data class CreateLocationArgs(
	@property:LLMDescription("The parent location of the new location. Same format as for setCurrentLocation.")
	val parent: String,
	@property:LLMDescription("The name of the new location.")
	val newLocationName: String,
	@property:LLMDescription("Optional description of the new location.")
	val description: String? = null
)
