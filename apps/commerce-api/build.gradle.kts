plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
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
    val queryDslVersion: String by project

    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":libs:domain-core"))
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

    // kafka (Producer only - Consumer는 commerce-streamer에서 처리)
    implementation("org.springframework.kafka:spring-kafka")

    // querydsl
    ksp("io.github.openfeign.querydsl:querydsl-ksp-codegen:$queryDslVersion")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
