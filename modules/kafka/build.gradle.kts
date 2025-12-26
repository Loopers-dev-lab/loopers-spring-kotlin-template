plugins {
    `java-test-fixtures`
}

dependencies {
    // Spring Boot 4.0 kafka starter
    api("org.springframework.boot:spring-boot-starter-kafka")

    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testFixturesImplementation("org.testcontainers:testcontainers-kafka")
}
