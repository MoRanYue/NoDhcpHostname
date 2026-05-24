plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.moranyue.nodhcphostname"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.moranyue.nodhcphostname"
        minSdk = 29
        targetSdk = 37
        versionCode = 100
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
}