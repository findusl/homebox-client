package de.findusl.homebox.client

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalUuidApi::class)
class AiToolsTest {

	@Test
	fun foo() {
		val test = AiTools(MutableStateFlow(null), HomeboxClient(HttpClient(), "http://localhost:8080", "token"), MutableStateFlow(kotlinx.collections.immutable.persistentListOf()))
		println(test.createLocationTool.descriptor)
	}
}
