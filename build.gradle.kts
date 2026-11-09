plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.azisaba"
version = "1.1.0+1.21.11"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.azisaba.net/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("de.epiceric:ShopChest:2.0.5+1.21.11") {
        isTransitive = false
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude("org.bukkit", "bukkit")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        // Kotlin's stdlib is bundled; relocate it so other Kotlin plugins can't clash with it
        relocate("kotlin", "net.azisaba.shopchestfinder.libs.kotlin")
        exclude("META-INF/*.kotlin_module")
        minimize()
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
}
