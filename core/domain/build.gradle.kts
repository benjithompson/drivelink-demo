plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Passes the repository root to the contract test as the system property drivelink.repoRoot.
 * The example folders are inputs with relative path sensitivity, so the absolute root path
 * does not change the build cache key.
 */
class ContractExamples(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val examples: File,
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val examplesRaw: File,
    @get:Internal val repoRoot: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): List<String> = listOf("-Ddrivelink.repoRoot=${repoRoot.path}")
}

tasks.test {
    jvmArgumentProviders.add(
        ContractExamples(
            examples = rootDir.resolve("api/examples"),
            examplesRaw = rootDir.resolve("api/examples-raw"),
            repoRoot = rootDir,
        ),
    )
}
