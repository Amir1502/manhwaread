import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Параметры релизной подписи читаются из keystore.properties (файл не коммитится).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.manhwaread.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.manhwaread.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Без keystore.properties (например, в CI) откатываемся к debug-подписи.
            signingConfig =
                if (keystorePropertiesFile.exists()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    // Сплиты по ABI: нативные библиотеки (OpenCV, ONNX, ML Kit) раздувают
    // универсальный APK до ~260 МБ; одному устройству нужна одна архитектура.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric-тесты запуска Activity и Hilt-графа требуют ресурсов Android.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    // Флаг онбординга для корневого гейта (ФАЗА 14).
    implementation(project(":core:datastore"))
    // Настройки провайдера перевода подключены к NavHost (ФАЗА 12).
    implementation(project(":feature:settings"))
    // Разделы UI подключены к NavHost (ФАЗА 14).
    implementation(project(":feature:library"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:history"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:details"))
    implementation(project(":feature:onboarding"))
    // Читалка: офлайн-маршрут главы из каталога очереди (ФАЗА 15).
    implementation(project(":feature:reader"))
    // Источники и сетевой слой регистрируются в SourceModule (ФАЗА 13).
    implementation(project(":core:network"))
    implementation(project(":source:api"))
    implementation(project(":source:mangadex"))
    implementation(project(":source:madara"))
    implementation(project(":source:asura"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Robolectric + Hilt: проверка запуска приложения и DI-графа на JVM.
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.hilt.android.testing)
    // Стриминг главы без скачивания: HTTP-фейк и моки финального загрузчика.
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    kspTest(libs.hilt.compiler)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
