import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.rigweave.mobile"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "app.rigweave.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake { cppFlags += "-std=c++17 -Wall -Wextra -Wpedantic" } }
    }

    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val flexProperties = Properties()
rootProject.file("../flex-developer.properties").takeIf { it.isFile }?.inputStream()?.use { flexProperties.load(it) }
fun flexValue(name: String): String = providers.environmentVariable(name).orNull
    ?: flexProperties.getProperty(name).orEmpty()
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android.defaultConfig {
    buildConfigField("String", "FLEX_SMARTLINK_CLIENT_ID", quoted(flexValue("FLEX_SMARTLINK_CLIENT_ID")))
    buildConfigField("String", "FLEX_SMARTLINK_AUTH_DOMAIN", quoted(flexValue("FLEX_SMARTLINK_AUTH_DOMAIN")))
    buildConfigField("String", "FLEX_SMARTLINK_REDIRECT_URI", quoted(flexValue("FLEX_SMARTLINK_REDIRECT_URI")))
    buildConfigField("String", "FLEX_SMARTLINK_SERVER", quoted(flexValue("FLEX_SMARTLINK_SERVER")))
}

val buildRustFlex by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the Nexus-derived Flex core for every Android ABI"
    workingDir(rootProject.file("../rust/rigweave-flex"))
    val sdkRoot = providers.environmentVariable("ANDROID_SDK_ROOT").orElse(providers.environmentVariable("ANDROID_HOME")).orNull
    if (sdkRoot != null) environment("ANDROID_NDK_HOME", file("$sdkRoot/ndk/${android.ndkVersion}").absolutePath)
    commandLine(providers.environmentVariable("CARGO").orElse("cargo").get(), "ndk", "-t", "armeabi-v7a", "-t", "arm64-v8a", "-t", "x86", "-t", "x86_64",
        "build", "--release")
}

tasks.matching { it.name.startsWith("configureCMake") }.configureEach { dependsOn(buildRustFlex) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.github.mik3y:usb-serial-for-android:3.11.0")
    implementation("org.maplibre.gl:android-sdk:13.0.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
