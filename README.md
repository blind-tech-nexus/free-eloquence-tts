# Alt eloquence TTS

Alt eloquence TTS is a free alternative Eloquence text-to-speech engine for Android and Wear OS.

The speech comes from [openevv](https://github.com/Mudb0y/openevv), a C rebuild of IBM's Embedded ViaVoice. This repo cross-compiles that engine for Android, wraps it in JNI, and puts a `TextToSpeechService` around it. openevv is a submodule and is not edited in place.

## Unofficial

A hobby project. Nobody's product. Not from IBM, Nuance, Cerence, Google or the openevv project, and none of them have endorsed or reviewed it.

It is not a supported screen reader engine. If you set it as your TalkBack voice and it stops talking, you have to undo that yourself, so read "Turning it off" before you turn it on.

No warranty. See LICENSE, and read the part about language data before redistributing anything.

## What works

* US English.
* Eight preset voices: Reed, Shelley, Bobby, Rocko, Glen, Sandy, Grandma, Grandpa. Offered through the Android voice API, so they appear separately.
* All eight voice parameters: speed, pitch, inflection, head size, roughness, breathiness, volume, gender.
* Speech rate and pitch from the system settings, with a switch to force this app's own over them.
* Symbol pronunciation: all, most, some, a custom list, or none.
* Pronunciation dictionaries, and a user dictionary file.
* A notification that keeps the engine in the foreground whether or not anything is bound to it, a stop button on it, and the same after a restart for whoever wants it: see [Keeping the voice alive](#keeping-the-voice-alive-when-nothing-is-speaking).
* An update channel, checked in the background on every launch, that ends at Android's own installer: see [Updates](#updates).
* Android 6 and later. arm64-v8a, armeabi-v7a, x86_64.

`app/src/androidTest` runs on a device: 150 test methods, of which the ones that matter most are the ones that never start a sound.

## Turning it on

TalkBack settings, Text-to-speech settings, Preferred engine, Alt eloquence TTS. The gear next to it opens the voice settings.

## Turning it off

TalkBack settings, Text-to-speech settings, Preferred engine, Google.

## Building

Needs the Android SDK, NDK r23 or later, JDK 17, Python 3 and bash. No Android Studio, and the wrapper fetches its own Gradle.

```
git submodule update --init --recursive
export ANDROID_NDK_HOME=/path/to/ndk
./gradlew installDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleDebugAndroidTest
```

The middle line needs a phone. The last one does not: it compiles the instrumented
tests against the app and stops there, which is the half of the check that can be
done where no device is plugged in -- and it is worth doing, because a test that
has never been compiled is a test that has never been read.

First build is about four minutes per ABI. Most of that is the engine writing the language rules out of text with Python and decompiling them into roughly 13 MB of C, which is per language rather than per ABI, so only the first ABI pays for it.

| property | default | effect |
| --- | --- | --- |
| `evvdroid.abis` | `arm64-v8a,armeabi-v7a,x86_64` | which ABIs to build |
| `evvdroid.rules` | `c` | `bytecode` builds in 30s and runs at under half the speed |
| `evvdroid.langs` | `lang/enus` | language modules to link in |
| `evvdroid.abiSplits` | `false` | one APK per ABI |

`-Pevvdroid.abis=arm64-v8a -Pevvdroid.rules=bytecode` is the fast loop for working on the Kotlin. Compiled rules are the default because interrupting and restarting costs about a third of what it costs interpreted, and a screen reader interrupts constantly.

A fix to openevv that upstream has not taken yet goes in `native/patches` and is applied at build time. There are none, so the submodule builds as upstream left it.

## Screens

Four, in one activity. A screen reader announces the departure and the arrival of every activity, an engine preview opened for the settings screen would be closed and reopened by a trip to the about screen, and a back stack of four activities is four things to be somewhere inside when the phone answers. So the screens are a state, the back key moves it, and "Go back" in the title bar does what the back key does.

*Welcome*, once, on a first launch: what the app is, and what it is about to ask for.

*Main*, which is what the launcher opens: what the app is, then the three things worth doing -- configure the voice, open the system's text-to-speech settings and choose this engine there, read about the app -- each with the reason said before the button that acts on it.

*Eloquence settings*, which is also what the gear beside the engine in the system settings opens, straight to it rather than by way of the main screen. Six sections, each a heading TalkBack can jump to: voice customization, voice behaviors, voice quality, voice dictionary settings, other settings, and a voice to test the lot of them on.

*About us*: what the app is, who made it -- Sujan Rai, with the Blind Tech Nexus team -- and three ways to reach them, as buttons rather than as addresses to copy down. WhatsApp on [wa.me/9779708340992](https://wa.me/9779708340992) for bugs, feedback and feature requests, the Telegram channel on [t.me/blindtechnexus](https://t.me/blindtechnexus) for update broadcasts and the rest, and blindtechvisionary@gmail.com for an answer that can be relied on, with the address and a subject already filled in. Each asks by link first and then by the app's own scheme, and a link that nothing on the device can follow says so out loud rather than doing nothing at all. WhatsApp is asked three ways rather than two: `wa.me` is the address WhatsApp hands out, `api.whatsapp.com/send?phone=` is what `wa.me` redirects to and is the one that works on a browser that will not follow the redirect, and `whatsapp://send?phone=` is for a device with the app and no browser. The address this used to name, `w.me`, is the short form of a signed-in desktop session and answers a phone with a page saying it cannot be loaded there, which is what the button labelled "Contact on WhatsApp" arrived at. The `<queries>` block in the manifest names the messaging apps for the same reason: Android 11 and later answer "nothing is installed" to an app that has not said what it is looking for, and a scheme that cannot be resolved is a button that does nothing.

### Battery optimization

The exemption from battery optimization is required rather than wanted. A text-to-speech engine is idle almost all the time and has to answer at once when it is not, which is the shape every battery policy is written to kill: Doze takes the alarms away from anything not exempt, and the manufacturer policies stacked on top of it are stricter than Doze and do not say what they did. Refused, the voice stops in the middle of a sentence, or does not start at all once the phone has been still for a while, and nothing inside the app can see that happening in order to explain it.

So the app asks on every launch: whatever has not been given is asked for in `onCreate`, the notification permission first and then the exemption, and the welcome screen does the same asking on the way past it. The reason is said in words of the app's own before handing over to a system screen that is a yes or no with nothing said about it. The asking happens once per launch rather than on every resume, because a dialog that comes back every time the app is resumed is a dialog that gets refused every time the app is resumed. The answer is read again on every resume, since the screens that grant it are not ours. Two of them are tried: the one that asks about this app by name, and the list of every app for a build whose manufacturer took the first one out.

### Keeping the voice alive when nothing is speaking

The notification is what holds the engine up, and for most of the life of a phone it is holding it up while nothing is bound to the engine at all. That is the reason it is not posted by the engine: a text-to-speech service belongs to the system, which starts it when a reader binds and destroys it when the last reader lets go, so a notification posted by the engine is a notification that goes away precisely when it is wanted. `KeepAliveService` exists for that one job. It is a foreground service of the same special-use type, started when the switch is turned on, started again by `EngineReceiver` on `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` when "Start again after a restart" is on, and stopped when the switch is turned off. `KeepAlive`, which the engine still owns, now only holds the notification itself while the app's service cannot: Android 12 and later refuse a foreground service started by a service the system bound, and when that happens the engine posts it for as long as it is bound and hands it over the moment the other one is up.

The two post one notification, with one id and one channel, because a foreground service is only as protected as the notice that says it is and two ids are two notifications to explain. Both look for it every three seconds and put it back when anything has taken it away, and both stop when the setting is turned off -- the check re-reads the setting rather than trusting what it was told at the start, so a switch moved on the battery screen is obeyed within three seconds by whatever was holding the notification up.

The notification carries the app's only control that works while the engine is speaking and the screen is off. Its button sends a broadcast naming this app's own receiver, which asks the running service to be quiet: nothing outside a speech service can reach the audio a service has already handed to Android. It is off by default and it is a switch on this screen, because a button on a notification that does something the person using the phone did not ask for is a worse default than no button. Tapping the notification itself opens the battery screen rather than the launcher, which is where the answer to "why did my voice stop" lives.

### The numbers on the battery screen

Four of them, and every one is a trade the app cannot make on anybody's behalf. They are choice buttons rather than sliders, because the values that mean anything are a handful of measured ones and a bar between them invites a number that has no meaning.

*Hold the CPU a little longer*, from not at all to a minute: how long the wake lock is kept after the last sample has been handed over. The tail of an utterance is where a device that has decided to sleep catches one.

*How far ahead of the speech*, 150 to 4000 milliseconds. This engine makes audio about five hundred times faster than it can be heard (see [Speed](#speed)), so with nothing to hold it back a reader swiping through a list has six items synthesized and waiting and loses five of them when it interrupts. A short lead loses fewer; a long one lets a paragraph finish the moment it was asked for, which is what *Run ahead on long text* is for -- four seconds for anything the length of a paragraph and whatever was set for anything shorter.

*Give the engine back when idle*, from never to an hour. An engine instance holds its language's tables and its arena; making a fresh one costs a second or so before the first syllable, which is why the default is a quarter of an hour rather than either end.

*Give up after the engine goes silent*, five seconds to five minutes. The read that has nothing to wait for any more is what ends an utterance rather than the thread that asked for it: without a ceiling, an engine that has stopped answering holds the framework's synthesis thread for the rest of the day and the phone's voice with it.

Below them, one paragraph says what is in force now -- whether the app's own service is holding the notification and whether notifications are allowed at all -- because the question this screen is opened to answer is not what was set but whether it is working.

## Settings

Launcher icon, or the gear beside the engine in the text-to-speech settings. The six sections above, top to bottom: the voice and its gender, the seven numeric parameters each behind a button that opens it in a dialog of its own, the sample rate, the three dictionaries and the abbreviation switch that belongs with them, pauses, symbol pronunciation, phrase prediction and putting a voice back as it shipped, and a sample to hear the lot of them on. Nothing speaks on its own: a setting takes effect when you set it, and "Speak a sample" is how you hear it. "Stop the sample" is there as well, because the preview plays into an AudioTrack of its own and nothing else stops it.

A setting that is a choice is a button that carries its own value -- "Voice (Reed) selected" -- and the state description says "Click to change", so a screen reader hears the setting, its value and the action in one stop. The seven numeric parameters are buttons too ("Set voice rate" and its like) that open a dialog with the setting's name for a title, the bar, and Save and Cancel. Nothing is changed until Save, so a look inside a setting costs nothing.

The bar in the dialog is one control. A label, a bar and a number would be three stops for a screen reader, so the dialog merges them into a single node with `clearAndSetSemantics`. It reads as "Voice rate, 20 percent" and adjusts in place.

What a setting does is written above it and not inside it. Inflection, head size and roughness are not words that carry their own meaning, so each has a sentence of its own; folded into the control's announcement it would be heard again on every visit, with no way past it.

Values are percentages. The engine uses three scales (gender is 0 or 1, speed runs to 250, the rest to 100), so a raw number means nothing without knowing which. The setting called rate is the engine's own speed parameter: rate is the word Android and every screen reader use for it, and the number shown beside it is a percentage of 250 as the rest are of 100.

The dialog takes the keyboard and the bar inside it does not. A Compose slider keeps a focus of its own, which no screen reader can see once the semantics belong to the dialog's control, and it answers the End key by going to the maximum. So the bar is out of the tab order and the dialog answers the keys itself.

An arrow moves one percent. TalkBack adjusts any slider by a twentieth of the range it is given, and the number of steps a Compose slider declares never reaches the platform, so TalkBack's own arrow asks for five. Five percent of the speed scale is the difference between too slow to bear and faster than was wanted, so the control reads a request to move as a request to move one.

### Forcing rate, pitch and volume over other apps

Next to the rate, pitch and volume buttons is a switch: "Force over other apps". Checked, the rate, pitch or volume set here is what is spoken, and what the app asking for speech asked for is ignored -- TalkBack's rate no longer scales the engine's speed, so the voice is always at the pace set here. Unchecked, a client's asking is honoured as it always was: TalkBack at 150 percent speaks half again as fast.

Volume is the odd one, and honestly so: Android hands no per-utterance volume to an engine, applying a client's volume to the audio stream after the speech has left the service. Forcing volume re-asserts this app's own engine volume on every utterance, which keeps the number here in force whatever else might have moved it, but no switch inside a TTS engine can reach a stream volume.

Speech rate from the system is a multiplier, so it is treated as one. The engine's speed scale is far from linear: speed 100 speaks 2.7 times the default rate and speed 200 speaks 16.6 times. A measured curve converts between the two rather than multiplying, so 200 percent is about twice as fast and very high rates saturate at the engine's ceiling.

Voice settings belong to the voice. Set Reed's pitch to 55, go to Glen and come back, and Reed is still at 55. Only "Reset this voice to its defaults" forgets it, and only for the voice you are on.

Speed is the listener's, not the voice's. Six presets ship at engine speed 50 and Glen and Sandy ship at 70, half again as fast, so a preset brings its other seven settings and leaves the speed alone.

Dialogs start at the voice's own values. Each preset carries its own head size, inflection and volume: Reed is inflection 30 and volume 92, Bobby is 35 and 90. A parameter you have not set is never sent to the engine, so a fresh install sounds like Eloquence always has.

## Pauses

Eloquence pauses generously, which reads well in prose and badly in a screen reader where most utterances are a few words. The engine has no setting for it but it takes an annotation: `` `p `` and a number is a pause of that many milliseconds, and one placed in front of a punctuation mark replaces the pause that mark would have had.

Three choices. Do not shorten leaves the engine alone. Shorten at end of text trims only the gap after the last thing said. Shorten all pauses trims punctuation as well, which is the default and takes a sentence with commas to 65 percent of its original time.

The rule is davidacm's, from the [NVDA IBMTTS driver](https://github.com/davidacm/NVDA-IBMTTS-Driver), which calls these JAWS-like pauses. A mark counts when it follows a letter, digit or space and is followed by whitespace, a slash or the end, which leaves the point in 3.14, the colon in 2:30 and the comma in 1,024 alone.

## Symbol pronunciation

The engine has no parameter for this, and neither does anything else in the ECI interface: the published eighteen settings are voices, rates and code sets, and that is all. What the engine does have is a character-name table built into its language data, and that was measured rather than assumed, by building the engine and speaking one string at a time:

* A symbol standing on its own is always named. "?" alone is "question mark", byte for byte the same audio as the words; "*" is "asterisk", "," is "comma", "~" is "tilda" -- the engine's own spellings, kept here so a symbol sounds the same whether the engine names it or we do.
* A symbol attached to a word is silent when it is sentence punctuation or a bracket -- "(hello)" costs exactly what "hello" costs -- and read in place when it carries meaning: "$5" is "five dollars", "50%" is "fifty percent", "a=b" is "a equals b". The backslash is one of these too: "a\\b" is "a backslash b" and a Windows path is spelled out, which is why none has to take it away.
* The backtick is the annotation escape and is never touched, and the hyphen standing alone is the one symbol the engine has no name for at all.

So the five settings are made out of text changes on the way in, which is also how Speech Dispatcher's own module for this engine does it. *Some* is the engine left exactly as it stands, and is the default, so nobody's speech changes when this arrives. *Most* adds the names of attached brackets, quotes, question marks and the like, which the engine leaves silent. *All* adds attached periods and commas. *None* names nothing: every symbol is replaced with silence except the marks the engine needs for its pauses and the apostrophe inside a word. *Custom* is none plus a list of the user's own, entered as the symbols themselves.

Three things are deliberately never replaced at any level that speaks them: the symbols the engine already reads in place -- the dollar, the percent, the equals and the rest -- because writing " dollar" in place of "$" trades the engine's "five dollars" for "five dollar"; a mark between two digits, because 3.14 and 2:30 and 1,024 are number readings rather than symbols; and the apostrophe inside a word, because "don't" is a contraction and not a symbol.

## Voice, phrase prediction, abbreviations

The voice is whichever the settings screen says. A caller that names a voice does not override it, because a client holds on to the Voice object it was given: a screen reader that connected while Reed was selected goes on asking for Reed however many times the setting changes underneath it. The name is still what decides the language.

Phrase prediction is the engine guessing where the phrase boundaries in a sentence are and shaping the intonation to match. That reads well in prose and gets in the way in a screen reader, where a line is usually a fragment and the guess is wrong, so it is off. It is an annotation rather than a parameter, so it goes out with every utterance.

Expand abbreviations is `eciDictionary`, which is inverted in the engine: zero turns it on. Off by default, and re-sent with every utterance, since it is the one setting with nothing to say whether something else has moved it.

## Dictionaries

Three rows pick a file each for the main, root and abbreviation volumes. "Remove dictionaries" clears all three.

The files people share are text: a word, a tab, then either a respelling (`dee oe jeigh`) or a pronunciation in the engine's own alphabet (`` `[Ekspoz1e] ``). [mohamed00/altibmttsdictionaries](https://github.com/mohamed00/altibmttsdictionaries) has the English pair.

The engine's own loader will not read them. `eciLoadDict` answers `eciDictAccessError` for every one because it expects IBM's saved binary form, so the text is parsed here and the engine is taught a word at a time through `eciUpdateDict`. 3000 entries take about 450 ms, once, when the service starts. They reload only when the files change.

A picked file is copied into app storage. A content URI belongs to whichever app supplied it, may not be readable later, and the speech service reads it from another process.

No dictionary is shipped here. See LICENSE.

## Interrupting

A screen reader wants each string to cut off the one before it. Two things make that work.

The engine cannot abandon an utterance, so long text is handed over in sentence-sized pieces and asking for silence waits out a piece rather than a whole paragraph. The rule for where a piece may end is openevv's, ported from its NVDA driver: a sentence end is free because the engine already pauses there, anywhere else costs about 0.4s, so a full stop has to prove itself. A word with a dot inside it is an abbreviation, and a short capitalised word is an initial.

Synthesis is paced to real time. The engine produces 1.3s of audio in 12 ms and Android queues every byte of it, so a reader swiping quickly would leave five or six items queued while the first is still playing and lose all of them when it asks for silence. Output runs no more than 300 ms ahead. Measured on the same text: 2229 ms of audio handed over in 700 ms of speaking without that limit, about 1000 ms with it.

## Updates

The app asks one URL when it starts: [the release channel](https://raw.githubusercontent.com/blind-tech-nexus/remote-data-json/main/alt-eloquence-tts-update.json), a file with two values in it, `version` and `url`. The version is compared with this build's `versionName` and, if they are not the same, a dialog says so in the words "A new version (%s) is available to download" and asks whether to fetch it now. "Later" is not a setting: the same version is not offered again for the rest of that run of the app and is asked about again the next time it opens. `UpdateTest` holds the rules of the answer: that the same version is no update, that a missing or blank value is no update, and that an address that is not https is no update -- the download is an executable, so a plain http address is refused rather than warned about, since a person halfway through a download is not in a position to judge a certificate.

Nothing is installed by the app. When the bytes are down the file is validated as a package by the package manager, and the installer is asked with a one-read grant on a content URI into the cache. On Android 8 and later that needs this app to have been allowed to install apps at all, which is not the app's to grant: the allowance is asked for on the system's screen, and the file waits in the cache until it has been given. The dialog while bytes are arriving says how many have arrived, in megabytes, and has a button that breaks the read off and deletes the partial file; the bar under the number is for whoever can see it, and nothing that has to be heard depends on it.

The asking is one `HttpURLConnection` and one `JSONObject`, on a thread of its own, with no library. The About screen says what it found whenever it is asked from there, which is the only case in which an answer of "you are up to date" or "nothing could be reached" is worth a sentence; on the way into a launch, silence is the right answer to both.

## Speed

The engine is not slow. A host build of the same C the app ships, on the same single thread the app uses, said 4003 characters of English to 8.5 megabytes of audio -- 386 seconds of speech -- in 0.74 seconds, 520 times real time. So every part of "the engine is slow" that was real was somewhere else, and the four that were found are worth writing down because none of them is where a person reading the code would look first.

**The engine's output buffer was too small.** Samples leave this engine one buffer at a time, and the buffer's size is asked of `eciSetOutputBuffer` in samples: the engine fills it, hands it over on the callback, and only then makes the next. At 1024 samples -- two kilobytes -- a long paragraph is four thousand callbacks, each one a lock, a copy into a malloc, a `SetByteArrayRegion` and a wake-up on the other side, and the same again in reverse for the read. Measured on the same text, going to 4096 samples took the whole utterance from 0.961 s to 0.742 s and the reads from 4156 to 1039; 16384 was faster again, but the first byte of audio arrived 2 milliseconds later and the ring has to be a multiple of the buffer, so four thousand and ninety-six samples is where it stays. The latency cost is one buffer's worth of synthesis and it shows: 12.0 ms to 12.3 ms. What was not worth doing is the reverse, a bigger buffer for a shorter first pause -- the first byte arrives when the *first* buffer is full, so past about a third of a second of speech the wait is heard.

**A piece boundary was a full stop.** Long text is handed over in pieces, because the engine cannot abandon an utterance and a cancel has to wait for something. Where the text has a sentence end, the piece goes there and it is free -- measured exactly: 482,449 samples whole, the same 482,449 in six pieces. Where it has none within reach, the cut lands on whitespace and the engine puts the pause of a full stop there anyway: 0.39 seconds, measured, every boundary. Text with no full stops in it is the ordinary case for a chapter of a book or a page of a table, and thirty seconds of silence in ten minutes of speech was what "slow" meant. `TextPieces` now marks a cut it had to make as forced and the service ends such a piece with the engine's own `` `p1 `` annotation, which measured 0.15 s for two boundaries instead of 0.78: the pause is not prosody, it is an artefact of where a splitter happened to cut, and it can be taken back out.

**The engine's own log was on.** `g_iTTSLogLevel` in openevv's port layer starts at 9, and at 4 and above the engine's internals write a line to stderr for a mutex that did not answer the way they expected. That path is not taken on the shipping arrangement, where one thread drives the engine and one reads it, so nothing was changed here -- but it is why a harness that runs the engine differently looks twenty per cent slower than the app is, and it is worth knowing before the next measurement of this kind is believed.

**Everything before the first syllable was done on every utterance.** The text used to be copied through a chain of five passes that each built new strings, the pronunciation dictionary was re-taught per utterance, and the settings were compared field by field. Now the emoji, symbol, fix-up and code-set passes each make one scan of the characters and each answer with the original string when they have nothing to change; `WesternText` keeps a table that says whether a character needs work at all, so ordinary English is a copy rather than a lookup per character; a dictionary is re-taught when the file's last-modified time has moved and not otherwise; and the settings carry a revision that goes up on any write, so an unchanged configuration is one integer comparison. What the reader waits for is the first frame of audio, which is 12 ms of this engine's time on a desktop build and is the only number in the whole utterance that cannot be shared out over the rest of it.

The engine is also kept between utterances rather than made and thrown away each time, which is what the "give the engine back when idle" number on the battery screen is for; and one read takes everything the engine has written rather than everything a caller happened to ask for, so a 4096-sample frame crosses the boundary between the two threads in one go whatever the size of the buffer the framework offered.

## Wear OS

Runs on a watch. Tested on a Pixel Watch 4, which is armeabi-v7a.

A Pixel Watch has no USB, so adb is wireless. On the watch: Settings, System, About, Versions, tap Build number seven times. Then Developer options, ADB debugging on, Wireless debugging on, Pair new device. Watch and computer on the same Wi-Fi, with the watch's Wi-Fi actually on rather than dropped for Bluetooth.

```
adb mdns services                 # the watch announces both ports
adb pair 192.168.x.x:PAIRPORT     # then the six digits on screen
adb connect 192.168.x.x:PORT
adb -s SERIAL shell getprop ro.product.cpu.abilist
./gradlew installDebug -Pevvdroid.abis=armeabi-v7a
```

## Layout

```
native/openevv          the engine, a submodule, untouched
native/android.mk       two make targets openevv has no reason to carry
native/build-native.sh  cross-compiles the engine, links libevvjni.so
native/tools            drives openevv's own test gate over adb
app/src/main/cpp        JNI bridge
app/src/main/kotlin     the service, engine wrapper, settings
```

The engine is built by its own Makefile rather than CMake, because that Makefile generates the language rules with Python first. Gradle runs the script and picks the result up as prebuilt `jniLibs`.

Everything links into one shared object, `libevvjni.so`. Android only packages files named `lib*.so`, and openevv's own library records a soname of `libeci.so.1`, which nothing would then find.

`native/tools/probe-on-device.sh` stands in for openevv's own probe, so its gate, `test/matrix.sh check enus`, drives an Android build over adb unchanged. That is 98 cases comparing sample hashes and reported answers against baselines recorded on x86.

## Licence

MIT for the Kotlin, the C bridge, the build and the tests. See LICENSE.

Not the engine. openevv is MIT under its own copyright and is fetched from upstream, not copied here.

Not the language data openevv carries. That is transcribed from IBM's Embedded ViaVoice objects and is IBM's. `native/openevv/NOTICE` says what it is and whose it is.

No pronunciation dictionary is included. Those belong to whoever wrote them, and the best known collection carries no licence at all, so it is linked rather than copied. The test fixture here is written for the purpose.
