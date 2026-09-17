plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Виртуальное время kotlinx-coroutines-test (advanceTimeBy и др.) помечено экспериментальным.
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    // AppError участвует в маппинге сетевых ошибок (asAppError).
    api(project(":core:common"))
    // Типы OkHttp (Interceptor, OkHttpClient) фигурируют в публичном API модуля.
    api(libs.okhttp)

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
