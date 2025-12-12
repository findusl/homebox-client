@file:OptIn(ExperimentalUuidApi::class)
@file:UseSerializers(UuidAsStringSerializer::class)

package de.findusl.homebox.client

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class HomeboxClientImpl(
	private val httpClient: HttpClient,
	private val baseUrl: String,
	private val username: String,
	private val password: String,
) : HomeboxClient {
	private var apiToken: String? = null

	init {
		require(baseUrl.isNotBlank()) { "Homebox base URL must not be blank" }
		require(username.isNotBlank()) { "Homebox username must not be blank" }
		require(password.isNotBlank()) { "Homebox password must not be blank" }
	}

	private suspend fun HttpRequestBuilder.addAuthHeader() {
		header(HttpHeaders.Authorization, apiToken ?: login())
	}

	private suspend fun login(): String {
		// Manually encode form data to avoid charset in Content-Type
		val formData = "username=${username.encodeURLParameter()}&password=${password.encodeURLParameter()}"
		
		val response = httpClient.post("$baseUrl/api/v1/users/login") {
			header(HttpHeaders.Accept, ContentType.Application.Json)
			setBody(TextContent(formData, ContentType.parse("application/x-www-form-urlencoded")))
		}

		val loginResponse = handleErrors(response) {
			response.body<LoginResponse>()
		}
		apiToken = loginResponse.token
		return loginResponse.token
	}

	private inline fun <R> handleErrors(response: HttpResponse, block: () -> R): R {
		if (!response.status.isSuccess()) {
			Napier.e("Error while executing request: ${response.status.value} ${response.status.description}")
		}
		try {
			return block()
		} catch (e: Exception) {
			Napier.e("Error while processing response: $response", e)
			throw e
		}
	}

	override suspend fun listLocations(filterChildren: Boolean?): List<Location> {
		val response = httpClient.get("$baseUrl/api/v1/locations") {
			filterChildren?.let { parameter("filterChildren", it) }
			accept(ContentType.Application.Json)
			addAuthHeader()
		}

		return handleErrors(response) {
			response.body()
		}
	}

	override suspend fun getLocationTree(atLocation: Uuid?, withItems: Boolean): List<TreeItem> {
		val response = httpClient.get("$baseUrl/api/v1/locations/tree") {
			if (withItems) {
				parameter("withItems", true)
			}
			accept(ContentType.Application.Json)
			addAuthHeader()
		}

		val locationTree = response.body<List<TreeItem>>()
		if (atLocation != null) {
			return locationTree.flatMap { it.flatten() }.filter { it.id == atLocation }
		} else {
			return locationTree
		}
	}

	override suspend fun createLocation(
		name: String,
		parentId: Uuid?,
		description: String?,
	): LocationSummary {
		require(name.isNotBlank()) { "Location name must not be blank" }
		val response = httpClient.post("$baseUrl/api/v1/locations") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			addAuthHeader()
			setBody(
				LocationCreateRequest(
					name = name,
					description = description,
					parentId = parentId,
				),
			)
		}

		return response.body()
	}

	override suspend fun listItems(
		query: String?,
		locationIds: List<Uuid>?,
		pageSize: Int,
	): ItemPage {
		val response = httpClient.get("$baseUrl/api/v1/items") {
			parameter("pageSize", pageSize)
			query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
			locationIds?.forEach { parameter("locations", it.toString()) }
			accept(ContentType.Application.Json)
			addAuthHeader()
		}

		return response.body()
	}

	override suspend fun createItem(
		name: String,
		locationId: Uuid,
		description: String?,
	): ItemSummary {
		require(name.isNotBlank()) { "Item name must not be blank" }

		val response = httpClient.post("$baseUrl/api/v1/items") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			addAuthHeader()
			setBody(
				ItemCreateRequest(
					name = name,
					description = description,
					locationId = locationId,
				),
			)
		}

		return response.body()
	}

	override suspend fun updateItemQuantity(id: Uuid, quantity: Int) {
		httpClient.patch("$baseUrl/api/v1/items/$id") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			addAuthHeader()
			setBody(ItemPatchRequest(quantity = quantity))
		}
	}

	override suspend fun getLocation(id: Uuid): LocationDetails {
		val response = httpClient.get("$baseUrl/api/v1/locations/$id") {
			accept(ContentType.Application.Json)
			addAuthHeader()
		}

		return response.body()
	}
}

@Serializable
data class Location(
	val id: Uuid,
	val name: String,
	val description: String? = null,
	val itemCount: Int? = null,
)

@Serializable
data class LocationSummary(
	val id: Uuid,
	val name: String,
	val description: String? = null,
)

@Serializable
data class TreeItem(
	val id: Uuid,
	val name: String,
	val type: TreeItemType,
	val children: List<TreeItem> = emptyList(),
)

@Serializable
enum class TreeItemType {
	@SerialName("location")
	LOCATION,

	@SerialName("item")
	ITEM,
}

@Serializable
private data class LocationCreateRequest(
	val name: String,
	val description: String? = null,
	val parentId: Uuid? = null,
)

@Serializable
private data class ItemCreateRequest(
	val name: String,
	val description: String? = null,
	val locationId: Uuid,
)

@Serializable
private data class ItemPatchRequest(
	val quantity: Int,
)

@Serializable
data class ItemSummary(
	val id: Uuid,
	val name: String,
	val description: String? = null,
	val quantity: Int? = null,
	val location: LocationSummary? = null,
)

@Serializable
data class ItemPage(
	val items: List<ItemSummary>,
	val page: Int,
	val pageSize: Int,
	val total: Int,
)

@Serializable
data class LocationDetails(
	val id: Uuid,
	val name: String,
	val description: String? = null,
	val parent: LocationSummary? = null,
)

@Serializable
private data class LoginResponse(
	val token: String,
	val expiresAt: String,
	val attachmentToken: String,
)

object UuidAsStringSerializer : KSerializer<Uuid> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Uuid) {
		encoder.encodeString(value.toString())
	}

	override fun deserialize(decoder: Decoder): Uuid = Uuid.parse(decoder.decodeString())
}
