plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // DomainResult/AppError — язык ошибок модуля.
    api(project(":core:common"))
    // DetectedLang — язык оригинала сегментов.
    api(project(":core:vision-model"))
    // OkHttpClient и asAppError — транспорт провайдеров перевода (ФАЗА 12).
    api(project(":core:network"))
    // JSON: разбор ответов LLM и сборка payload промта.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
