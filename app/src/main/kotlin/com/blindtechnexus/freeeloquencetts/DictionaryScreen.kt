package com.blindtechnexus.freeeloquencetts

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
		words = try {
			UserDictionary.words(context)
		} catch (e: Exception) {
			emptyList()
		}
	}

	val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) {
			// Run heavy work off main thread to avoid ANR and crash
			Thread {
				val entries = try {
					context.contentResolver.openInputStream(uri)?.use { input ->
						// Try stream parse first (safe for huge files), fallback to decode
						val streamEntries = UserDictionary.parseStream(input)
						if (streamEntries.isNotEmpty()) streamEntries
						else {
							// Re-open because previous parse consumed stream
							context.contentResolver.openInputStream(uri)?.use { input2 ->
								val text = Dictionaries.decode(input2.readBytes())
								UserDictionary.parse(text)
							} ?: emptyList()
						}
					} ?: emptyList()
				} catch (e: Exception) {
					emptyList()
				}
				val taken = try {
					if (entries.isEmpty()) 0 else UserDictionary.merge(context, entries)
				} catch (e: Exception) {
					0
				}
				// Back to main thread for UI
				(context as? android.app.Activity)?.runOnUiThread {
					try {
						refresh()
						notice = context.getString(
							if (taken > 0) R.string.dictionary_imported else R.string.dictionary_import_nothing,
							taken
						)
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					} catch (_: Exception) {
						Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
					}
				}
			}.apply { isDaemon = true }.start()
		}
	}
	val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
		if (uri != null) {
			Thread {
				val exported = try {
					context.contentResolver.openOutputStream(uri)?.use { sink ->
						// Stream write to avoid building huge string
						sink.bufferedWriter(Charsets.UTF_8).use { w ->
							for ((word, say) in words) {
								if (word.isEmpty() || say.isEmpty()) continue
								w.write(word.replace("\n", " ").replace("\t", " "))
								w.write('\t'.code)
								w.write(say.replace("\n", " "))
								w.write('\n'.code)
							}
						}
					}
					true
				} catch (e: Exception) {
					false
				}
				(context as? android.app.Activity)?.runOnUiThread {
					try {
						notice = if (exported) {
							context.getString(R.string.dictionary_exported, words.size)
						} else {
							context.getString(R.string.dictionary_export_failed)
						}
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					} catch (_: Exception) {
						Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
					}
				}
			}.apply { isDaemon = true }.start()
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
						onPress = { editing = word to say }
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
			.semantics(mergeDescendants = true) {
				contentDescription = "$word, said as $say"
				role = Role.Button
			}
			.padding(horizontal = 16.dp, vertical = 12.dp)
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
	val keys = KeyboardOptions(
		capitalization = KeyboardCapitalization.None,
		autoCorrect = false,
		keyboardType = KeyboardType.Text
	)
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
					keyboardOptions = keys,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = replacement,
					onValueChange = { replacement = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.dictionary_say_hint)) },
					keyboardOptions = keys,
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
