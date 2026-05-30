plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.silkmonad"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
    implementation("org.web3j:crypto:4.12.0")
    implementation("org.web3j:abi:4.12.0")
    implementation("org.web3j:utils:4.12.0")
    implementation("org.web3j:rlp:4.12.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    shadowJar {
        archiveClassifier.set("")
        // Relocate web3j + its bouncycastle dep so we never collide with whatever
        // BC the server or other plugins ship.
        relocate("org.bouncycastle", "com.silkmonad.shaded.bouncycastle")
        relocate("org.web3j", "com.silkmonad.shaded.web3j")
        mergeServiceFiles()
    }
    build {
        dependsOn(shadowJar)
    }
}
