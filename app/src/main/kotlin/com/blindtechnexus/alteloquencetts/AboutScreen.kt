package com.blindtechnexus.alteloquencetts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * What the app is, who made it, and how to reach them.
 *
 * This is the screen a person is sent to when something is wrong, so the three
 * ways to say so are buttons and not addresses to copy down: WhatsApp for a
 * bug or a feature request, Telegram for the news, and email for an answer
 * that can be relied on. Each says what it is for before the button that does
 * it, in the order a reader meets them.
 *
 * A link that cannot be followed is said out loud. These addresses belong to
 * apps that may not be installed and a watch may have none of them, so what
 * happens when nothing opens is a sentence in a live region rather than a
 * button that quietly did nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
	val context = LocalContext.current
	val subject = stringResource(R.string.about_email_subject)
	// All of what a click might have to say is resolved here rather than
	// there: the click is not a composition, and a stringResource inside it
	// would not compile.
	val noWhatsApp = stringResource(
		R.string.about_unavailable, stringResource(R.string.about_whatsapp_action)
	)
	val noTelegram = stringResource(
		R.string.about_unavailable, stringResource(R.string.about_telegram_action)
	)
	val noEmail = stringResource(
		R.string.about_unavailable, stringResource(R.string.about_email_action)
	)
	var notice by remember { mutableStateOf("") }
	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.about_title),
				onBack = onBack
			)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
		) {
			LiveNotice(text = notice)

			Paragraph(text = stringResource(R.string.about_app))
			Paragraph(text = stringResource(R.string.about_accessibility))
			Paragraph(text = stringResource(R.string.about_free))
			Paragraph(text = stringResource(R.string.about_unofficial))

			SectionHeading(text = stringResource(R.string.about_developer_heading), horizontal = 0.dp)
			Paragraph(text = stringResource(R.string.about_developer))
			Paragraph(text = stringResource(R.string.about_team))

			SectionHeading(text = stringResource(R.string.about_contact_heading), horizontal = 0.dp)
			Contact(
				description = stringResource(R.string.about_contact_whatsapp),
				action = stringResource(R.string.about_whatsapp_action),
				onOpen = { ExternalLinks.whatsApp(context) },
				onRefused = { notice = noWhatsApp }
			)
			Contact(
				description = stringResource(R.string.about_contact_telegram),
				action = stringResource(R.string.about_telegram_action),
				onOpen = { ExternalLinks.telegram(context) },
				onRefused = { notice = noTelegram }
			)
			Contact(
				description = stringResource(R.string.about_contact_email),
				action = stringResource(R.string.about_email_action),
				onOpen = { ExternalLinks.email(context, subject) },
				onRefused = { notice = noEmail }
			)

			SectionHeading(text = stringResource(R.string.about_updates_heading), horizontal = 0.dp)
			Paragraph(text = stringResource(R.string.about_updates))
			// What the last answer was, said as a sentence rather than as a bar:
			// the state of the check is a fact about the app, and the only way
			// anybody without sight can be told it is in words. It is put above
			// the button that asks so that the answer is read first, which is the
			// order a person wants it in when they have just asked.
			Paragraph(text = updateState())
			ActionRow(
				label = stringResource(R.string.about_update_check),
				description = stringResource(R.string.about_update_check_description),
				onClick = { Updater.check(force = true) }
			)

			SectionHeading(text = stringResource(R.string.about_build_heading), horizontal = 0.dp)
			Paragraph(text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
		}
	}
}

/**
 * A way to reach us: what it is for, then the button, then the next one.
 *
 * [onRefused] is called when nothing on the device could take the link, which
 * is the case worth announcing rather than the one that worked.
 */
@Composable
private fun Contact(
	description: String,
	action: String,
	onOpen: () -> Boolean,
	onRefused: () -> Unit
) {
	Text(
		text = description,
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier.fillMaxWidth()
	)
	Gap(12.dp)
	OutlinedButton(
		onClick = { if (!onOpen()) onRefused() },
		modifier = Modifier.fillMaxWidth()
	) {
		Text(text = action, style = MaterialTheme.typography.labelLarge)
	}
	Gap(24.dp)
}

/**
 * What is known about the newest version, in one sentence.
 *
 * The dialog is where an update is offered, and it is not up by the time a
 * person reaches this screen: so the row that asks again has to answer in its
 * own words, and those words have to be the same facts the dialog was built on
 * rather than a second reading of them. Everything here comes from
 * [Updater.state] and [Updater.notice], which are the one place either of them
 * is kept.
 */
@Composable
private fun updateState(): String {
	// Asked for by hand, answered by word of mouth: a button that leaves the
	// sentence above it exactly as it was is a button that was not pressed, as
	// far as anybody who cannot see the screen can tell. So the words the last
	// manual ask found take precedence over the standing state, which would
	// otherwise say "up to date" in the same breath it said it before.
	return when (Updater.notice) {
		Updater.Notice.UP_TO_DATE ->
			stringResource(R.string.about_update_answer_current, BuildConfig.VERSION_NAME)
		Updater.Notice.UNREACHABLE ->
			stringResource(R.string.about_update_answer_unreachable)
		Updater.Notice.NONE -> standingState()
	}
}

/** What the last check found, in a sentence, when nobody has just asked. */
@Composable
private fun standingState(): String = when (val now = Updater.state) {
	Updater.State.Idle -> stringResource(R.string.about_update_never_asked)
	Updater.State.Checking -> stringResource(R.string.about_update_checking)
	Updater.State.UpToDate -> stringResource(R.string.about_update_current, BuildConfig.VERSION_NAME)
	Updater.State.Unreachable -> stringResource(R.string.about_update_unreachable)
	Updater.State.Failed -> stringResource(R.string.update_failed)
	is Updater.State.Available -> stringResource(R.string.about_update_found, now.version)
	is Updater.State.Downloading -> if (now.total > 0) {
		stringResource(R.string.update_progress_of, megabytes(now.done), megabytes(now.total))
	} else {
		stringResource(R.string.update_progress_so_far, megabytes(now.done))
	}
	is Updater.State.Ready -> stringResource(R.string.update_ready_title)
	Updater.State.Dismissed -> stringResource(R.string.about_update_dismissed)
}
