package com.loopers.domain.point

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.test.KSelect
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.instancio.Instancio
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class PointServiceIntegrationTest @Autowired constructor(
    private val pointService: PointService,
    private val userRepository: UserRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("포인트 충전 통합테스트")
    @Nested
    inner class Charge {
        var user: User? = null

        @BeforeEach
        fun setUp() {
            val mockUser = Instancio.of(User::class.java)
                .ignore(KSelect.field(User::id))
                .set(KSelect.field(User::username), "김준형")
                .set(KSelect.field(User::birth), LocalDate.of(1994, 9, 23))
                .set(KSelect.field(User::email), "toong@toong.io")
                .set(KSelect.field(User::gender), Gender.MALE)
                .create()
            user = userRepository.save(mockUser)
        }

        @DisplayName("존재하지 않는 유저 id로 충전을 할 수 없다.")
        @Test
        fun throwsException_whenInvalidUserIdIsProvided() {
            // when
            val invalidUserId = 999L
            val correctAmount = Money.krw(1000)
            val exception = assertThrows<CoreException> { pointService.charge(invalidUserId, correctAmount) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).isEqualTo("[id = $invalidUserId] 유저를 찾을 수 없습니다.")
        }

        @DisplayName("존재하는 유저 id로 포인트를 충전할 수 있다.")
        @Test
        fun chargePoint_whenValidUserIdIsProvided() {
            // given
            val existUser = user!!

            // when
            val correctAmount = Money.krw(1000)
            val wallet = pointService.charge(existUser.id, correctAmount)

            // then
            val pointTransactions = wallet.transactions()
            assertAll(
                { assertThat(wallet.userId).isEqualTo(existUser.id) },
                { assertThat(pointTransactions).hasSize(1) },
                { assertThat(pointTransactions[0].amount).isEqualTo(correctAmount) },
                { assertThat(pointTransactions[0].transactionType).isEqualTo(PointTransactionType.CHARGE) },
            )
        }
    }
}
