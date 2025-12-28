plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

dependencies {

    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":libs:domain-core"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // retry & resilience
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    implementation("io.github.resilience4j:resilience4j-rxjava3")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")

    // feign client
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // kafka (Producer only - Consumer는 commerce-streamer에서 처리)
    implementation("org.springframework.kafka:spring-kafka")

    // Spring Boot 4.0 rest test client
    testImplementation("org.springframework.boot:spring-boot-resttestclient")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
