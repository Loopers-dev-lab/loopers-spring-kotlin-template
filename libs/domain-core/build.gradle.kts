plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    // No external dependencies needed for pure domain objects
    // Tests
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
