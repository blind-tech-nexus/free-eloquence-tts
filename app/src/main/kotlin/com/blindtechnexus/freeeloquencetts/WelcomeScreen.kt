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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
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

/** A concise first-run introduction: six useful reasons to use the app. */
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
				.padding(horizontal = 20.dp, vertical = 12.dp)
		) {
			Text(
				text = stringResource(R.string.welcome_title),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier
					.fillMaxWidth()
					.semantics { heading() }
			)
			Text(
				text = stringResource(R.string.welcome_intro),
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
			)
			WelcomeFeature(
				Icons.Filled.RecordVoiceOver,
				stringResource(R.string.welcome_voices),
				stringResource(R.string.welcome_voices_description)
			)
			WelcomeFeature(
				Icons.Filled.Language,
				stringResource(R.string.welcome_languages),
				stringResource(R.string.welcome_languages_description)
			)
			WelcomeFeature(
				Icons.Filled.Speaker,
				stringResource(R.string.welcome_fast),
				stringResource(R.string.welcome_fast_description)
			)
			WelcomeFeature(
				Icons.Filled.Pin,
				stringResource(R.string.welcome_codes),
				stringResource(R.string.welcome_codes_description)
			)
			WelcomeFeature(
				Icons.Filled.Code,
				stringResource(R.string.welcome_code),
				stringResource(R.string.welcome_code_description)
			)
			WelcomeFeature(
				Icons.Filled.Book,
				stringResource(R.string.welcome_private),
				stringResource(R.string.welcome_private_description)
			)
			Text(
				text = stringResource(R.string.welcome_permissions),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
			)
			Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
				Text(stringResource(R.string.welcome_continue))
			}
		}
	}
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, description: String) {
	ElevatedCard(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 10.dp)
	) {
		Row(
			modifier = Modifier.padding(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(32.dp)
			)
			Column(modifier = Modifier.padding(start = 16.dp)) {
				Text(title, style = MaterialTheme.typography.titleMedium)
				Text(
					description,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 3.dp)
				)
			}
		}
	}
}
