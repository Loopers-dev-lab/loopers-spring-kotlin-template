plugins {
    `java-test-fixtures`
}

dependencies {
    // Spring Boot 4.0 kafka starter
    api("org.springframework.boot:spring-boot-starter-kafka")

    // Jackson 2 compatibility for Apache Kafka clients
    // kafka-clients 4.1.1 still uses com.fasterxml.jackson package internally
    // while we use Jackson 3 (tools.jackson) for Spring components
    // Spring Boot 4 supports running both versions side-by-side
    // Using Jackson 2.20.1 (latest 2.x) for Kafka compatibility
    api("com.fasterxml.jackson.core:jackson-core")
    api("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testFixturesImplementation("org.testcontainers:testcontainers-kafka")
}
