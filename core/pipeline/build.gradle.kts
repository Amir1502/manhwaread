plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // DomainResult/AppError — язык результатов и ошибок конвейера.
    api(project(":core:common"))
    // TextSegment/OverlaySpec — данные стадий анализа и типографики.
    api(project(":core:vision-model"))
    // TranslatedSegment — результат стадии перевода.
    api(project(":core:translation-api"))
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
