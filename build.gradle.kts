plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Resets the stateful cloud virtual service (drivelink-proto) to its seed data: all cars locked,
// charge limits 80 / 90. Needs BLAZEMETER_API_KEY (path to the key JSON), default api-key.json.
//   ./gradlew resetCloudState
// With -PresetCloudState, every connected*AndroidTest task runs it first.
val resetCloudState by tasks.registering(Exec::class) {
    group = "verification"
    description = "Reset drivelink-proto to its seed data (scripts/build-mock.py --reset)."
    workingDir = rootDir
    environment("BLAZEMETER_API_KEY", System.getenv("BLAZEMETER_API_KEY") ?: rootDir.resolve("api-key.json").path)
    commandLine("python3", "scripts/build-mock.py", "--reset")
}

if (providers.gradleProperty("resetCloudState").isPresent) {
    subprojects {
        tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
            .configureEach { dependsOn(resetCloudState) }
    }
}
