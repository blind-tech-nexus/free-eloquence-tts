package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Where the speech is heard, chosen as one list.
 *
 * This is its own screen rather than a dialog on the settings screen because
 * it is a list of thirteen routes with a sentence each, and a dialog that long
 * is a dialog a screen reader has to walk from the top every time it opens.
 * Here the list is the screen: a heading, the promise that a route is applied
 * from the next thing spoken, and one stop per route.
 *
 * Each row is one stop. The radio button is not a separate stop -- it is
 * cleared from the semantics and the row carries the role, the selected state
 * and both sentences -- so a reader hears "Media and music, chosen, this app
 * plays every utterance on the music channel..." once and can pick it, rather
 * than meeting the same choice twice.
 *
 * The choice is written as it is made, so leaving the screen at once is not a
 * way to lose it, and the row says it is chosen before it is left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRoutingScreen(onBack: () -> Unit) {
	val context = LocalContext.current
	val settings = remember { Settings(context.applicationContext) }
	var chosen by remember { mutableStateOf(settings.synthesisRouting) }
	// The preferences are listened to rather than only written, so a screen
	// left open while something else moves them is not left holding a
	// listener for the life of the app.
	DisposableEffect(settings) {
		onDispose { settings.close() }
	}

	Scaffold(
		topBar = {
			ScreenTopBar(title = stringResource(R.string.routing_screen_title), onBack = onBack)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.selectableGroup()
				.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
		) {
			SectionHeading(text = stringResource(R.string.routing_screen_title), horizontal = 0.dp)
			SectionText(text = stringResource(R.string.routing_screen_description))
			for (route in SynthesisRouting.CHOICES) {
				RouteRow(
					route = route,
					selected = chosen == route,
					onChoose = {
						chosen = route
						settings.synthesisRouting = route
					}
				)
			}
			SectionText(text = stringResource(R.string.routing_screen_note))
		}
	}
}

/** One route: its name, what it does, and whether it is the one in force. */
@Composable
private fun RouteRow(route: Int, selected: Boolean, onChoose: () -> Unit) {
	val name = stringResource(routeNameOf(route))
	val description = stringResource(routeDescriptionOf(route))
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.selectable(
				selected = selected,
				role = Role.RadioButton,
				onClick = onChoose
			)
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		// The row is the control; the button is its picture.
		RadioButton(
			selected = selected,
			onClick = null,
			modifier = Modifier.clearAndSetSemantics {}
		)
		Column(modifier = Modifier.padding(start = 8.dp)) {
			Text(text = name, style = MaterialTheme.typography.bodyLarge)
			Text(text = description, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

/** The name of [route] as the screen offers it. */
internal fun routeNameOf(route: Int): Int = when (SynthesisRouting.clamp(route)) {
	SynthesisRouting.ACCESSIBILITY -> R.string.routing_accessibility
	SynthesisRouting.MEDIA -> R.string.routing_media
	SynthesisRouting.ALARMS -> R.string.routing_alarms
	SynthesisRouting.NOTIFICATION -> R.string.routing_notification
	SynthesisRouting.NOTIFICATION_EVENT -> R.string.routing_notification_event
	SynthesisRouting.RINGTONE -> R.string.routing_ringtone
	SynthesisRouting.NAVIGATION -> R.string.routing_navigation
	SynthesisRouting.SONIFICATION -> R.string.routing_sonification
	SynthesisRouting.VOICE_COMMUNICATION -> R.string.routing_voice_communication
	SynthesisRouting.GAME -> R.string.routing_game
	SynthesisRouting.ASSISTANT -> R.string.routing_assistant
	SynthesisRouting.ALL -> R.string.routing_all
	else -> R.string.routing_automatic
}

/** What [route] does, in one sentence. */
internal fun routeDescriptionOf(route: Int): Int = when (SynthesisRouting.clamp(route)) {
	SynthesisRouting.ACCESSIBILITY -> R.string.routing_accessibility_description
	SynthesisRouting.MEDIA -> R.string.routing_media_description
	SynthesisRouting.ALARMS -> R.string.routing_alarms_description
	SynthesisRouting.NOTIFICATION -> R.string.routing_notification_description
	SynthesisRouting.NOTIFICATION_EVENT -> R.string.routing_notification_event_description
	SynthesisRouting.RINGTONE -> R.string.routing_ringtone_description
	SynthesisRouting.NAVIGATION -> R.string.routing_navigation_description
	SynthesisRouting.SONIFICATION -> R.string.routing_sonification_description
	SynthesisRouting.VOICE_COMMUNICATION -> R.string.routing_voice_communication_description
	SynthesisRouting.GAME -> R.string.routing_game_description
	SynthesisRouting.ASSISTANT -> R.string.routing_assistant_description
	SynthesisRouting.ALL -> R.string.routing_all_description
	else -> R.string.routing_automatic_description
}
