plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/** Reads a value from -P / gradle.properties first, then the environment (used by CI secrets). */
fun config(name: String): String? =
    (providers.gradleProperty(name).orNull ?: System.getenv(name))?.takeIf { it.isNotBlank() }

// Google's public test ad IDs. Release builds use real IDs when the secrets are set.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBanner = "ca-app-pub-3940256099942544/9214589741"
val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
val testRewarded = "ca-app-pub-3940256099942544/5224354917"

val releaseAds = listOf("ADMOB_APP_ID", "ADMOB_BANNER_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_REWARDED_ID")
    .associateWith { config(it) }
val hasRealAds = releaseAds.values.all { it != null }
fun releaseAd(name: String, test: String): String = if (hasRealAds) releaseAds.getValue(name)!! else test

// Store builds (CI sets REQUIRE_RELEASE_CONFIG=true on version tags) must never ship test ads,
// a debug signature, or a placeholder support address.
if (config("REQUIRE_RELEASE_CONFIG") == "true") {
    val missing = buildList {
        if (!hasRealAds) add("ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID / ADMOB_REWARDED_ID")
        if (config("KEYSTORE_FILE") == null) add("KEYSTORE_BASE64 (+ passwords)")
        if (config("SUPPORT_EMAIL") == null) add("SUPPORT_EMAIL")
    }
    if (missing.isNotEmpty()) throw GradleException("Release build is missing: ${missing.joinToString(", ")}")
}

android {
    namespace = "com.gridlark.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gridlark.app"
        minSdk = 26
        targetSdk = 36
        versionCode = (config("VERSION_CODE") ?: "1").toInt()
        versionName = config("VERSION_NAME") ?: "1.0.0"
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${config("PRIVACY_POLICY_URL") ?: ""}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${config("SUPPORT_EMAIL") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val store = config("KEYSTORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = config("KEYSTORE_PASSWORD")
                keyAlias = config("KEY_ALIAS")
                keyPassword = config("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "AD_BANNER", "\"$testBanner\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"$testInterstitial\"")
            buildConfigField("String", "AD_REWARDED", "\"$testRewarded\"")
            buildConfigField("boolean", "TEST_ADS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (config("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // No release keystore configured: sign with the debug key so the APK still installs for testing.
                signingConfig = signingConfigs.getByName("debug")
            }
            manifestPlaceholders["admobAppId"] = releaseAd("ADMOB_APP_ID", testAppId)
            buildConfigField("String", "AD_BANNER", "\"${releaseAd("ADMOB_BANNER_ID", testBanner)}\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"${releaseAd("ADMOB_INTERSTITIAL_ID", testInterstitial)}\"")
            buildConfigField("String", "AD_REWARDED", "\"${releaseAd("ADMOB_REWARDED_ID", testRewarded)}\"")
            buildConfigField("boolean", "TEST_ADS", (!hasRealAds).toString())
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "ObsoleteLintCustomCheck")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)
    implementation(libs.review.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
