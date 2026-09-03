import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Load signing config from keystore.properties if present (gitignored).
// Required keys: storeFile, storePassword, keyAlias, keyPassword.
// When absent, the release build is produced unsigned.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}
val hasSigningConfig = keystorePropertiesFile.exists()

android {
    namespace = "io.github.moranyue.nodhcphostname"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.moranyue.nodhcphostname"
        minSdk = 29
        targetSdk = 37
        versionCode = 101
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
}
