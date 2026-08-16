import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "com.wordtomarkdown"
version = "1.0.0"

// material3 se versiona aparte del resto de Compose Multiplatform
val material3Version = "1.9.0"
val mammothVersion = "1.12.0"
val flexmarkVersion = "0.64.8"

kotlin {
    // Java 21, igual que el subproyecto Java
    jvmToolchain(21)

    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.compose.material3:material3:$material3Version")
                // Mismas librerías de conversión que la versión Java
                implementation("org.zwobble.mammoth:mammoth:$mammothVersion")
                implementation("com.vladsch.flexmark:flexmark-html2md-converter:$flexmarkVersion")
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

/*
 * JAR autónomo para Windows.
 *
 * Compose dibuja con Skia, que es código nativo: el JAR normal (`jvmJar`) arrastra
 * las librerías de la máquina donde se compila, así que uno construido en macOS no
 * arranca en Windows. Esta configuración pide explícitamente las nativas de
 * Windows x64, y la tarea `windowsJar` las empaqueta junto al código y al resto de
 * dependencias en un único archivo ejecutable con `java -jar`.
 */
val windowsRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    windowsRuntime(compose.desktop.windows_x64)
    windowsRuntime("org.jetbrains.compose.material3:material3:$material3Version")
    windowsRuntime("org.zwobble.mammoth:mammoth:$mammothVersion")
    windowsRuntime("com.vladsch.flexmark:flexmark-html2md-converter:$flexmarkVersion")
}

tasks.register<Jar>("windowsJar") {
    group = "distribution"
    description = "JAR autónomo ejecutable en Windows (java -jar)"

    archiveBaseName.set("word-to-markdown-kt")
    archiveClassifier.set("windows-x64")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.wordtomarkdown.MainKt"
    }

    // Código y recursos propios
    from(kotlin.jvm().compilations.getByName("main").output.allOutputs)

    // Dependencias, desempaquetadas dentro del JAR
    from({
        windowsRuntime.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })

    // Las firmas de los JAR originales dejan de ser válidas al reempaquetar
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    exclude("META-INF/versions/*/module-info.class", "module-info.class")
}

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
