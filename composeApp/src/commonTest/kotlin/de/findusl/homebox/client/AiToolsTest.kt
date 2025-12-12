package de.findusl.homebox.client

import dev.mokkery.mock
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

class AiToolsTest {
	@Test
	fun foo() {
		val test = AiTools(MutableStateFlow(null), mock(), MutableStateFlow(persistentListOf()))
		println(test.createLocationTool.descriptor)
	}
}
