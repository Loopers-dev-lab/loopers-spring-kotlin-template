package com.loopers.domain.user

import com.loopers.domain.user.fixture.UserServiceIntegrationFixture
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
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
        fun `해당 ID 의 회원이 존재할 경우, 회원 정보가 반환된다`() {
            // given
            val exampleModel = UserServiceIntegrationFixture.saveUser(userRepository)

            // when
            val result = userService.getMe(exampleModel.userName.value)

            // then
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result?.id).isEqualTo(exampleModel.id) },
                { assertThat(result?.userName).isEqualTo(exampleModel.userName) },
                { assertThat(result?.gender).isEqualTo(exampleModel.gender) },
                { assertThat(result?.birthDate).isEqualTo(exampleModel.birthDate) },
                { assertThat(result?.email).isEqualTo(exampleModel.email) },
            )
        }

        @Test
        fun `해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다`() {
            // given
            val userName = "userName"

            // when
            val result = userService.getMe("userName")

            // then
            assertThat(result).isNull()
        }
    }
}
