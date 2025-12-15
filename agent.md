# Agent Notes

- Run `./gradlew checkAgentsEnvironment -PenableAndroid=false --parallel --console=plain` before committing.
- Run `./gradlew ktlintFormat` to apply formatting fixes.
- Run `./gradlew :composeApp:jvmTest` to execute JVM tests for Compose.
- Android instrumented tests cannot run here because the Android SDK is unavailable.
- UI tests are not expected to run in the headless agent environment.
