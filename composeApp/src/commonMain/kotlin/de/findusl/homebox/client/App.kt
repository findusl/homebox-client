package de.findusl.homebox.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.findusl.wavrecorder.Recorder
import de.findusl.wavrecorder.platformRecorder

@Composable
fun App() {
	setupWavRecorderLogging()
	val recorder: Recorder = remember { platformRecorder }
	val coroutineScope = rememberCoroutineScope()
	val viewModel = remember { MainViewModel(recorder, coroutineScope) }

	DisposableEffect(recorder) {
		onDispose { recorder.close() }
	}

	MaterialTheme {
		MainScreen(viewModel = viewModel)
	}
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
	val isRecording = viewModel.isRecording
	val isTranscribing = viewModel.isTranscribing
	val transcription = viewModel.transcription
	val errorMessage = viewModel.errorMessage
	val events by viewModel.homeboxAiAgent.events.collectAsState()
	val eventListState = rememberLazyListState()
	
	LaunchedEffect(events.size) {
		if (events.isNotEmpty()) {
			eventListState.animateScrollToItem(events.size - 1)
		}
	}

	Column(
		modifier =
			Modifier
				.background(MaterialTheme.colorScheme.primaryContainer)
				.safeContentPadding()
				.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		val buttonLabel =
			when {
				isRecording -> "Stop recording"
				isTranscribing -> "Transcribing..."
				else -> "Start recording"
			}

		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth(),
		) {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				state = eventListState,
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
			) {
				items(events) { event ->
					Card(
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(
							text = event.humanDescription,
							modifier = Modifier.padding(16.dp),
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}
			}
		}

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Button(
				onClick = viewModel::onRecordButtonClick,
				enabled = isRecording || !isTranscribing,
			) {
				Text(buttonLabel)
			}

			if (isRecording) {
				Text("Recording...")
			}

			Column(
				modifier = Modifier.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				transcription?.let {
					Text("Transcription")
					Text(it)
				}

				errorMessage?.let {
					Text("Error")
					Text(it)
				}
			}
		}
	}
}
