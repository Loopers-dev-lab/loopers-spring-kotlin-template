plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "io.github.resilience4j") {
                useVersion("2.3.0")
                because("Force resilience4j version to 2.3.0 to avoid Spring Cloud BOM downgrade")
            }
        }
    }
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // retry & resilience
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("io.github.resilience4j:resilience4j-rxjava3:2.3.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // feign client
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.3.0")

    // kafka
    implementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility:4.2.0")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
