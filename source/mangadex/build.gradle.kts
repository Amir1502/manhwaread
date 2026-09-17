plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Контракт Source/SManga/SourceException; AppError и OkHttp приходят транзитивно.
    api(project(":source:api"))
    // RateLimiter, asAppError и CloudflareBlocked-семантика сетевого слоя.
    implementation(project(":core:network"))
    // Разбор JSON API v5 без DTO.
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
