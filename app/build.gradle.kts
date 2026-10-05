import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// secrets.properties is gitignored. CI writes it from repository secrets.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.drivelink.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.drivelink.demo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.drivelink.core.testing.DriveLinkTestRunner"
        buildConfigField("String", "MOCK_BASE_URL", "\"${secrets.getProperty("MOCK_BASE_URL", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The device tests serve the contract examples (api/examples) from a MockWebServer.
    sourceSets {
        getByName("androidTest") {
            assets.directories.add(rootProject.file("api/examples").path)
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.osmdroid.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    kspAndroidTest(libs.hilt.compiler)
}

// The ViewModel tests decode the contract examples in api/examples.
class RepoRootArgument(@get:Internal val path: String) : CommandLineArgumentProvider {
    override fun asArguments(): List<String> = listOf("-Ddrivelink.repoRoot=$path")
}

tasks.withType<Test>().configureEach {
    inputs.dir(rootDir.resolve("api/examples")).withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(RepoRootArgument(rootDir.path))
}

// A Perfecto repository script (espresso execute) cannot pass instrumentation arguments. With
// -PembedBaseUrl, the test APK carries MOCK_BASE_URL as the asset drivelink-base-url.txt, and
// DriveLinkTestRunner uses it when no baseUrl argument is given. Without the flag the asset is absent.
abstract class EmbedBaseUrl : DefaultTask() {
    @get:Input abstract val baseUrl: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun write() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        if (baseUrl.get().isNotBlank()) dir.resolve("drivelink-base-url.txt").writeText(baseUrl.get())
    }
}

val embedBaseUrl = tasks.register<EmbedBaseUrl>("embedBaseUrl") {
    baseUrl.set(if (providers.gradleProperty("embedBaseUrl").isPresent) secrets.getProperty("MOCK_BASE_URL", "") else "")
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(embedBaseUrl, EmbedBaseUrl::outputDir)
    }
}
