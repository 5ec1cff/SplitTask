import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = if (keystorePropertiesFile.exists() && keystorePropertiesFile.isFile) {
    Properties().apply {
        load(FileInputStream(keystorePropertiesFile))
    }
} else null

android {
    namespace = "io.github.a13e300.splittask"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.a13e300.splittask"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSig = signingConfigs.findByName("release")
            signingConfig = if (releaseSig != null) releaseSig else {
                println("use debug signing config")
                signingConfigs["debug"]
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    compileOnly(libs.androidx.annotation)
}