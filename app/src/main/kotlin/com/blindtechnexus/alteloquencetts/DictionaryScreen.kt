package com.blindtechnexus.alteloquencetts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The dictionary manager, which is the whole of the dictionary feature.
 *
 * A dictionary is a list of pairs: a word as it is written, and what to say
 * instead. The list is on the screen, and each word is a stop of its own that
 * says both halves. Pressing a word opens a choice between editing it and
 * taking it away. Add word opens a small form, and a whole file can be
 * brought in or written out beside it.
 *
 * Every change is written to the dictionary file at once, and the speech
 * service reads that file's own revision on the next utterance, so a word added
 * here is heard the next time anything is spoken -- no restart, no save. A word
 * matches whatever capital letters it is written in: "DOJ" and "doj" are the
 * same entry, and both are heard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(onBack: () -> Unit) {
	val context = LocalContext.current
	var words by remember { mutableStateOf(UserDictionary.words(context)) }
	var notice by remember { mutableStateOf("") }
	var adding by remember { mutableStateOf(false) }
	var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
	var editingForEdit by remember { mutableStateOf(false) }

	fun refresh() {
		words = UserDictionary.words(context)
	}

	val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) {
			val text = runCatching {
				context.contentResolver.openInputStream(uri)?.use { source ->
					Dictionaries.decode(source.readBytes())
				}.orEmpty()
			}.getOrDefault("")
			val entries = UserDictionary.parse(text)
			val taken = if (entries.isEmpty()) 0 else UserDictionary.merge(context, entries)
			refresh()
			notice = context.getString(
				if (taken > 0) R.string.dictionary_imported else R.string.dictionary_import_nothing,
				taken
			)
			Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
		}
	}
	val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
		if (uri != null) {
			val exported = runCatching {
				context.contentResolver.openOutputStream(uri)?.use { sink ->
					val body = buildString {
						for ((word, say) in words) {
							append(word)
							append('\t')
							append(say)
							append('\n')
						}
					}
					sink.write(body.toByteArray(Charsets.UTF_8))
				} != null
			}.getOrDefault(false)
			notice = if (exported) {
				context.getString(R.string.dictionary_exported, words.size)
			} else {
				context.getString(R.string.dictionary_export_failed)
			}
			Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
		}
	}

	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.dictionary_manager),
				onBack = onBack
			)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(bottom = 24.dp)
		) {
			LiveNotice(text = notice, horizontal = 16.dp)
			SectionText(text = stringResource(R.string.dictionary_screen_description))

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			) {
				Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
					Text(
						text = stringResource(R.string.dictionary_add),
						style = MaterialTheme.typography.labelLarge
					)
				}
				Gap(8.dp)
				Row(modifier = Modifier.fillMaxWidth()) {
					OutlinedButton(
						onClick = { importFile.launch(arrayOf("*/*")) },
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = stringResource(R.string.dictionary_import),
							style = MaterialTheme.typography.labelLarge
						)
					}
					Spacer(modifier = Modifier.width(8.dp))
					OutlinedButton(
						onClick = {
							exportFile.launch(
								context.getString(R.string.dictionary_export_name)
							)
						},
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = stringResource(R.string.dictionary_export),
							style = MaterialTheme.typography.labelLarge
						)
					}
				}
			}

			Gap(8.dp)
			if (words.isEmpty()) {
				Text(
					text = stringResource(R.string.dictionary_empty),
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp)
				)
			} else {
				for ((word, say) in words) {
					WordRow(
						word = word,
						say = say,
						onClick = { editing = word to say }
					)
				}
			}
		}
	}

	if (adding) {
		WordDialog(
			title = stringResource(R.string.dictionary_add),
			word = "",
			say = "",
			confirm = stringResource(R.string.dictionary_add),
			onConfirm = { word, say ->
				if (UserDictionary.add(context, word.trim(), say.trim())) {
					refresh()
					val said = context.getString(R.string.dictionary_added, word.trim())
					notice = said
					Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
				}
			},
			onDismiss = { adding = false }
		)
	}

	editing?.let { (word, say) ->
		if (editingForEdit) {
			WordDialog(
				title = stringResource(R.string.dictionary_edit),
				word = word,
				say = say,
				confirm = stringResource(R.string.save),
				onConfirm = { newWord, newSay ->
					if (UserDictionary.replace(context, word, newWord.trim(), newSay.trim())) {
						refresh()
						notice = context.getString(R.string.dictionary_updated, newWord.trim())
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					}
				},
				onDismiss = {
					editing = null
					editingForEdit = false
				}
			)
		} else {
			WordOptionsDialog(
				word = word,
				onEdit = { editingForEdit = true },
				onDelete = {
					if (UserDictionary.remove(context, word)) {
						refresh()
						notice = context.getString(R.string.dictionary_deleted, word)
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					}
					editing = null
					editingForEdit = false
				},
				onDismiss = {
					editing = null
					editingForEdit = false
				}
			)
		}
	}
}

/** One word of the dictionary: what is written, and what is said. */
@Composable
private fun WordRow(word: String, say: String, onPress: () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(role = Role.Button, onClick = onPress)
			.padding(horizontal = 16.dp, vertical = 12.dp)
			.clearAndSetSemantics {
				contentDescription = "$word, said as $say"
				role = Role.Button
				onClick {
					onPress()
					true
				}
			}
	) {
		Text(text = word, style = MaterialTheme.typography.bodyLarge)
		Text(
			text = say,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/** The form for a word and what to say instead of it. */
@Composable
private fun WordDialog(
	title: String,
	word: String,
	say: String,
	confirm: String,
	onConfirm: (String, String) -> Unit,
	onDismiss: () -> Unit
) {
	var current by remember { mutableStateOf(word) }
	var replacement by remember { mutableStateOf(say) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = title) },
		text = {
			Column {
				OutlinedTextField(
					value = current,
					onValueChange = { current = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.dictionary_word_hint)) },
					keyboardOptions = KeyboardOptions(
						capitalization = KeyboardCapitalization.None,
						autoCorrectEnabled = false,
						keyboardType = KeyboardType.Text
					),
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = replacement,
					onValueChange = { replacement = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.dictionary_say_hint)) },
					keyboardOptions = KeyboardOptions(
						capitalization = KeyboardCapitalization.None,
						autoCorrectEnabled = false,
						keyboardType = KeyboardType.Text
					),
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					if (current.isNotBlank() && replacement.isNotBlank()) {
						onConfirm(current, replacement)
						onDismiss()
					}
				}
			) {
				Text(text = confirm)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(R.string.cancel))
			}
		}
	)
}

/** What to do with one word: change it, or take it away. */
@Composable
private fun WordOptionsDialog(
	word: String,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = word) },
		text = { Text(text = stringResource(R.string.dictionary_word_choice)) },
		confirmButton = {
			TextButton(onClick = onEdit) {
				Text(text = stringResource(R.string.dictionary_edit))
			}
		},
		dismissButton = {
			TextButton(onClick = onDelete) {
				Text(text = stringResource(R.string.dictionary_delete))
			}
		}
	)
}
