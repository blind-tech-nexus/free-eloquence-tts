package com.blindtechnexus.alteloquencetts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * What the launcher opens, and the three things worth doing from here.
 *
 * This is the screen somebody arrives at with a screen reader on and no idea
 * what the app is, so it is written to be listened to in order: what the app
 * is, then three things to do, each with the reason for it said before the
 * button that does it. Nothing depends on a colour, an icon or a position.
 *
 * The order of a description and its button matters. A reader swipes forward
 * one node at a time, and a button reached first is a button whose purpose has
 * to be guessed and then swiped back for, so each description is the stop
 * before the thing it describes.
 *
 * The permission at the bottom is the one the engine cannot do without, and it
 * says whether it has been given. A screen that asks once and then never
 * mentions it again leaves somebody who said no at the wrong moment with a
 * voice that stops working and nothing said about why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	exemptFromBatteryOptimization: Boolean,
	onConfigure: () -> Unit,
	onOpenSystemSpeechSettings: () -> Boolean,
	onAbout: () -> Unit,
	onRequestExemption: () -> Unit
) {
	// Resolved here rather than where it is used: the click that needs it is
	// not a composition, and a stringResource there would not compile.
	val nothingOpensSpeechSettings = stringResource(R.string.main_system_unavailable)
	var notice by remember { mutableStateOf("") }
	Scaffold(
		topBar = { ScreenTopBar(title = stringResource(R.string.app_name)) }
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
		) {
			LiveNotice(text = notice)
			Text(
				text = stringResource(R.string.main_tagline),
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 24.dp)
			)

			Step(
				description = stringResource(R.string.main_configure_description),
				action = stringResource(R.string.main_configure_action),
				onClick = onConfigure
			)
			Step(
				description = stringResource(R.string.main_system_description),
				action = stringResource(R.string.main_system_action),
				onClick = {
					if (!onOpenSystemSpeechSettings()) notice = nothingOpensSpeechSettings
				}
			)
			Step(
				description = stringResource(R.string.main_about_description),
				action = stringResource(R.string.main_about_action),
				onClick = onAbout
			)

			RequiredPermission(
				granted = exemptFromBatteryOptimization,
				onRequest = {
					notice = ""
					onRequestExemption()
				}
			)
		}
	}
}

/**
 * A reason, and then the button that acts on it.
 *
 * Two stops rather than one, which is deliberate: merging them would put the
 * description inside the button's own announcement every time it is reached,
 * and a description is worth hearing once rather than on every visit. They are
 * beside each other in the order they are read, which is what makes two stops
 * here as easy to follow as one.
 */
@Composable
private fun Step(description: String, action: String, onClick: () -> Unit) {
	Text(
		text = description,
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier.fillMaxWidth()
	)
	Gap(12.dp)
	Button(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth()
	) {
		Text(text = action, style = MaterialTheme.typography.labelLarge)
	}
	Gap(24.dp)
}

/**
 * The exemption from battery optimization, and whether it has been given.
 *
 * The state is spoken rather than left to the button's absence to imply. "The
 * button is gone" is not a sentence, and a status that can only be worked out
 * from what is missing from a screen is a status nobody who cannot see the
 * screen has. It is a live region as well, so that coming back from the
 * system's own confirmation screen says whether it worked.
 */
@Composable
private fun RequiredPermission(granted: Boolean, onRequest: () -> Unit) {
	val status = stringResource(
		if (granted) R.string.main_required_allowed else R.string.main_required_missing
	)
	SectionHeading(text = stringResource(R.string.main_required_heading), horizontal = 0.dp)
	Text(
		text = stringResource(R.string.main_required_description),
		style = MaterialTheme.typography.bodyMedium,
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 8.dp)
	)
	Text(
		text = status,
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 12.dp)
			.semantics { liveRegion = LiveRegionMode.Polite }
	)
	if (!granted) {
		Button(
			onClick = onRequest,
			modifier = Modifier.fillMaxWidth()
		) {
			Text(
				text = stringResource(R.string.main_required_action),
				style = MaterialTheme.typography.labelLarge
			)
		}
	}
}
