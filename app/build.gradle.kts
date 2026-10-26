plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.digitalsolverland.opencode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.digitalsolverland.opencode"
        minSdk = 26
        targetSdk = 28
        versionCode = 7
        versionName = "2.2.3"
    }

    /**
     * Clé de signature stable, versionnée dans le dépôt : elle garantit que
     * chaque nouvel APK peut être installé par-dessus le précédent.
     * (Clé de distribution interne, sans secret : le code est public.)
     */
    signingConfigs {
        create("stable") {
            storeFile = file("keystore/opencode-release.jks")
            storePassword = System.getenv("OPENCODE_KEYSTORE_PASSWORD") ?: "opencode-mobile"
            keyAlias = System.getenv("OPENCODE_KEY_ALIAS") ?: "opencode"
            keyPassword = System.getenv("OPENCODE_KEY_PASSWORD") ?: "opencode-mobile"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    
    // Markdown rendering (simple)
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
}
