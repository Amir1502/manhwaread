plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Контракт Source/SManga/SourceException + парсеры дат/номеров глав.
    api(project(":source:api"))
    // RateLimiter, asAppError; OkHttp транзитивно.
    implementation(project(":core:network"))
    // HTML-парсинг Next.js-вёрстки Asura.
    implementation(libs.jsoup)
    // Разбор __NEXT_DATA__ (JSON со списком страниц главы).
    implementation(libs.kotlinx.serialization.json)

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
