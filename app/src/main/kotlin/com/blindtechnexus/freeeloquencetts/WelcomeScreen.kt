package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * The title is a heading, the features are a headed list with an icon each,
 * and the rest are paragraphs, which is the whole of the structure: TalkBack
 * can jump between the headings, and everything under them is in order and
 * says one thing each. The icons carry no meaning of their own -- they are
 * silent to a screen reader and the row's own words say the thing -- so there
 * is nothing here that depends on a colour, an image or a position.
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
			Text(
				text = stringResource(R.string.welcome_features),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 4.dp)
					.semantics { heading() }
			)
			Feature(
				icon = Icons.Filled.RecordVoiceOver,
				text = stringResource(R.string.welcome_voices),
				description = stringResource(R.string.welcome_voices_description)
			)
			Feature(
				icon = Icons.Filled.Code,
				text = stringResource(R.string.welcome_code),
				description = stringResource(R.string.welcome_code_description)
			)
			Feature(
				icon = Icons.Filled.Spellcheck,
				text = stringResource(R.string.welcome_spelling),
				description = stringResource(R.string.welcome_spelling_description)
			)
			Feature(
				icon = Icons.Filled.Book,
				text = stringResource(R.string.welcome_dictionaries),
				description = stringResource(R.string.welcome_dictionaries_description)
			)
			Gap(8.dp)
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

/** One thing the app does, with its icon beside it. The icon is silent: the
 *  words are what a screen reader reads, with or without eyes on the row. */
@Composable
private fun Feature(icon: ImageVector, text: String, description: String) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			imageVector = icon,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(32.dp)
		)
		Column(modifier = Modifier.padding(start = 16.dp)) {
			Text(
				text = text,
				style = MaterialTheme.typography.bodyLarge
			)
			Text(
				text = description,
				style = MaterialTheme.typography.bodySmall
			)
		}
	}
}
