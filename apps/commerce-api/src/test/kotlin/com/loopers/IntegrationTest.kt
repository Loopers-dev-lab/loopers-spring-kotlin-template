package com.loopers

import com.loopers.utils.DatabaseCleanUp
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
abstract class IntegrationTest {

    @Autowired
    private lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }
}
