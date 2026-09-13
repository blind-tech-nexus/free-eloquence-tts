package com.blindtechnexus.freeeloquencetts

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The one screen that is read rather than filled in, shown once on a first
 * launch and never again unless somebody asks for it.
 *
 * It is what a person meets with a screen reader already talking, so the first
 * thing it says is what the app is and the second is what it is about to ask
 * for. Two permissions come straight after this screen, one of which the
 * engine cannot work without, and a confirmation screen that arrives out of
 * nowhere is refused far more often than one that has been explained -- which
 * is a voice that stops working later, for a reason nobody was told.
 *
 * The title is a heading and the rest are paragraphs, which is the whole of
 * the structure: TalkBack can jump to the heading, and everything under it is
 * in order and says one thing each. There is nothing here that depends on a
 * colour, an image or a position on the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
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
			Text(
				text = stringResource(R.string.welcome_title),
				style = MaterialTheme.typography.headlineSmall,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 16.dp)
					.semantics { heading() }
			)
			Paragraph(text = stringResource(R.string.welcome_alternative))
			Paragraph(text = stringResource(R.string.welcome_free))
			Paragraph(text = stringResource(R.string.welcome_solution))
			Paragraph(text = stringResource(R.string.welcome_permissions))
			Paragraph(text = stringResource(R.string.welcome_about))
			Gap(16.dp)
			Button(
				onClick = onContinue,
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = stringResource(R.string.welcome_continue),
					style = MaterialTheme.typography.labelLarge
				)
			}
		}
	}
}
