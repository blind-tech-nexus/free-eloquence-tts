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
 * A control is one thing to stop at. A label, a bar and a number are three
 * stops for a screen reader, so each row merges into a single node that says
 * what it is, what it is set to, and what pressing or adjusting it is called.
 *
 * The numbers are percentages. The engine's own are on three scales -- gender
 * is a choice of two, speed runs to 250, the rest to a hundred -- and a bare
 * number leaves it to the listener to remember which scale they are on.
 *
 * What a setting does is said above it, and not inside it. Inflection, head
 * size and roughness are not words that carry their own meaning, so each has a
 * sentence of its own; folded into the control's announcement it would be
 * heard again on every visit, and a reader who knows what a slider is would be
 * unable to skip it.
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
	ChoiceRow(
		label = stringResource(R.string.voice_label),
		options = Eci.PRESET_NAMES.toList(),
		chosen = state.voice,
		onChoose = state::chooseVoice
	)
	ChoiceRow(
		label = stringResource(R.string.gender_label),
		options = listOf(
			stringResource(R.string.gender_male),
			stringResource(R.string.gender_female)
		),
		chosen = state.gender,
		onChoose = { state.setShape(Eci.VOICE_GENDER, it) }
	)
}

/** The seven sliders, in the order somebody tuning a voice reaches for them:
 *  how fast, then how high, then the colour of it, then how loud. */
@Composable
private fun VoiceBehaviours(state: SettingsModel) {
	SectionHeading(text = stringResource(R.string.section_behaviour))
	for (param in Settings.BEHAVIOURS) {
		SliderRow(
			label = stringResource(labelOf(param)),
			description = stringResource(descriptionOf(param)),
			percent = state.percentOf(param),
			onPercent = { state.setPercent(param, it) },
			onStep = { state.stepPercent(param, it) }
		)
	}
}

/** How the samples themselves are made. */
@Composable
private fun VoiceQuality(state: SettingsModel) {
	SectionHeading(text = stringResource(R.string.section_quality))
	SectionText(text = stringResource(R.string.sample_rate_description))
	ChoiceRow(
		label = stringResource(R.string.sample_rate_label),
		options = SettingsModel.RATES.map { stringResource(R.string.hertz, it) },
		chosen = SettingsModel.RATES.indexOf(state.sampleRateHz).coerceAtLeast(0),
		onChoose = { state.chooseSampleRate(SettingsModel.RATES[it]) }
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

/** How the engine handles what it is given: the gaps it leaves, the guesses it
 *  makes, and putting a voice back as it shipped. [onReset] is the screen
 *  above this one's for the same reason [VoiceDictionaries] takes one. */
@Composable
private fun OtherSettings(state: SettingsModel, onReset: () -> Unit) {
	SectionHeading(text = stringResource(R.string.section_other))
	ChoiceRow(
		label = stringResource(R.string.pauses_label),
		description = stringResource(R.string.pauses_description),
		options = listOf(
			stringResource(R.string.pauses_keep),
			stringResource(R.string.pauses_end),
			stringResource(R.string.pauses_all)
		),
		chosen = state.pauses,
		onChoose = state::choosePauses
	)
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

private fun labelOf(param: Int): Int = when (param) {
	Eci.VOICE_SPEED -> R.string.rate_label
	Eci.VOICE_PITCH_BASELINE -> R.string.pitch_label
	Eci.VOICE_PITCH_FLUCTUATION -> R.string.inflection_label
	Eci.VOICE_BREATHINESS -> R.string.breathiness_label
	Eci.VOICE_HEAD_SIZE -> R.string.head_size_label
	Eci.VOICE_ROUGHNESS -> R.string.roughness_label
	else -> R.string.volume_label
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

// ---- the rows -------------------------------------------------------------

/**
 * What a control is for, said above it, and then the control.
 *
 * Two stops rather than one, and the sentence is the first of them. Half the
 * settings here have names that do not carry their own meaning -- inflection,
 * head size, roughness -- and an explanation is worth hearing; folded into the
 * control's own announcement it would be heard again every single time the
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
 * A label, a bar and a number, as one thing.
 *
 * clearAndSetSemantics throws away what the label and the bar would each have
 * said and puts one control there instead: the name, the value, the range it
 * moves in, and how to move it. Without it a reader finds the text and the bar
 * separately and neither says what the other is.
 *
 * The row takes the keyboard, and the bar inside it does not. A bar that keeps
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
private fun SliderRow(
	label: String,
	description: String,
	percent: Int,
	onPercent: (Int) -> Unit,
	onStep: (Int) -> Unit
) {
	val spoken = stringResource(R.string.percent_spoken, percent)
	Described(description = description) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.onKeyEvent { press ->
					if (press.type != KeyEventType.KeyDown) return@onKeyEvent false
					when (press.key) {
						Key.DirectionLeft, Key.DirectionDown -> onStep(-1)
						Key.DirectionRight, Key.DirectionUp -> onStep(1)
						Key.MoveHome -> onPercent(0)
						Key.MoveEnd -> onPercent(Eci.PERCENT_MAX)
						else -> return@onKeyEvent false
					}
					true
				}
				.focusable()
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = spoken
					progressBarRangeInfo = ProgressBarRangeInfo(percent.toFloat(), 0f..100f, 99)
					setProgress { want ->
						val target = want.roundToInt()
						if (target != percent) onStep(if (target > percent) 1 else -1)
						true
					}
				}
		) {
			Text(
				text = stringResource(
					R.string.labelled_value,
					label,
					stringResource(R.string.percent, percent)
				),
				style = MaterialTheme.typography.labelLarge
			)
			Slider(
				modifier = Modifier.focusProperties { canFocus = false },
				value = percent.toFloat(),
				onValueChange = { onPercent(it.roundToInt()) },
				valueRange = 0f..100f,
				steps = 0
			)
		}
	}
}

/**
 * A label, what it is set to, and the list to choose from, as one control.
 *
 * The sentence on the second line is written for a finger -- "click to
 * change" -- and is not what a reader hears. What a reader hears is the row's
 * own click label, which TalkBack turns into "double tap to change", so the
 * instruction is right for whoever is listening instead of being right for
 * whoever is looking.
 */
@Composable
private fun ChoiceRow(
	label: String,
	options: List<String>,
	chosen: Int,
	onChoose: (Int) -> Unit,
	description: String? = null
) {
	var open by remember { mutableStateOf(false) }
	val now = options.getOrElse(chosen) { "" }
	val selected = stringResource(R.string.state_selected, now)
	val change = stringResource(R.string.action_change)
	Described(description = description) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.clickable(onClickLabel = change, role = Role.Button) { open = true }
				.padding(vertical = 8.dp)
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = selected
					role = Role.Button
				}
		) {
			Text(text = label, style = MaterialTheme.typography.bodyLarge)
			Text(
				text = stringResource(R.string.choice_value, now),
				style = MaterialTheme.typography.bodyMedium
			)
		}
	}
	if (open) {
		AlertDialog(
			onDismissRequest = { open = false },
			title = { Text(text = label) },
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
