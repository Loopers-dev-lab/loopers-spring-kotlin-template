package com.loopers.domain.point

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PointServiceIntegrationTest @Autowired constructor(
    private val pointService: PointService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("조회")
    @Nested
    inner class Get {
        @Test
        fun `해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다`() {
            // given
            val userId = -1L

            // when
            val result = pointService.getMe(userId)

            // then
            assertThat(result).isNull()
        }
    }
}
