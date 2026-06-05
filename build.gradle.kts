plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}


tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
        jvmArgs("-Xms2G", "-Xmx2G")
    }
    shadowJar {
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
