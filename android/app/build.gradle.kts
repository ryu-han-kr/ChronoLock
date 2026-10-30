import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.chronolock"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.chronolock.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 28
        versionName = "0.18.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // Debug builds fall back to Google test banners; device verification and demo recordings turn them off.
        buildConfigField("Boolean", "TEST_ADS", "true")
        // Free/Pro split. Off until the Pro product exists in Play Console; while off everyone has every feature.
        buildConfigField("Boolean", "PRO_GATING", (setting("chronolock.proGating") == "true").toString())
        manifestPlaceholders["admobAppId"] = admobAppId.ifBlank { "ca-app-pub-3940256099942544~3347511713" }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        // R8 shrinks and optimizes the DEX code and drops unused resources; Play flags unoptimized DEX.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The release build as shipped (R8 on), but debug-signed and installed beside the Play app for device checks.
        create("releaseCheck") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".verification"
            matchingFallbacks += "release"
            buildConfigField("Boolean", "TEST_ADS", "false")
        }
    }
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
        buildTypes.getByName("debug") { applicationIdSuffix = ".verification"; buildConfigField("Boolean", "TEST_ADS", "false") }
        // A minified build beside the installed Play app, for measuring real performance on a personal device.
        buildTypes.getByName("release") { applicationIdSuffix = ".verification" }
    }
    // Every supported language must be available offline after an in-app language change.
    bundle { language { enableSplit = false } }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":domain"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Material 3 no longer brings the icon set; only the core icons (ArrowBack) are used.
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Room 2.8 schema bundles (room-testing) need serialization-core 1.8; a transitive 1.7.3 pin otherwise wins.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.android.installreferrer:installreferrer:2.2")
    // Installs the bundled baseline profiles (Compose, app) at install time for faster start and scrolling.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Avoid reflection on InputManager.getInstance, removed on newer Android images.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
