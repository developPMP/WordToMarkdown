pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

/*
 * Java no usa el almacén de certificados del sistema: trae el suyo (cacerts). En
 * equipos Windows con un proxy corporativo o un antivirus que inspecciona HTTPS,
 * los certificados vienen firmados por una CA que Windows sí conoce pero Java no,
 * y la descarga de dependencias falla con:
 *
 *     PKIX path building failed: unable to find valid certification path
 *
 * Delegar en el almacén de Windows evita tener que importar la CA en el JDK. Solo
 * se aplica en Windows: el tipo "Windows-ROOT" no existe en macOS ni Linux.
 */
if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
    System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "word-to-markdown-kt"
