package de.findusl.homebox.client

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@OptIn(ExperimentalUuidApi::class)
class AiTools(
	val currentLocation: MutableStateFlow<Uuid?>,
	val homeboxClient: HomeboxClient,
	private val events: MutableStateFlow<PersistentList<Event>>,
) {

	val setCurrentLocationTool: Tool<String, String> = object : SimpleTool<String>() {
		override suspend fun doExecute(args: String): String {
			Napier.i("Got tool call to set current location to $args")
			val locationId = resolveLocation(args) ?: return "Could not find location $args"
			val previousLocationId = currentLocation.value
			currentLocation.value = locationId
			Napier.i("Set current location to $locationId based on $args")
			addEvent(Event.CurrentLocationSet(args, locationId, previousLocationId))
			return "Current location set to $args"
		}

		override val argsSerializer = String.serializer()
		override val description = "Set the current location of the user for subsequent commands. " +
			"Parameter is the location as slash separated path from the root 'Home/Basement/First Rack/Top Shelf' or if the name is unique, just the location name." +
			"Will be matched case insensitive and whitespace insensitive."
		override val name = "setCurrentLocation"
	}

	val createLocationTool: Tool<CreateLocationArgs, String> = object : SimpleTool<CreateLocationArgs>() {
		override suspend fun doExecute(args: CreateLocationArgs): String {
			Napier.i("Got tool call to create location ${args.name} below ${args.parentLocation} with description ${args.description ?: "none"}")
			if (args.name.contains('/')) return "New location name must not contain slashes"
			val location = resolveLocation(args.parentLocation) ?: return "Could not find location $args"
			val result = homeboxClient.createLocation(args.name, location, args.description)
			Napier.i("Created location $result below $location (${args.parentLocation}")
			addEvent(Event.LocationCreated(args.name, args.parentLocation, result.id))
			return "Location ${args.name} created below $location"
		}

		override val argsSerializer = CreateLocationArgs.serializer()
		override val description = "Create a new location below the provided parent location."
		override val name = "createLocation"
	}

	val createItemTool: Tool<CreateItemArgs, String> = object : SimpleTool<CreateItemArgs>() {
		override suspend fun doExecute(args: CreateItemArgs): String {
			Napier.i("Got tool call to create item ${args.name} below ${args.parentLocation} with description ${args.description ?: "none"} and quantity ${args.quantity}")
			val location = resolveLocation(args.parentLocation) ?: return "Could not find location $args"
			val result = homeboxClient.createItem(args.name, location, args.description)
			Napier.i("Created item $result below $location (${args.parentLocation}")
			if (args.quantity != 1) {
				homeboxClient.updateItemQuantity(result.id, args.quantity)
			}
			addEvent(Event.ItemCreated(args.name, args.parentLocation, result.id))
			return "Item ${args.name} added ${args.quantity} times below ${args.parentLocation}"
		}

		override val argsSerializer = CreateItemArgs.serializer()
		override val description = "Create a new item below the provided parent location."
		override val name = "createItem"
	}

	/**
	 * Matches location case and whitespace insensitive and returns the ID of the first matching location.
	 */
	private suspend fun resolveLocation(location: String): Uuid? {
		Napier.i("Resolving location $location")
		val normalized = location.normalize()
		val segments = normalized
			.split('/')
			.filter { it.isNotEmpty() }
		Napier.d("Resolved segments $segments")
		if (segments.isEmpty()) return null
		val locationTree = homeboxClient.getLocationTree(atLocation = currentLocation.value)
		if (segments.size == 1) {
			val locationList = locationTree.flatMap { it.flatten() }
			Napier.i("Only one segment, trying to match location name")
			val matches = locationList.filter { it.nameNormalized.equals(segments.first(), ignoreCase = true) }
			if (matches.size == 1) {
				Napier.i("Found exact match for location name $location")
				return matches.first().id
			}
		}
		val resolvedLocation = resolveLocationRecursive(segments, locationTree)
		if (resolvedLocation != null) Napier.i("Resolved location path $location")
		return resolvedLocation
	}

	private fun resolveLocationRecursive(segments: List<String>, currentNodes: List<TreeItem>): Uuid? {
		val node = currentNodes
			.filter { it.type == TreeItemType.LOCATION }
			.find { it.nameNormalized.equals(segments.first(), ignoreCase = true) }
		if (node == null) {
			Napier.i("Could not find location ${segments.first()}" + currentLocation.value?.let { "Under current location." })
			return null
		}

		if (segments.size == 1) {
			return node.id
		}

		return resolveLocationRecursive(segments.subList(1, segments.size), node.children)
	}

	private val TreeItem.nameNormalized
		get() = name.normalize()

	private fun addEvent(event: Event) {
		events.value = events.value.add(event)
	}

	private fun String.normalize() = filter { !it.isWhitespace() }.lowercase()
}

@Serializable
data class CreateLocationArgs(
	@property:LLMDescription("The parent location of the new location. Same format as for setCurrentLocation.")
	val parentLocation: String,
	@property:LLMDescription("The name of the new location.")
	val name: String,
	@property:LLMDescription("Optional description of the new location.")
	val description: String? = null
)

@Serializable
data class CreateItemArgs(
	@property:LLMDescription("The parent location of the new item. Same format as for setCurrentLocation.")
	val parentLocation: String,
	@property:LLMDescription("The name of the new item.")
	val name: String,
	@property:LLMDescription("Optional description of the new item.")
	val description: String? = null,
	@property:LLMDescription("Optional quantity of the new item. Defaults to 1.")
	val quantity: Int = 1,
)
