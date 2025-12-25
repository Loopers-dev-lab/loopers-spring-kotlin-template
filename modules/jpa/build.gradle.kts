plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
    id("com.google.devtools.ksp")
    `java-test-fixtures`
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

dependencies {
    val queryDslVersion: String by project

    // jpa
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    // querydsl
    api("io.github.openfeign.querydsl:querydsl-jpa:$queryDslVersion")
    ksp("io.github.openfeign.querydsl:querydsl-ksp-codegen:$queryDslVersion")
    // jdbc-mysql
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.testcontainers:mysql")

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testFixturesImplementation("org.testcontainers:mysql")
}
