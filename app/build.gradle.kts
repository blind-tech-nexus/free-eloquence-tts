import org.gradle.internal.os.OperatingSystem
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

val abis = (findProperty("evvdroid.abis") as String? ?: "arm64-v8a,armeabi-v7a,x86_64")
	.split(",").map { it.trim() }.filter { it.isNotEmpty() }
val rulesForm = findProperty("evvdroid.rules") as String? ?: "c"
// The ten language modules this app speaks: the eight Western modules
// (American and British English, German, Castilian and Mexican Spanish,
// French and Canadian French, and Italian) plus Hindi and Nepali. All ten
// are offered above -- on the settings screen, in the voices the service
// answers with, and in what a caller can ask for by locale -- so the weight of
// each is weight the app can speak in. See [Eci.LANGUAGES], which lists the
// same ten and is checked against this by a test. The Japanese and the
// unfinished Polish modules are left out on purpose. American English stays
// first: the engine's Makefile takes the first as its default language.
val languages = "lang/enus lang/engb lang/dede lang/eses lang/esus lang/frfr lang/frca lang/itit lang/hiin lang/nenp"
val nativeOut = layout.buildDirectory.dir("native/jniLibs")

// The engine is built by its own Makefile rather than by CMake, because that
// Makefile writes the language rules with Python before it compiles them and
// nothing here has a reason to reimplement that.
val buildNative by tasks.registering(Exec::class) {
	group = "build"
	description = "Cross-compiles the openevv engine and the JNI bridge for each ABI."
	val engine = rootProject.layout.projectDirectory.dir("native/openevv")
	val script = rootProject.layout.projectDirectory.file("native/build-native.sh")
	// The engine is vendored in native/openevv rather than fetched as a
	// submodule, so its sources are inputs like any other and an edit to one of
	// them is a rebuild. Only src/, rom/ and lib/ are named: the language modules
	// under lang/ are megabytes of text data that rarely changes, and
	// hashing them on every build would cost more than the build saves. The
	// generated rule tables are written under native/openevv/build/, which is
	// outside the tree named here and output rather than input.
	inputs.files(rootProject.fileTree("native/openevv/src"))
	inputs.files(rootProject.fileTree("native/openevv/rom"))
	inputs.files(rootProject.fileTree("native/openevv/lib"))
	inputs.files(
		rootProject.fileTree("native/openevv/lang") {
			include("*/*.settings", "*/*.statements", "*/*.phonemes", "*/*.codepoints", "*/rules/*")
		}
	)
	inputs.file(rootProject.layout.projectDirectory.file("native/android.mk"))
	inputs.file(script)
	inputs.file(layout.projectDirectory.file("src/main/cpp/evv_jni.c"))
	inputs.property("abis", abis)
	inputs.property("rules", rulesForm)
	inputs.property("langs", languages)
	outputs.dir(nativeOut)
	val shell = if (OperatingSystem.current().isWindows) {
		val git = System.getenv("PROGRAMFILES")?.let { file("$it/Git/bin/bash.exe") }
		if (git != null && git.exists()) git.absolutePath else "bash"
	} else {
		"bash"
	}
	commandLine(shell, script.asFile.absolutePath)
	environment("ABIS", abis.joinToString(" "))
	environment("RULES", rulesForm)
	environment("LANGS", languages)
	environment("OUT", nativeOut.get().asFile.absolutePath)
	doFirst {
		if (!engine.file("Makefile").asFile.exists()) {
			throw GradleException("native/openevv has no Makefile; the engine is vendored in this repository.")
		}
	}
}

android {
	namespace = "com.blindtechnexus.freeeloquencetts"
	compileSdk = 36
	ndkVersion = "26.1.10909125"

	defaultConfig {
		applicationId = "com.blindtechnexus.freeeloquencetts"
		minSdk = 23
		targetSdk = 36
		versionCode = 5
		versionName = "1.5"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		ndk {
			abiFilters += abis
		}
	}

	sourceSets.getByName("androidTest") {
		kotlin.srcDirs("src/androidTest/kotlin")
	}

	sourceSets.getByName("main") {
		kotlin.srcDirs("src/main/kotlin")
		jniLibs.srcDirs(nativeOut)
	}

	signingConfigs {
		create("debugConfig") {
			storeFile = file("${rootDir}/debug.keystore")
			storePassword = "android"
			keyAlias = "androiddebugkey"
			keyPassword = "android"
		}
		create("release") {
			storeFile = rootProject.file("release.jks")
			storePassword = "811015"
			keyAlias = "release"
			keyPassword = "811015"
			// Off by default in AGP. It is what lets the key be rotated
			// later without every install having to be thrown away.
			enableV3Signing = true
		}
	}

	buildTypes {
		release {
			signingConfig = signingConfigs.findByName("release")
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
		debug {
			signingConfig = signingConfigs.getByName("debugConfig")
			isJniDebuggable = true
		}
	}

	packaging {
		jniLibs {
			// A screen reader cannot wait for a library to be unpacked, and an
			// uncompressed one is mapped straight out of the APK.
			useLegacyPackaging = false
		}
	}

	splits {
		abi {
			isEnable = (findProperty("evvdroid.abiSplits") as String?)?.toBoolean() ?: false
			reset()
			include(*abis.toTypedArray())
			isUniversalApk = true
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	buildFeatures {
		buildConfig = true
		compose = true
	}
}

val ndkHome = System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT")
if (!ndkHome.isNullOrEmpty() && file(ndkHome).exists()) {
	tasks.named("preBuild") {
		dependsOn(buildNative)
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.material)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.material3)
	implementation(libs.compose.material.icons.core)
	implementation(libs.compose.material.icons.extended)

	androidTestImplementation(libs.junit)
	androidTestImplementation(libs.androidx.test.junit)
	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.core)
	// The bill of materials a second and a third time. It is what supplies the
	// version to the versionless compose coordinates above, and a configuration
	// carries only the constraints declared on it and on the ones it extends --
	// neither androidTestImplementation nor debugImplementation extends
	// implementation. Without this the two coordinates below have no version to
	// resolve to, and the answer is "Could not find
	// androidx.compose.ui:ui-test-junit4:." with nothing after the last colon
	// rather than a missing library. It went unnoticed because no build here
	// compiled the androidTest sources until one did.
	androidTestImplementation(platform(libs.compose.bom))
	androidTestImplementation("androidx.compose.ui:ui-test-junit4")
	debugImplementation(platform(libs.compose.bom))
	debugImplementation("androidx.compose.ui:ui-test-manifest")
}
