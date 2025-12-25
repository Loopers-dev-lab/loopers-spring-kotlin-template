import org.gradle.api.Project.DEFAULT_VERSION
import org.springframework.boot.gradle.tasks.bundling.BootJar

/** --- configuration functions --- */
fun getGitHash(): String {
    return runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
    }.getOrElse { "init" }
}

/** --- project configurations --- */
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") apply false
    kotlin("plugin.spring") apply false
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("org.jlleitschuh.gradle.ktlint") apply false
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        jvmToolchain(25)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allprojects {
    val projectGroup: String by project
    group = projectGroup
    version = if (version == DEFAULT_VERSION) getGitHash() else version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${project.properties["springCloudDependenciesVersion"]}")
            mavenBom("tools.jackson:jackson-bom:${project.properties["jacksonVersion"]}")
        }
    }

    // Allow both Jackson 2 and Jackson 3 to coexist
    // kafka-clients 4.1.1 requires Jackson 2 (com.fasterxml.jackson)
    // while Spring components use Jackson 3 (tools.jackson)
    // Spring Boot 4 supports running both versions side-by-side
    // Note: We keep jackson-annotations from Jackson 2 for compatibility

    dependencies {
        // Kotlin
        runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        // Spring
        implementation("org.springframework.boot:spring-boot-starter")
        // Serialize
        implementation("tools.jackson.module:jackson-module-kotlin")
        // Test
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        // testcontainers:mysql 이 jdbc 사용함
        testRuntimeOnly("com.mysql:mysql-connector-j")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("com.ninja-squad:springmockk:${project.properties["springMockkVersion"]}")
        testImplementation("org.mockito:mockito-core:${project.properties["mockitoVersion"]}")
        testImplementation("org.mockito.kotlin:mockito-kotlin:${project.properties["mockitoKotlinVersion"]}")
        testImplementation("org.instancio:instancio-junit:${project.properties["instancioJUnitVersion"]}")
        // Testcontainers
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:testcontainers")
        testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    }

    tasks.withType(Jar::class) { enabled = true }
    tasks.withType(BootJar::class) { enabled = false }

    configure(allprojects.filter { it.parent?.name.equals("apps") }) {
        tasks.withType(Jar::class) { enabled = false }
        tasks.withType(BootJar::class) { enabled = true }
    }

    tasks.test {
        maxParallelForks = 1
        useJUnitPlatform()
        systemProperty("user.timezone", "Asia/Seoul")
        systemProperty("spring.profiles.active", "test")
        jvmArgs("-Xshare:off")
    }

    tasks.withType<JacocoReport> {
        mustRunAfter("test")
        executionData(fileTree(layout.buildDirectory.asFile).include("jacoco/*.exec"))
        reports {
            xml.required = true
            csv.required = false
            html.required = false
        }
        afterEvaluate {
            classDirectories.setFrom(
                files(
                    classDirectories.files.map {
                        fileTree(it)
                    },
                ),
            )
        }
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(properties["ktLintVersion"] as String)
        filter {
            exclude { it.file.path.contains("build${File.separator}generated") }
        }
    }
}

// module-container 는 task 를 실행하지 않도록 한다.
project("apps") { tasks.configureEach { enabled = false } }
project("modules") { tasks.configureEach { enabled = false } }
project("supports") { tasks.configureEach { enabled = false } }
