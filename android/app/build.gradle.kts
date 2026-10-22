import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.chronolock"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.chronolock.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 8
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
        // Service keys come from local.properties or -P; blank values keep login hidden and ads on test units.
        fun setting(name: String): String = providers.gradleProperty(name).orNull
            ?: localSettings.getProperty(name).orEmpty()
        buildConfigField("String", "SUPABASE_URL", "\"${setting("chronolock.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${setting("chronolock.supabaseAnonKey")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${setting("chronolock.googleWebClientId")}\"")
        val admobAppId = setting("chronolock.admobAppId")
        val admobBannerId = setting("chronolock.admobBannerId")
        // Both or neither: a live banner under the sample app ID violates AdMob policy, and a real app ID without a
        // banner declares the advertising ID for ads that never show (Data safety mismatch).
        check(admobAppId.isBlank() == admobBannerId.isBlank()) {
            "Set both chronolock.admobAppId and chronolock.admobBannerId in local.properties, or neither."
        }
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        manifestPlaceholders["admobAppId"] = admobAppId.ifBlank { "ca-app-pub-3940256099942544~3347511713" }
    }
    buildFeatures { compose = true; buildConfig = true }
    // Upload key for Play App Signing lives outside the repo; local.properties points to it.
    fun localSetting(name: String) = providers.gradleProperty(name).orNull ?: localSettings.getProperty(name).orEmpty()
    val releaseStore = localSetting("chronolock.releaseStoreFile")
    if (releaseStore.isNotBlank()) {
        signingConfigs.create("release") {
            storeFile = file(releaseStore)
            storePassword = localSetting("chronolock.releaseStorePassword")
            keyAlias = localSetting("chronolock.releaseKeyAlias")
            keyPassword = localSetting("chronolock.releaseKeyPassword")
        }
        buildTypes.getByName("release") { signingConfig = signingConfigs.getByName("release") }
    }
    // Without a real AdMob app the release build serves no ads, so it must not declare the advertising ID.
    if (localSetting("chronolock.admobAppId").isBlank()) sourceSets.getByName("release").manifest.srcFile("src/releaseNoAds/AndroidManifest.xml")
    // Opt-in device scenarios change permissions and reboot. Keep their data separate.
    if (providers.gradleProperty("deviceVerification").orNull == "true") {
        buildTypes.getByName("debug") { applicationIdSuffix = ".verification" }
    }
    // Every supported language must be available offline after an in-app language change.
    bundle { language { enableSplit = false } }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kapt { correctErrorTypes = true }

dependencies {
    implementation(project(":domain"))
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("androidx.work:work-runtime-ktx:2.7.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Avoid reflection on InputManager.getInstance, removed on newer Android images.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
