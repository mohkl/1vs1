plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val downloadUrl: String = (project.findProperty("downloadUrl") as String?)
    ?: "https://github.com/rFM/1vs1/releases/latest"

base.archivesName.set("1vs1-v2.0.1")

android {
    namespace = "com.rfm.onevsone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rfm.onevsone"
        minSdk = 26
        targetSdk = 34
        versionCode = 201
        versionName = "2.0.1"
        buildConfigField("String", "DOWNLOAD_URL", "\"$downloadUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // توقيع بمفتاح debug ليكون ملف APK قابلاً للتثبيت مباشرة
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")
}
