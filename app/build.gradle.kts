plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.mahesh.icontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.mahesh.icontrol"
        // 30 for StorageVolume.directory (finding the USB drive).
        minSdk = 30
        // 29, not 36: from 30 up Android hides removable drives from apps unless they hold all-files access, which
        // this TV has no Settings screen to grant. At 29 the legacy-storage opt-in plus a normal permission prompt works.
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded personal build: sign with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Deliberate: see targetSdk above. This is a sideloaded personal build, not a Play upload.
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-material:1.1.0")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    // Talks to this TV's own ADB service, which is the only way an app can restart the TV (see AdbReboot).
    implementation("dev.mobile:dadb:1.2.10")
}
