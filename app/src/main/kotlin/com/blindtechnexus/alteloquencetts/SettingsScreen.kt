package com.blindtechnexus.alteloquencetts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Every setting the engine has, in the six sections they belong to, and a way
 * to hear the lot of them.
 *
 * Four things about it are deliberate and easy to undo by accident.
 *
 * A control is one thing to stop at. A row that is a button carries its own
 * name, so nothing is cleared or merged on top of it; a row that is a bar
 * inside a dialog merges into one node that says what it is, what it is set
 * to, and what adjusting it does. Either way a reader meets one stop and not
 * three.
 *
 * The numbers are percentages. The engine's own are on three scales -- gender
 * is a choice of two, speed runs to 250, the rest to a hundred -- and a bare
 * number leaves it to the listener to remember which scale they are on.
 *
 * What a setting does is said above it, and not inside it. Inflection, head
 * size and roughness are not words that carry their own meaning, so each has
 * a sentence of its own; folded into the control's announcement it would be
 * heard again on every visit, and a reader who knows what a setting is would
 * be unable to skip it.
 *
 * The sections are headings, which is what makes a screen this long navigable
 * rather than merely long: TalkBack moves from heading to heading, so the six
 * sections are six jumps and the controls under them are reached from the
 * section that was asked for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsModel, onBack: () -> Unit) {
	val context = LocalContext.current
	var wanted by remember { mutableStateOf(Eci.DICT_MAIN) }
	var notice by remember { mutableStateOf("") }
	val refused = stringResource(R.string.dictionary_refused)
	val removed = stringResource(R.string.dictionary_removed)
	val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) {
			// Said out loud, and said whether it worked. The row's own value
			// changes, but a change to a node nobody is focused on is a change
			// nobody hears, and a file the engine could not read leaves the row
			// exactly as it was: to a finger, a pick that did nothing and a
			// pick that taught the engine three thousand words are the same
			// event unless one of them is announced.
			val taken = state.chooseDictionary(wanted, uri)
			notice = if (taken) {
				context.getString(
					R.string.labelled_value,
					context.getString(dictionaryLabelOf(wanted)),
					state.dictionaryName(wanted)
				)
			} else {
				refused
			}
		}
	}
	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.settings_title),
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
			VoiceCustomization(state)
			VoiceBehaviours(state)
			VoiceQuality(state)
			VoiceDictionaries(
				state = state,
				onPick = { volume ->
					wanted = volume
					pick.launch(arrayOf("*/*"))
				},
				onRemove = {
					state.removeDictionaries()
					notice = removed
				}
			)
			OtherSettings(
				state = state,
				onReset = {
					state.resetVoice()
					notice = context.getString(
						R.string.voice_reset,
						Eci.PRESET_NAMES.getOrElse(state.voice) { "" }
					)
				}
			)
			TestVoice(state)
		}
	}
}

// ---- the six sections -----------------------------------------------------

/** Which voice speaks, and whether it is a man or a woman. */
@Composable
private fun VoiceCustomization(state: SettingsModel) {
	SectionHeading(text = stringResource(R.string.section_voice))
	SectionText(text = stringResource(R.string.section_voice_description))
	val voices = Eci.PRESET_NAMES.toList()
	ChoiceButton(
		title = stringResource(R.string.voice_label),
		shown = stringResource(
			R.string.value_selected,
			stringResource(R.string.voice_label),
			voices.getOrElse(state.voice) { "" }
		),
		options = voices,
		chosen = state.voice,
		onChoose = state::chooseVoice
	)
	val genders = listOf(
		stringResource(R.string.gender_male),
		stringResource(R.string.gender_female)
	)
	ChoiceButton(
		title = stringResource(R.string.gender_label),
		shown = stringResource(
			R.string.value_selected,
			stringResource(R.string.gender_label),
			genders.getOrElse(state.gender) { "" }
		),
		options = genders,
		chosen = state.gender,
		onChoose = { state.setShape(Eci.VOICE_GENDER, it) }
	)
}

/**
 * The seven numeric parameters, in the order somebody tuning a voice reaches
 * for them: how fast, then how high, then the colour of it, then how loud.
 * Each is a button that opens the setting in a dialog of its own, and the
 * rate, the pitch and the volume each carry a switch that says whose number
 * is in force when another app asks for speech: this app's, or the asker's.
 */
@Composable
private fun VoiceBehaviours(state: SettingsModel) {
	SectionHeading(text = stringResource(R.string.section_behaviour))
	for (param in Settings.BEHAVIOURS) {
		ParamButton(
			label = stringResource(setActionOf(param)),
			title = stringResource(dialogTitleOf(param)),
			description = stringResource(descriptionOf(param)),
			percent = state.percentOf(param),
			onPercent = { state.setPercent(param, it) }
		)
		when (param) {
			Eci.VOICE_SPEED -> SwitchRow(
				label = stringResource(R.string.force_over),
				description = stringResource(R.string.force_rate_description),
				checked = state.forceRate,
				onChange = state::chooseForceRate
			)
			Eci.VOICE_PITCH_BASELINE -> SwitchRow(
				label = stringResource(R.string.force_over),
				description = stringResource(R.string.force_pitch_description),
				checked = state.forcePitch,
				onChange = state::chooseForcePitch
			)
			Eci.VOICE_VOLUME -> SwitchRow(
				label = stringResource(R.string.force_over),
				description = stringResource(R.string.force_volume_description),
				checked = state.forceVolume,
				onChange = state::chooseForceVolume
			)
		}
	}
}

/** How the samples themselves are made. */
@Composable
private fun VoiceQuality(state: SettingsModel) {
	SectionHeading(text = stringResource(R.string.section_quality))
	val rates = SettingsModel.RATES.map { stringResource(R.string.hertz, it) }
	val chosen = SettingsModel.RATES.indexOf(state.sampleRateHz).coerceAtLeast(0)
	ChoiceButton(
		title = stringResource(R.string.sample_rate_label),
		shown = stringResource(
			R.string.value_selected,
			stringResource(R.string.sample_rate_label),
			rates.getOrElse(chosen) { "" }
		),
		options = rates,
		chosen = chosen,
		onChoose = { state.chooseSampleRate(SettingsModel.RATES[it]) },
		description = stringResource(R.string.sample_rate_description)
	)
}

/** The three pronunciation dictionaries, and the abbreviation switch that
 *  belongs with them rather than with the voice.
 *
 *  [onRemove] belongs to the screen above this one, because clearing all three
 *  is something that has to be said out loud: three rows change to "None" and
 *  nobody is focused on any of them when the button is pressed. */
@Composable
private fun VoiceDictionaries(
	state: SettingsModel,
	onPick: (Int) -> Unit,
	onRemove: () -> Unit
) {
	SectionHeading(text = stringResource(R.string.section_dictionaries))
	SectionText(text = stringResource(R.string.dictionaries_description))
	for (volume in Eci.DICT_VOLUMES) {
		FileRow(
			label = stringResource(dictionaryLabelOf(volume)),
			value = state.dictionaryName(volume),
			onClick = { onPick(volume) }
		)
	}
	ActionRow(
		label = stringResource(R.string.dictionary_remove),
		description = stringResource(R.string.dictionary_remove_description),
		onClick = onRemove
	)
	SwitchRow(
		label = stringResource(R.string.abbreviations_label),
		description = stringResource(R.string.abbreviations_description),
		checked = state.abbreviations,
		onChange = state::chooseAbbreviations
	)
}

/** How the engine handles what it is given: the gaps it leaves, the symbols
 *  it names, the guesses it makes, and putting a voice back as it shipped.
 *  [onReset] is the screen above this one's for the same reason
 *  [VoiceDictionaries] takes one. */
@Composable
private fun OtherSettings(state: SettingsModel, onReset: () -> Unit) {
	SectionHeading(text = stringResource(R.string.section_other))
	val pauseModes = listOf(
		stringResource(R.string.pauses_keep),
		stringResource(R.string.pauses_end),
		stringResource(R.string.pauses_all)
	)
	ChoiceButton(
		title = stringResource(R.string.pauses_label),
		shown = stringResource(
			R.string.value_selected,
			stringResource(R.string.pauses_label),
			pauseModes.getOrElse(state.pauses) { "" }
		),
		options = pauseModes,
		chosen = state.pauses,
		onChoose = state::choosePauses,
		description = stringResource(R.string.pauses_description)
	)
	var symbolsOpen by remember { mutableStateOf(false) }
	Described(description = stringResource(R.string.symbol_description)) {
		ValueButton(
			shown = stringResource(
				R.string.mode_value,
				stringResource(R.string.symbol_mode_label),
				stringResource(nameOf(state.symbolMode))
			),
			onClick = { symbolsOpen = true }
		)
	}
	if (symbolsOpen) {
		SymbolDialog(state = state, onDismiss = { symbolsOpen = false })
	}
	SwitchRow(
		label = stringResource(R.string.phrase_prediction_label),
		description = stringResource(R.string.phrase_prediction_description),
		checked = state.phrasePrediction,
		onChange = state::choosePhrasePrediction
	)
	ActionRow(
		label = stringResource(R.string.reset_label),
		description = stringResource(R.string.reset_description),
		onClick = onReset
	)
}

/** Hearing it. Nothing on this screen speaks by itself, so this is the only
 *  way to know what a change did. */
@Composable
private fun TestVoice(state: SettingsModel) {
	SectionHeading(text = stringResource(R.string.section_test))
	SectionText(text = stringResource(R.string.test_description))
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
	) {
		Button(onClick = state::say, modifier = Modifier.fillMaxWidth()) {
			Text(
				text = stringResource(R.string.speak_label),
				style = MaterialTheme.typography.labelLarge
			)
		}
		Gap(12.dp)
		// Worth its own button rather than being left to the screen reader
		// interrupting itself: this preview speaks into an AudioTrack of its
		// own, which nothing else stops.
		OutlinedButton(onClick = state::stopSpeaking, modifier = Modifier.fillMaxWidth()) {
			Text(
				text = stringResource(R.string.stop_label),
				style = MaterialTheme.typography.labelLarge
			)
		}
	}
}

// ---- which word goes with which parameter ---------------------------------

private fun setActionOf(param: Int): Int = when (param) {
	Eci.VOICE_SPEED -> R.string.set_rate
	Eci.VOICE_PITCH_BASELINE -> R.string.set_pitch
	Eci.VOICE_PITCH_FLUCTUATION -> R.string.set_inflection
	Eci.VOICE_BREATHINESS -> R.string.set_breathiness
	Eci.VOICE_HEAD_SIZE -> R.string.set_head_size
	Eci.VOICE_ROUGHNESS -> R.string.set_roughness
	else -> R.string.set_volume
}

private fun dialogTitleOf(param: Int): Int = when (param) {
	Eci.VOICE_SPEED -> R.string.dialog_rate_title
	Eci.VOICE_PITCH_BASELINE -> R.string.dialog_pitch_title
	Eci.VOICE_PITCH_FLUCTUATION -> R.string.dialog_inflection_title
	Eci.VOICE_BREATHINESS -> R.string.dialog_breathiness_title
	Eci.VOICE_HEAD_SIZE -> R.string.dialog_head_size_title
	Eci.VOICE_ROUGHNESS -> R.string.dialog_roughness_title
	else -> R.string.dialog_volume_title
}

private fun descriptionOf(param: Int): Int = when (param) {
	Eci.VOICE_SPEED -> R.string.rate_description
	Eci.VOICE_PITCH_BASELINE -> R.string.pitch_description
	Eci.VOICE_PITCH_FLUCTUATION -> R.string.inflection_description
	Eci.VOICE_BREATHINESS -> R.string.breathiness_description
	Eci.VOICE_HEAD_SIZE -> R.string.head_size_description
	Eci.VOICE_ROUGHNESS -> R.string.roughness_description
	else -> R.string.volume_description
}

private fun dictionaryLabelOf(volume: Int): Int = when (volume) {
	Eci.DICT_MAIN -> R.string.dictionary_main
	Eci.DICT_ROOT -> R.string.dictionary_root
	else -> R.string.dictionary_abbreviation
}

private fun nameOf(mode: Int): Int = when (mode) {
	Symbols.ALL -> R.string.symbol_mode_all
	Symbols.MOST -> R.string.symbol_mode_most
	Symbols.SOME -> R.string.symbol_mode_some
	Symbols.CUSTOM -> R.string.symbol_mode_custom
	else -> R.string.symbol_mode_none
}

private fun symbolDescriptionOf(mode: Int): Int = when (mode) {
	Symbols.ALL -> R.string.symbol_all_description
	Symbols.MOST -> R.string.symbol_most_description
	Symbols.SOME -> R.string.symbol_some_description
	Symbols.CUSTOM -> R.string.symbol_custom_description
	else -> R.string.symbol_none_description
}

// ---- the rows and the dialogs ---------------------------------------------

/**
 * What a control is for, said above it, and then the control.
 *
 * Two stops rather than one, and the sentence is the first of them. Half the
 * settings here have names that do not carry their own meaning -- inflection,
 * head size, roughness -- and an explanation is worth hearing; folded into
 * the control's own announcement it would be heard again every single time the
 * control is reached, and there would be no way past it.
 */
@Composable
private fun Described(description: String?, control: @Composable () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
	) {
		// let rather than a null check: no smart cast to be lost inside a
		// composable lambda, and nothing composed at all when there is no
		// description, so the empty stop is not there either.
		description?.let { said ->
			Text(
				text = said,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 4.dp)
			)
		}
		control()
	}
}

/**
 * A button that carries its own value: "Voice (Reed) selected", "Symbol
 * pronunciation mode (Some)".
 *
 * The label says what the setting is and what it is set to, because a button
 * whose name is only "Change" or "Choose" is a button whose setting has to be
 * guessed at. What the button does is said as well, and said in words that
 * are right for whoever is listening: the state carries "Click to change",
 * which a screen reader reads out between the label and the button's own
 * "double tap to activate", so nothing about the row depends on being able to
 * see it.
 */
@Composable
private fun ValueButton(shown: String, onClick: () -> Unit) {
	val change = stringResource(R.string.action_click_to_change)
	OutlinedButton(
		onClick = onClick,
		modifier = Modifier
			.fillMaxWidth()
			.semantics {
				contentDescription = shown
				stateDescription = change
			}
	) {
		Text(text = shown, style = MaterialTheme.typography.labelLarge)
	}
}

/**
 * A button that opens one of the numeric settings in a dialog of its own.
 *
 * The bar lives in the dialog rather than on the screen, so the screen is a
 * list of things to change rather than a wall of numbers, and the change is
 * Saved or Cancelled rather than made on the way past. [onPercent] is called
 * once, by Save.
 */
@Composable
private fun ParamButton(
	label: String,
	title: String,
	description: String,
	percent: Int,
	onPercent: (Int) -> Unit
) {
	var open by remember { mutableStateOf(false) }
	Described(description = description) {
		OutlinedButton(
			onClick = { open = true },
			modifier = Modifier.fillMaxWidth()
		) {
			Text(text = label, style = MaterialTheme.typography.labelLarge)
		}
	}
	if (open) {
		SliderDialog(
			title = title,
			percent = percent,
			onPercent = onPercent,
			onDismiss = { open = false }
		)
	}
}

/**
 * A label, a bar and a number, as one thing, inside the dialog that owns it.
 *
 * clearAndSetSemantics throws away what the label and the bar would each have
 * said and puts one control there instead: the name, the value, the range it
 * moves in, and how to move it. Without it a reader finds the text and the bar
 * separately and neither says what the other is.
 *
 * The bar takes the keyboard and the bar inside it does not. A bar that keeps
 * a focus of its own is a second place for the keyboard to be, invisible to a
 * screen reader because the semantics here are the row's: tabbing lands on a
 * bar nothing announces, and its own answer to the End key is to go to the
 * maximum, so a setting moves with nothing said about it. So the bar is taken
 * out of the tab order and the row answers the keys itself.
 *
 * A key press moves it by one. TalkBack adjusts any slider by a twentieth of
 * the range it is given, so its own arrow key asks for five percent here
 * whatever the steps say, and the number of steps is not part of what the
 * platform is told. Five percent of the speed scale is the difference between
 * too slow to bear and faster than was wanted, so a request to move is read as
 * a request to move one rather than as the number it carries.
 */
@Composable
private fun SliderDialog(
	title: String,
	percent: Int,
	onPercent: (Int) -> Unit,
	onDismiss: () -> Unit
) {
	var chosen by remember { mutableStateOf(percent) }
	val spoken = stringResource(R.string.percent_spoken, chosen)
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = title) },
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.onKeyEvent { press ->
						if (press.type != KeyEventType.KeyDown) return@onKeyEvent false
						when (press.key) {
							Key.DirectionLeft, Key.DirectionDown -> {
								chosen = (chosen - 1).coerceIn(0, Eci.PERCENT_MAX)
								true
							}
							Key.DirectionRight, Key.DirectionUp -> {
								chosen = (chosen + 1).coerceIn(0, Eci.PERCENT_MAX)
								true
							}
							Key.MoveHome -> {
								chosen = 0
								true
							}
							Key.MoveEnd -> {
								chosen = Eci.PERCENT_MAX
								true
							}
							else -> false
						}
					}
					.focusable()
					.clearAndSetSemantics {
						contentDescription = title
						stateDescription = spoken
						progressBarRangeInfo = ProgressBarRangeInfo(chosen.toFloat(), 0f..100f, 99)
						setProgress { want ->
							val target = want.roundToInt()
							if (target != chosen) {
								chosen = (chosen + if (target > chosen) 1 else -1)
									.coerceIn(0, Eci.PERCENT_MAX)
							}
							true
						}
					}
			) {
				Text(
					text = stringResource(R.string.percent, chosen),
					style = MaterialTheme.typography.labelLarge,
					modifier = Modifier.fillMaxWidth()
				)
				Slider(
					modifier = Modifier.focusProperties { canFocus = false },
					value = chosen.toFloat(),
					onValueChange = { chosen = it.roundToInt() },
					valueRange = 0f..100f,
					steps = 0
				)
			}
		},
		confirmButton = {
			TextButton(onClick = {
				onPercent(chosen)
				onDismiss()
			}) {
				Text(text = stringResource(R.string.save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(R.string.cancel))
			}
		}
	)
}

/**
 * A setting that is a choice, as a button that opens the list of them. The
 * value is in the button's own label, the list is radio buttons with the
 * chosen one marked, and choosing one of them applies it at once and closes.
 */
@Composable
private fun ChoiceButton(
	title: String,
	shown: String,
	options: List<String>,
	chosen: Int,
	onChoose: (Int) -> Unit,
	description: String? = null
) {
	var open by remember { mutableStateOf(false) }
	Described(description = description) {
		ValueButton(shown = shown, onClick = { open = true })
	}
	if (open) {
		AlertDialog(
			onDismissRequest = { open = false },
			title = { Text(text = title) },
			text = {
				Column(modifier = Modifier.selectableGroup()) {
					options.forEachIndexed { at, option ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.selectable(
									selected = at == chosen,
									onClick = {
										onChoose(at)
										open = false
									},
									role = Role.RadioButton
								)
								.padding(vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							RadioButton(selected = at == chosen, onClick = null)
							Text(
								text = option,
								modifier = Modifier.padding(start = 12.dp),
								style = MaterialTheme.typography.bodyLarge
							)
						}
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { open = false }) {
					Text(text = stringResource(R.string.cancel))
				}
			}
		)
	}
}

/**
 * The symbol pronunciation list, with the custom symbols under it.
 *
 * Every level has its sentence beside it, because "Most" on its own does not
 * say most of what. Choosing Custom keeps the list open rather than applying
 * at once, since what Custom means is still to be written down, and nothing is
 * in force until Save: the mode and the list are saved together or not at all.
 */
@Composable
private fun SymbolDialog(state: SettingsModel, onDismiss: () -> Unit) {
	var picked by remember { mutableStateOf(state.symbolMode) }
	var symbols by remember { mutableStateOf(state.customSymbols) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = stringResource(R.string.symbol_label)) },
		text = {
			Column(
				modifier = Modifier
					.selectableGroup()
					.verticalScroll(rememberScrollState())
			) {
				Symbols.MODES.forEach { mode ->
					val chosen = picked == mode
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.selectable(
								selected = chosen,
								onClick = { picked = mode },
								role = Role.RadioButton
							)
							.padding(vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						RadioButton(selected = chosen, onClick = null)
						Column(modifier = Modifier.padding(start = 12.dp)) {
							Text(
								text = stringResource(nameOf(mode)),
								style = MaterialTheme.typography.bodyLarge
							)
							Text(
								text = stringResource(symbolDescriptionOf(mode)),
								style = MaterialTheme.typography.bodySmall
							)
						}
					}
				}
				if (picked == Symbols.CUSTOM) {
					OutlinedTextField(
						value = symbols,
						onValueChange = { symbols = it },
						singleLine = true,
						label = { Text(text = stringResource(R.string.symbol_custom_hint)) },
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 8.dp)
					)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = {
				state.chooseSymbolMode(picked)
				state.chooseCustomSymbols(symbols)
				onDismiss()
			}) {
				Text(text = stringResource(R.string.save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(R.string.cancel))
			}
		}
	)
}

/** A label, a switch, and the state of it, as one control. */
@Composable
private fun SwitchRow(
	label: String,
	checked: Boolean,
	onChange: (Boolean) -> Unit,
	description: String? = null
) {
	val state = stringResource(if (checked) R.string.switch_on else R.string.switch_off)
	Described(description = description) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { onChange(!checked) }
				.padding(vertical = 8.dp)
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = state
					role = Role.Switch
				},
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.weight(1f)
			)
			Switch(checked = checked, onCheckedChange = null)
		}
	}
}

/**
 * A label, the file that has been chosen for it, and a way to choose another.
 *
 * The value is what the row is called when it speaks: a file's own name and
 * how many words are in it, or "None". Both are worth more than a tick, which
 * is what a picker usually shows and which says nothing at all out loud.
 */
@Composable
private fun FileRow(label: String, value: String, onClick: () -> Unit) {
	val choose = stringResource(R.string.action_choose_file)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			// Clickable before the padding, so that the padding is part of what
			// a finger can hit rather than a margin around it.
			.clickable(onClickLabel = choose, role = Role.Button, onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 12.dp)
			.clearAndSetSemantics {
				contentDescription = label
				stateDescription = value
				role = Role.Button
			}
	) {
		Text(text = label, style = MaterialTheme.typography.bodyLarge)
		Text(
			text = stringResource(R.string.file_value, value),
			style = MaterialTheme.typography.bodyMedium
		)
	}
}

/** Something to press, with what it does said above it. A button of its own
 *  rather than a row that behaves like one, so that its label needs no
 *  semantics of ours to be announced as one. */
@Composable
private fun ActionRow(label: String, description: String?, onClick: () -> Unit) {
	Described(description = description) {
		OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
			Text(text = label, style = MaterialTheme.typography.labelLarge)
		}
	}
}
