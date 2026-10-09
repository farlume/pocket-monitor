plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val syncProjectNotices = tasks.register<Sync>("syncProjectNotices") {
    from(rootProject.projectDir) { include("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md") }
    into(layout.buildDirectory.dir("generated/project-notices/licenses"))
}
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        val required = mapOf(
            "ANDROID_KEYSTORE_PATH" to releaseStoreFile,
            "ANDROID_KEYSTORE_PASSWORD" to releaseStorePassword,
            "ANDROID_KEY_ALIAS" to releaseKeyAlias,
            "ANDROID_KEY_PASSWORD" to releaseKeyPassword,
        )
        val missing = required.filterValues { it.orNull.isNullOrBlank() }.keys
        check(missing.isEmpty()) { "Missing release signing environment variables: ${missing.joinToString()}" }
        check(file(releaseStoreFile.get()).isFile) { "Release keystore file does not exist" }
    }
}

android {
    namespace = "dev.icelum.pocketmonitor"
    compileSdk = 35
    ndkVersion = "28.2.13676358"
    externalNativeBuild { ndkBuild { path = file("src/main/cpp/Android.mk") } }
    defaultConfig {
        applicationId = "dev.icelum.pocketmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile.orNull?.let { file(it) }
            storePassword = releaseStorePassword.orNull
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }
    buildTypes {
        getByName("debug") { versionNameSuffix = "-debug" }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    // Both languages must remain available for offline, in-app switching in AAB installs.
    bundle { language { enableSplit = false } }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/project-notices"))
    testOptions.unitTests.isIncludeAndroidResources = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging.resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
}
tasks.named("preBuild") { dependsOn(syncProjectNotices) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}
dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("com.herohan:UVCAndroid:1.0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.8.1")
}
