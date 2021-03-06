//ext.kotlin.version = "1.6.10"

plugins {
    kotlin("multiplatform") version "1.6.10"
}

group = "me.rfirmin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val luciferTarget = when {
        hostOs == "Mac OS X" -> macosX64("lucifer")
        hostOs == "Linux" -> linuxX64("lucifer")
        isMingwX64 -> mingwX64("lucifer")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    luciferTarget.apply {
        compilations["main"].enableEndorsedLibs = false
        compilations["main"].cinterops {
            val ncurses by creating {

            }
        }

        binaries {
            executable(listOf(DEBUG, RELEASE)) {
                entryPoint = "main"
            }
        }
    }
    sourceSets {
        val luciferMain by getting
        val luciferTest by getting
    }

    // this is here to fool intellij into being able to run tests
    jvm {}
}
