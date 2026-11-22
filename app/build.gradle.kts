plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val permanentStore = providers.environmentVariable("DUCKDOUGH_KEYSTORE_PATH").orNull
val permanentPassword = providers.environmentVariable("DUCKDOUGH_STORE_PASSWORD").orNull
val permanentAlias = providers.environmentVariable("DUCKDOUGH_KEY_ALIAS").orNull
val permanentKeyPassword = providers.environmentVariable("DUCKDOUGH_KEY_PASSWORD").orNull
val permanentSigningReady = listOf(permanentStore, permanentPassword, permanentAlias, permanentKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.norman.duckdough"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.norman.duckdough"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("duckdoughVersionCode").orNull?.toInt() ?: 2
        versionName = providers.gradleProperty("duckdoughVersionName").orNull ?: "0.2.0"
        require(versionCode!! > 0) { "versionCode must be positive" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (permanentSigningReady) {
        signingConfigs.create("permanentRelease") {
            storeFile = file(permanentStore!!)
            storeType = "PKCS12"
            storePassword = permanentPassword
            keyAlias = permanentAlias
            keyPassword = permanentKeyPassword
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("permanentRelease")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    if (releaseRequested && !permanentSigningReady && providers.gradleProperty("duckdoughAllowUnsigned").orNull != "true") {
        throw GradleException("Permanent release signing is not configured. See SIGNING.md; release builds never fall back to a debug key.")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
