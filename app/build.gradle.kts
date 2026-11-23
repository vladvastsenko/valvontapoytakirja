plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cloudKeystorePath = System.getenv("CLOUD_KEYSTORE_PATH")
val cloudKeystorePassword = System.getenv("CLOUD_KEYSTORE_PASSWORD")
val cloudKeyAlias = System.getenv("CLOUD_KEY_ALIAS")

android {
    namespace = "fi.vladi.valvontapoytakirja"
    compileSdk = 35

    defaultConfig {
        applicationId = "fi.vladi.tarkastuscloud"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "0.6.18-test"
    }

    if (!cloudKeystorePath.isNullOrBlank() &&
        !cloudKeystorePassword.isNullOrBlank() &&
        !cloudKeyAlias.isNullOrBlank()
    ) {
        signingConfigs.create("cloud") {
            storeFile = file(cloudKeystorePath)
            storePassword = cloudKeystorePassword
            keyAlias = cloudKeyAlias
            keyPassword = cloudKeystorePassword
        }
        buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("cloud")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":tarkastus"))
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
