plugins { id("org.jetbrains.kotlin.plugin.jpa") }

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

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // batch
    implementation("org.springframework.boot:spring-boot-starter-batch")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
//  testImplementation("org.springframework.batch:spring-batch-test")
}
