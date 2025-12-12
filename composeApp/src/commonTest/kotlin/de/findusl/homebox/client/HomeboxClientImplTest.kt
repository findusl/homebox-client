package de.findusl.homebox.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class HomeboxClientImplTest {
	@Test
	fun `listLocations requests and parses response`() =
		runTest {
			var capturedRequest: HttpRequestData? = null
			val locationId = TestConstants.TEST_ID_1
			val engine = MockEngine { request ->
				capturedRequest = request
				respond(
					content = ByteReadChannel(
						"""[{"id":"$locationId","name":"Kitchen","itemCount":4}]""",
					),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine) {
				install(ContentNegotiation) {
					json()
				}
			}

			val client = HomeboxClientImpl(httpClient, "https://example.test", "username", "password")

			val locations = client.listLocations()

			val request = requireNotNull(capturedRequest)
			assertEquals("/v1/locations", request.url.encodedPath)
			assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
			assertEquals(1, locations.size)
			assertEquals("Kitchen", locations.first().name)
			assertEquals(4, locations.first().itemCount)
		}

	@Test
	fun `listLocations forwards filter parameter`() =
		runTest {
			var capturedUrl: Url? = null
			val engine = MockEngine { request ->
				capturedUrl = request.url
				respond(
					content = ByteReadChannel("[]"),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine)
			val client = HomeboxClientImpl(httpClient, "https://example.test", "username", "password")

			client.listLocations(filterChildren = true)

			val url = requireNotNull(capturedUrl)
			assertEquals("true", url.parameters["filterChildren"])
		}

	@Test
	fun `getLocationTree forwards withItems parameter`() =
		runTest {
			var capturedUrl: Url? = null
			val engine = MockEngine { request ->
				capturedUrl = request.url
				respond(
					content = ByteReadChannel("[]"),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine)
			val client = HomeboxClientImpl(httpClient, "https://example.test", "username", "password")

			client.getLocationTree(withItems = true)

			val url = requireNotNull(capturedUrl)
			assertEquals("true", url.parameters["withItems"])
		}

	@Test
	fun `listItems requests and parses response`() =
		runTest {
			var capturedRequest: HttpRequestData? = null
			val itemId = TestConstants.TEST_ID_1
			val locationId = TestConstants.TEST_ID_2
			val engine = MockEngine { request ->
				capturedRequest = request
				respond(
					content = ByteReadChannel(
"""{"items":[{"id":"$itemId","name":"Hammer","quantity":2,"description":"Steel","location":{"id":"$locationId","name":"Shelf A"}}],"page":1,"pageSize":100,"total":1}""",
					),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine) {
				install(ContentNegotiation) { json() }
			}

			val client = HomeboxClientImpl(httpClient, "https://example.test", "username", "password")

			val page = client.listItems(pageSize = 100)

			val request = requireNotNull(capturedRequest)
			assertEquals("/v1/items", request.url.encodedPath)
			assertEquals("100", request.url.parameters["pageSize"])
			assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
			assertEquals(1, page.items.size)
			assertEquals("Hammer", page.items.first().name)
			assertEquals("Shelf A", page.items.first().location?.name)
			assertEquals(1, page.total)
		}

	@Test
	fun `listItems forwards location filters`() =
		runTest {
			var capturedUrl: Url? = null
			val engine = MockEngine { request ->
				capturedUrl = request.url
				respond(
					content = ByteReadChannel("""{"items":[],"page":1,"pageSize":100,"total":0}"""),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine)
			val client = HomeboxClientImpl(httpClient, "https://example.test", "username", "password")

			val locationId1 = TestConstants.TEST_ID_1
			val locationId2 = TestConstants.TEST_ID_2
			client.listItems(locationIds = listOf(locationId1, locationId2))

			val url = requireNotNull(capturedUrl)
			assertEquals(listOf(locationId1.toString(), locationId2.toString()), url.parameters.getAll("locations"))
		}

	@Test
	fun `getLocation fetches details`() =
		runTest {
			var capturedRequest: HttpRequestData? = null
			val locationId = TestConstants.TEST_ID_1
			val parentId = TestConstants.TEST_ID_2
			val engine = MockEngine { request ->
				capturedRequest = request
				respond(
					content = ByteReadChannel(
						"""{"id":"$locationId","name":"Shelf A","parent":{"id":"$parentId","name":"Home"}}""",
					),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine)
			val client = HomeboxClientImpl(httpClient, "https://example.test", "username", "password")

			val location = client.getLocation(locationId)

			val request = requireNotNull(capturedRequest)
			assertEquals("/v1/locations/$locationId", request.url.encodedPath)
			assertEquals("Shelf A", location.name)
			assertEquals(parentId, location.parent?.id)
		}
}
