package com.loopers.application.point

import com.loopers.application.point.fixture.PointFacadeIntegrationFixture
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PointFacadeIntegrationTest @Autowired constructor(
    private val pointFacade: PointFacade,
    private val userRepository: UserJpaRepository,
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
        fun `해당 ID의 회원이 존재할 경우, 보유 포인트가 반환된다`() {
            // given
            val user = PointFacadeIntegrationFixture.saveUser(userRepository)

            // when
            val result = pointFacade.getMe(user.id)

            // then
            assertThat(result).isNotNull()
            assertThat(result?.amount).isEqualTo(0)
        }
    }

    @DisplayName("충전")
    @Nested
    inner class Charge {
        @Test
        fun `존재하지_않는 유저 ID 로 충전을 시도한 경우, 실패한다`() {
            // when & then
            val exception = assertThrows<CoreException> {
                pointFacade.charge(PointInfo.Charge.of("invalid", 1000))
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
