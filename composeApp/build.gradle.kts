
import com.android.build.api.dsl.ApplicationExtension
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.serialization)
	alias(libs.plugins.buildKonfig)
	alias(libs.plugins.androidApplication) apply false
}

val androidEnabled =
	providers
		.gradleProperty("enableAndroid")
		.map(String::toBoolean)
		.orElse(true)

if (androidEnabled.get()) {
	pluginManager.apply(
		libs.plugins.androidApplication
			.get()
			.pluginId,
	)
	extensions.configure<ApplicationExtension> {
		namespace = "de.findusl.homebox.client"
		compileSdk =
			libs.versions.android.compileSdk
				.get()
				.toInt()

		defaultConfig {
			applicationId = "de.findusl.homebox.client"
			minSdk =
				libs.versions.android.minSdk
					.get()
					.toInt()
			targetSdk =
				libs.versions.android.targetSdk
					.get()
					.toInt()
			versionCode = 1
			versionName = "1.0"
		}
		packaging {
			resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
		buildTypes {
			getByName("release") {
				isMinifyEnabled = false
			}
		}
		compileOptions {
			sourceCompatibility = JavaVersion.VERSION_11
			targetCompatibility = JavaVersion.VERSION_11
		}
	}
	dependencies {
		add("debugImplementation", compose.uiTooling)
	}
}

kotlin {
	if (androidEnabled.get()) {
		androidTarget {
			compilerOptions {
				jvmTarget.set(JvmTarget.JVM_11)
			}
		}
	}

	jvm {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
		}
		val testCompilation = compilations["test"]
		val uiTestCompilation: KotlinJvmCompilation =
			compilations.create("uiTest",
				// This anonymous function shuts up the weird warning here
				fun KotlinJvmCompilation.() {
					associateWith(testCompilation)
				}
			)
		testRuns {
			val uiTest by creating {
				setExecutionSourceFrom(uiTestCompilation)
			}
		}
	}

	listOf(
		iosArm64(),
		iosSimulatorArm64(),
	).forEach { iosTarget ->
		iosTarget.binaries.framework {
			baseName = "ComposeApp"
			isStatic = true
		}
	}

	sourceSets {
		if (androidEnabled.get()) {
			val androidMain by getting
			androidMain.dependencies {
				implementation(compose.preview)
				implementation(libs.androidx.activity.compose)
				implementation(libs.ktor.client.cio)
			}
		}
		commonMain.dependencies {
			implementation(compose.foundation)
			implementation(compose.runtime)
			implementation(compose.ui)
			implementation(libs.compose.material3)
			implementation(libs.koog)
			implementation(libs.kotlinx.collections.immutable)
			implementation(libs.kotlinx.datetime)
			implementation(libs.ktor.client.cio)
			implementation(libs.ktor.client.contentNegotiation)
			implementation(libs.ktor.client.core)
			implementation(libs.ktor.serialization.kotlinxJson)
			implementation(libs.multiplatform.settings)
			implementation(libs.multiplatform.settings.noarg)
			implementation(libs.napier)
			implementation(libs.wav.recorder)
		}
		jvmMain.dependencies {
			implementation(compose.desktop.currentOs)
			implementation(libs.ktor.client.cio)
		}
		val jvmTest by getting {
			dependencies {
				implementation(libs.junit)
			}
		}
		val jvmUiTest by getting {
			dependencies {
				implementation(libs.junit)
				@OptIn(ExperimentalComposeLibrary::class)
				implementation(compose.uiTest)
				@OptIn(ExperimentalComposeLibrary::class)
				implementation(compose.desktop.uiTestJUnit4)
				implementation(compose.components.uiToolingPreview)
				implementation(libs.multiplatform.settings.test)
				implementation(libs.mockk)
			}
		}
		iosArm64Main.dependencies {
			implementation(libs.ktor.client.darwin)
		}
		iosSimulatorArm64Main.dependencies {
			implementation(libs.ktor.client.darwin)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.kotlinx.coroutines.test)
			implementation(libs.ktor.client.mock)
		}
	}
}

compose.desktop {
	application {
		mainClass = "de.findusl.homebox.client.MainKt"
	}
}

buildkonfig {
	packageName = "de.findusl.homebox.client"

	defaultConfigs {
		buildConfigField(STRING, "OPENAI_API_KEY", System.getenv("PRIVATE_OPENAI_API_KEY"))
		buildConfigField(STRING, "HOMEBOX_BASE_URL", System.getenv("HOMEBOX_BASE_URL"))
		buildConfigField(STRING, "HOMEBOX_USERNAME", System.getenv("HOMEBOX_USERNAME"))
		buildConfigField(STRING, "HOMEBOX_PASSWORD", System.getenv("HOMEBOX_PASSWORD"))
	}
}
