import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Версия Jacoco захватывается в корне: внутри subprojects {} аксессор libs недоступен.
val jacocoToolVersion = libs.versions.jacoco.get()

// Статический анализ подключается ко всем модулям единообразно.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    // Покрытие: единая версия инструмента; .exec пишутся тестовыми задачами автоматически.
    apply(plugin = "jacoco")
    configure<JacocoPluginExtension> {
        toolVersion = jacocoToolVersion
    }
}

// Jacoco-плагин в корне: сводной задаче JacocoReport нужны agent classpath
// и конвенции отчётов (без плагина валидация задачи падает).
apply(plugin = "jacoco")
configure<JacocoPluginExtension> {
    toolVersion = jacocoToolVersion
}

// Классы, сгенерированные компиляторами/процессорами, в отчёт покрытия не входят.
val jacocoGeneratedPatterns = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*_Impl*.*",
    "**/*_Factory*.*",
    "**/*_MembersInjector*.*",
    "**/Dagger*.*",
    "**/Hilt_*.*",
    "**/*_HiltModules*.*",
    "**/*_GeneratedInjector*.*",
    "**/ComposableSingletons*.*",
    "**/*Kt$*.*",
    "**/*\$*Inlined\$*.*",
)

// Сводный отчёт покрытия по всем модулям: ./gradlew build jacocoFullReport.
tasks.register<JacocoReport>("jacocoFullReport") {
    group = "verification"
    description = "Сводный отчёт Jacoco по всем модулям"
    dependsOn(subprojects.flatMap { sub -> sub.tasks.withType(Test::class.java) })
    classDirectories.setFrom(
        files(
            subprojects.map { sub ->
                sub.fileTree(sub.layout.buildDirectory) {
                    include(
                        "classes/kotlin/main/**/*.class",
                        "tmp/kotlin-classes/debug/**/*.class",
                    )
                    exclude(jacocoGeneratedPatterns)
                }
            },
        ),
    )
    sourceDirectories.setFrom(files(subprojects.map { sub -> sub.file("src/main/kotlin") }))
    executionData.setFrom(
        files(
            subprojects.map { sub ->
                sub.fileTree(sub.layout.buildDirectory) {
                    include(
                        "jacoco/*.exec",
                        "outputs/unit_test_code_coverage/**/*.exec",
                    )
                }
            },
        ),
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoFullReport/jacocoFullReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoFullReport/html"))
    }
}
