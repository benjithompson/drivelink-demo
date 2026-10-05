plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.drivelink.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:settings"))
    testImplementation(libs.okhttp.mockwebserver)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The repository tests serve the contract examples in api/examples and api/examples-raw.
// The example folders are inputs with relative path sensitivity; the root path is only an argument.
class RepoRootArgument(@get:Internal val path: String) : CommandLineArgumentProvider {
    override fun asArguments(): List<String> = listOf("-Ddrivelink.repoRoot=$path")
}

tasks.withType<Test>().configureEach {
    inputs.dir(rootDir.resolve("api/examples")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootDir.resolve("api/examples-raw")).withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(RepoRootArgument(rootDir.path))
}
