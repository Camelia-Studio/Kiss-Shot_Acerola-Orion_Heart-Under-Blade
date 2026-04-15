plugins {
    application
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
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
    implementation("org.hibernate.orm:hibernate-core:7.3.1.Final")
    implementation("org.hibernate.orm:hibernate-hikaricp:7.3.1.Final")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("net.dv8tion:JDA:6.4.1")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("dev.arbjerg:lavaplayer:2.2.6")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
}


tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<Jar>("shadowJar") {
    manifest {
        attributes["Main-Class"] = "org.camelia.studio.kiss.shot.acerola.KissShotAcerola"
    }

    archiveFileName.set("kiss-shot-acerola.jar")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}