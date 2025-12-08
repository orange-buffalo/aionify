plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.allopen") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-kotlin-serialization")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Database persistence
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.quarkiverse.jdbi:quarkus-jdbi:1.7.0")
    implementation("org.jdbi:jdbi3-kotlin:3.49.1")
    implementation("org.jdbi:jdbi3-kotlin-sqlobject:3.49.1")

    // Security / Password hashing
    implementation("io.quarkus:quarkus-elytron-security-common")
    implementation("io.quarkus:quarkus-security")

    // JWT authentication - using Auth0 java-jwt for native image compatibility
    implementation("com.auth0:java-jwt:4.4.0")

    // Validation
    implementation("io.quarkus:quarkus-hibernate-validator")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("com.microsoft.playwright:playwright:1.52.0")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
}

// E2E tests source set
sourceSets {
    create("e2eTest") {
        kotlin {
            srcDir("src/e2eTest/kotlin")
        }
        resources {
            srcDir("src/e2eTest/resources")
        }
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

// E2E test dependencies
val e2eTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

dependencies {
    e2eTestImplementation("org.testcontainers:testcontainers:1.20.4")
    e2eTestImplementation("org.testcontainers:junit-jupiter:1.20.4")
    e2eTestImplementation("com.microsoft.playwright:playwright:1.52.0")
    e2eTestImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

group = "io.orange-buffalo"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
    }
}

// Frontend build tasks using Bun
tasks.register<Exec>("bunInstall") {
    workingDir = file("frontend")
    commandLine("bun", "install")
}

tasks.register<Exec>("bunBuild") {
    workingDir = file("frontend")
    commandLine("bun", "run", "build")
    dependsOn("bunInstall")
}

tasks.named("processResources") {
    dependsOn("bunBuild")
}

// Copy frontend dist to META-INF/resources
tasks.named<ProcessResources>("processResources") {
    from("frontend/dist") {
        into("META-INF/resources")
    }
}

// Playwright browser installation task
val playwrightConfig by configurations.creating {
    extendsFrom(configurations.testImplementation.get())
}

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    classpath = playwrightConfig
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps", "chromium")
}

// E2E test task
val e2eTest by tasks.registering(Test::class) {
    description = "Runs E2E tests against Docker image"
    group = "verification"

    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath

    // Must run after the image is built
    mustRunAfter(tasks.build)

    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")

    // Pass the Docker image tag to tests via system property
    // Check is deferred to execution time via doFirst
    doFirst {
        val dockerImage = System.getenv("AIONIFY_DOCKER_IMAGE")
            ?: System.getProperty("aionify.docker.image")
            ?: throw GradleException("E2E tests require AIONIFY_DOCKER_IMAGE environment variable or -Daionify.docker.image system property to be set")

        systemProperty("aionify.docker.image", dockerImage)
    }

    useJUnitPlatform()

    // Generate test reports
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}
