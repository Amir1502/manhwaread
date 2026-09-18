plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.manhwaread.feature.downloads"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // ChapterAnalyzer/PageStore/ChapterJob — контракты конвейера; transitively
    // тянет :core:common (DomainResult/AppError) и :core:vision-model (Bubble/TextSegment).
    api(project(":core:pipeline"))

    // DAO очереди загрузок и задач перевода для экрана «Загрузки» (ФАЗА 14);
    // transitively даёт :core:model (DownloadStatus).
    implementation(project(":core:database"))

    // Реестр источников и контракты Source/SChapter/Page для загрузчика очереди (ФАЗА 15).
    implementation(project(":source:api"))
    // asAppError + OkHttpClient (api-транзитивно) для скачивания страниц глав.
    implementation(project(":core:network"))
    // Настройки перевода и ключи: очередь запускает перевод только при выбранном провайдере.
    implementation(project(":core:datastore"))
    // Кодеки каталога главы (chapter.json/overlays.json): владелец формата — :feature:reader,
    // переиспользуем их, чтобы писатель архива и читалка не разошлись (ФАЗА 15).
    implementation(project(":feature:reader"))

    // Vision-движки стадии анализа (ФАЗА 11).
    implementation(libs.opencv)
    implementation(libs.onnxruntime.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.japanese)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.junit)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
