package com.loopers

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.task.scheduling.enabled=false",
        "spring.batch.jdbc.initialize-schema=always"
    ]
)
class CommerceBatchContextTest {

    @Test
    fun contextLoads() {

    }
}
