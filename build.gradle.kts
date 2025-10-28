plugins {
	// this is necessary to avoid the plugins to be loaded multiple times
	// in each subproject's classloader
	alias(libs.plugins.androidApplication) apply false
	alias(libs.plugins.androidLibrary) apply false
	alias(libs.plugins.composeMultiplatform) apply false
	alias(libs.plugins.composeCompiler) apply false
	alias(libs.plugins.kotlinMultiplatform) apply false
	alias(libs.plugins.serialization) apply false
	alias(libs.plugins.buildKonfig) apply false
	alias(libs.plugins.ktlint)
}

val printKtlintFormatTask = tasks.register("printKtlintFormatTask") {
	doLast {
		println("Use ./gradlew ktlintFormat to fix formatting issues")
	}
}

tasks
	.matching { it.name.startsWith("ktlint") && it.name.endsWith("Check") }
	.configureEach { finalizedBy(printKtlintFormatTask) }

allprojects {
	apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

	ktlint {
		kotlinScriptAdditionalPaths {
			include(
				fileTree(
					mapOf(
						"dir" to projectDir,
						"include" to listOf("*.gradle.kts", "gradle/**/*.gradle.kts"),
					),
				),
			)
		}
		filter {
			exclude("**/generated/**")
			exclude("**/BuildKonfig.kt")
		}
	}

	tasks
		.matching { it.name.startsWith("ktlint") && it.name.endsWith("Check") }
		.configureEach { finalizedBy(printKtlintFormatTask) }
}

tasks.register("checkAgentsEnvironment") {
	group = "verification"
	description = "Runs all tests that are expected to pass in the agent environment"
	dependsOn(":composeApp:jvmTest")
	val secondaryTasks = listOf(":composeApp:jvmUiTestClasses", "ktlintCheck") + subprojects.map { "${it.path}:ktlintCheck" }
	dependsOn(secondaryTasks)

	val jvmTestTask = tasks.findByPath(":composeApp:jvmTest")
	secondaryTasks.map { tasks.findByPath(it) }.forEach {
		it!!.mustRunAfter(jvmTestTask)
	}
}
