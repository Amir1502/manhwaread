plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Контракт Source/SManga/SourceException + парсеры дат/номеров глав.
    api(project(":source:api"))
    // RateLimiter, asAppError, CloudflareBlocked-семантика; OkHttp транзитивно.
    implementation(project(":core:network"))
    // HTML-парсинг тем Madara (WordPress).
    implementation(libs.jsoup)

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
