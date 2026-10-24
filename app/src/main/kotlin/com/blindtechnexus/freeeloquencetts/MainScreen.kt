package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The uncluttered home screen: the four actions people return for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	onConfigure: () -> Unit,
	onOpenSystemSpeechSettings: () -> Boolean,
	onAbout: () -> Unit,
	onAudio: () -> Unit
) {
	val nothingOpensSpeechSettings = stringResource(R.string.main_system_unavailable)
	var notice by remember { mutableStateOf("") }
	Scaffold(topBar = { ScreenTopBar(title = stringResource(R.string.app_name)) }) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 12.dp)
		) {
			LiveNotice(text = notice)
			Text(
				text = stringResource(R.string.main_tagline),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.padding(bottom = 6.dp)
			)
			Text(
				text = stringResource(R.string.main_intro),
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 20.dp)
			)
			HomeAction(
				icon = Icons.Filled.RecordVoiceOver,
				title = stringResource(R.string.main_configure_action),
				description = stringResource(R.string.main_configure_description),
				onClick = onConfigure
			)
			HomeAction(
				icon = Icons.Filled.Settings,
				title = stringResource(R.string.main_system_action),
				description = stringResource(R.string.main_system_description),
				onClick = {
					if (!onOpenSystemSpeechSettings()) notice = nothingOpensSpeechSettings
				}
			)
			HomeAction(
				icon = Icons.Filled.Speaker,
				title = stringResource(R.string.main_audio_action),
				description = stringResource(R.string.main_audio_description),
				onClick = onAudio
			)
			HomeAction(
				icon = Icons.Filled.Info,
				title = stringResource(R.string.main_about_action),
				description = stringResource(R.string.main_about_description),
				onClick = onAbout
			)
		}
	}
}

@Composable
private fun HomeAction(
	icon: ImageVector,
	title: String,
	description: String,
	onClick: () -> Unit
) {
	ElevatedCard(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 12.dp)
			.clickable(onClick = onClick)
			.semantics(mergeDescendants = true) { role = Role.Button }
	) {
		Row(
			modifier = Modifier.padding(18.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(34.dp)
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
