plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Виртуальное время kotlinx-coroutines-test (currentTime и др.) помечено экспериментальным.
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    // Корутины — часть публичного API модуля (AppDispatchers, Retry, CircuitBreaker).
    api(libs.kotlinx.coroutines.core)
    // TestAppDispatchers живёт в main source set: test-API доступно потребителям транзитивно.
    api(libs.kotlinx.coroutines.test)

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
