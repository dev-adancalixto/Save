plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Versión: la pasa el CI con -PappVersion=1.2.3 (desde la etiqueta v1.2.3).
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "0.0.0-dev"

fun versionCodeOf(v: String): Int {
    val p = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    return (p[0] * 10000 + p[1] * 100 + p[2]).coerceAtLeast(1)
}

// Repositorio de GitHub donde la app busca nuevas versiones.
val updateRepo: String = (findProperty("updateRepo") as String?) ?: "dev-adancalixto/Save"

android {
    namespace = "com.save.finanzas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.save.finanzas"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        // Llave fija para que cada versión nueva se instale encima de la anterior.
        create("shared") {
            storeFile = rootProject.file("keystore/save.jks")
            storePassword = "savesave"
            keyAlias = "save"
            keyPassword = "savesave"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
}
