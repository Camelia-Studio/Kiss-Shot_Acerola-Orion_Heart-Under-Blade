import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "fr.melaine.gerard.kiss.shot.acerola"
version = "1.0-SNAPSHOT"
application {
    mainClass = "org.camelia.studio.kiss.shot.acerola.KissShotAcerola"
}
repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.2")
    implementation("net.dv8tion:JDA:5.2.1")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation ("dev.arbjerg:lavaplayer:2.2.2")
}


tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    manifest {
        attributes["Main-Class"] = "org.camelia.studio.kiss.shot.acerola.KissShotAcerola"
    }

    archiveFileName.set("kiss-shot-acerola.jar")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}