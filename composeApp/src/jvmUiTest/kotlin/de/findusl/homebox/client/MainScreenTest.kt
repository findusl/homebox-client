package de.findusl.homebox.client

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.findusl.wavrecorder.Recorder
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainScreenTest {
	@get:Rule
	val composeTestRule = createComposeRule()

	private val mockRecorder: Recorder = mock()
	private val testScope: TestScope = TestScope()
	private val mockHomeboxAiAgent: HomeboxAiAgent = mock {
		// Mock the events flow to return empty list
		every { events } returns kotlinx.coroutines.flow.MutableStateFlow(kotlinx.collections.immutable.persistentListOf())
	}

	@Test
	fun mainScreen_showsOnlyRecordButtonInitially() {
		// Arrange
		val viewModel = MainViewModel(mockRecorder, testScope, mockHomeboxAiAgent)

		// Act
		composeTestRule.setContent {
			MainScreen(viewModel = viewModel)
		}

		// Wait for the UI to stabilize and check record button exists
		composeTestRule.waitUntilAtLeastOneExists(
			hasText("Start recording", substring = false),
		)

		// Assert - Record button is visible
		composeTestRule
			.onNodeWithText("Start recording")
			.assertIsDisplayed()

		// Assert - Recording status is not shown
		composeTestRule
			.onNodeWithText("Recording...")
			.assertDoesNotExist()

		// Assert - Transcription is not shown
		composeTestRule
			.onNodeWithText("Transcription")
			.assertDoesNotExist()

		// Assert - Error message is not shown
		composeTestRule
			.onNodeWithText("Error")
			.assertDoesNotExist()
	}
}
