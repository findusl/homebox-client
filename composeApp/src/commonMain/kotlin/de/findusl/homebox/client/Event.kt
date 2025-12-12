package de.findusl.homebox.client

import kotlin.uuid.Uuid

sealed interface Event {
	val humanDescription: String

	data class CurrentLocationSet(val location: String, val locationId: Uuid, val previousLocationId: Uuid?) : Event, Undoable {
		override val humanDescription: String = "Current location set to $location"
	}

	data class LocationCreated(val locationName: String, val parentLocation: String, val locationId: Uuid) : Event, Undoable {
		override val humanDescription: String = "Location $locationName created in $parentLocation"
	}

	data class ItemCreated(val itemName: String, val parentLocation: String, val itemId: Uuid) : Event, Undoable {
		override val humanDescription: String = "Item $itemName created in $parentLocation"
	}

	interface Undoable
}
