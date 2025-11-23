plugins {
    `java-test-fixtures`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")

    testFixturesImplementation("com.redis:testcontainers-redis")
    testFixturesImplementation("org.springframework.boot:spring-boot-test")
}
