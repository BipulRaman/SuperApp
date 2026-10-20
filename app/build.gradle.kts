plugins {
    id("com.android.application") version "9.4.1"
}

android {
    namespace = "com.example.superapp"
    compileSdk = 36

    defaultConfig {
        // Must stay com.example.superapp so new builds install over the old Expo-based APKs.
        applicationId = "com.example.superapp"
        minSdk = 26
        targetSdk = 36
        // CI passes -PversionCode=<run number> -PversionName=<tag>; local builds use these defaults.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("1.0.0-dev")
    }

    signingConfigs {
        // CI decodes the stable keystore from the DEBUG_KEYSTORE_BASE64 secret and points
        // SUPERAPP_KEYSTORE at it, so every release is signed with the same key (in-place upgrades).
        // Without it, release builds fall back to the local debug key so they still install.
        create("release") {
            val keystore = providers.environmentVariable("SUPERAPP_KEYSTORE").orNull
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("SUPERAPP_KEYSTORE_PASSWORD").getOrElse("android")
                keyAlias = providers.environmentVariable("SUPERAPP_KEY_ALIAS").getOrElse("androiddebugkey")
                keyPassword = providers.environmentVariable("SUPERAPP_KEY_PASSWORD").getOrElse("android")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // Kotlin reflection metadata; nothing in the app uses reflection.
            excludes += listOf("kotlin/**", "META-INF/*.kotlin_module", "META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 sandbox reaches into JDK internals (FileDescriptor access).
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
