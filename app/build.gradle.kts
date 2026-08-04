import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orbyte.canvasstudio"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.orbyte.canvasstudio"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "2.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }
    }

    val signingPropertiesFile = rootProject.file("keystore.properties")
    val signingProperties = Properties().apply {
        if (signingPropertiesFile.isFile) {
            FileInputStream(signingPropertiesFile).use(::load)
        }
    }
    signingConfigs {
        if (signingPropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            // Keeps development builds installable alongside a signed release, without touching user projects.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // The desktop sandbox may expose a different default debug keystore between
            // invocations. Reusing the configured local signing identity keeps the app and
            // instrumentation APK installable as a stable pair on the tablet.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    val inkVersion = "1.0.0"
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 2.11.0 requires compileSdk 37 and AGP 9.1; 2.10 keeps the API 36 release line stable.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.ink:ink-authoring:$inkVersion")
    implementation("androidx.ink:ink-brush:$inkVersion")
    implementation("androidx.ink:ink-geometry:$inkVersion")
    implementation("androidx.ink:ink-nativeloader:$inkVersion")
    implementation("androidx.ink:ink-rendering:$inkVersion")
    implementation("androidx.ink:ink-strokes:$inkVersion")
    implementation("androidx.ink:ink-storage:$inkVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
