package de.findusl.homebox.client

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.serializer

@OptIn(ExperimentalUuidApi::class)
class AiTools(val locationTree: List<TreeItem>, val locationsAsList: List<TreeItem>, currentLocation: MutableStateFlow<Uuid?>) {

	val setCurrentLocationTool: Tool<String, String> = object : SimpleTool<String>() {
		override suspend fun doExecute(args: String): String {
			val location = resolveLocation(args) ?: return "Could not find location $args"
			currentLocation.value = location
			return "Current location set to $args which is ID $location"
		}

		override val argsSerializer = String.serializer()
		override val description = "Set the current location of the user for subsequent commands. " +
			"Parameter is the location as slash separated path from the root 'Home/Basement/First Rack' or if the name is unique, just the location name." +
			"Will be matched case insensitive and whitespace will be ignored."
		override val name = "setCurrentLocation"
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
