package de.findusl.homebox.client

import kotlin.uuid.Uuid

interface HomeboxClient {
	suspend fun listLocations(filterChildren: Boolean? = null): List<Location>

	suspend fun getLocationTree(atLocation: Uuid? = null, withItems: Boolean = false): List<TreeItem>

	suspend fun createLocation(
		name: String,
		parentId: Uuid? = null,
		description: String? = null,
	): LocationSummary

	suspend fun listItems(
		query: String? = null,
		locationIds: List<Uuid>? = null,
		pageSize: Int = 100,
	): ItemPage

	suspend fun createItem(
		name: String,
		locationId: Uuid,
		description: String? = null,
	): ItemSummary

	suspend fun updateItemQuantity(id: Uuid, quantity: Int)

	suspend fun getLocation(id: Uuid): LocationDetails
}
