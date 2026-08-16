import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "com.wordtomarkdown"
version = "1.0.0"

kotlin {
    // Java 21, igual que el subproyecto Java
    jvmToolchain(21)

    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // material3 se versiona aparte del resto de Compose Multiplatform
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                // Mismas librerías de conversión que la versión Java
                implementation("org.zwobble.mammoth:mammoth:1.12.0")
                implementation("com.vladsch.flexmark:flexmark-html2md-converter:0.64.8")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// El toolchain fija con qué JDK se *compila*, pero `run` usaría el JDK con el que
// corre Gradle (que puede ser anterior y no entender el bytecode 21). Se apunta a
// la misma instalación de Java 21 para que ejecutar no dependa del entorno.
val java21Home: String = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}.get().metadata.installationPath.asFile.absolutePath

compose.desktop {
    application {
        mainClass = "com.wordtomarkdown.MainKt"
        javaHome = java21Home

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WordToMarkdown"
            packageVersion = "1.0.0"
        }
    }
}
