package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.instancio.Instancio
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PointWalletTest {
    @DisplayName("거래 추가 테스트")
    @Nested
    inner class AddTransaction {
        lateinit var sut: PointWallet

        @BeforeEach
        fun setUp() {
            val userId = 1L
            sut = PointWallet.of(userId)
        }

        @DisplayName("같은 유저의 포인트 충전을 추가할 수 있다.")
        @Test
        fun addTransaction_whenSameUserIdIsProvided() {
            // when
            val correctAmount = Instancio.of(Money::class.java)
                .create()
            val chargedTransaction = PointTransaction.charge(sut.userId, correctAmount)
            sut.addTransaction(chargedTransaction)

            // then
            assertThat(sut.transactions()).hasSize(1)
            assertThat(sut.transactions().first().transactionType).isEqualTo(PointTransactionType.CHARGE)
        }

        @DisplayName("같은 유저의 포인트 충전을 여러개 추가할 수 있다.")
        @Test
        fun addTransaction_whenMultipleSameUserIdIsProvided() {
            // when
            val correctAmount = Instancio.of(Money::class.java)
                .create()
            val chargedTransaction = PointTransaction.charge(sut.userId, correctAmount)
            sut.addTransaction(chargedTransaction)
            sut.addTransaction(chargedTransaction)

            // then
            assertThat(sut.transactions()).hasSize(2)
            assertThat(sut.transactions().first().transactionType).isEqualTo(PointTransactionType.CHARGE)
            assertThat(sut.transactions().last().transactionType).isEqualTo(PointTransactionType.CHARGE)
        }

        @DisplayName("다른 유저의 포인트 충전을 추가할 수 없다.")
        @Test
        fun throwsException_whenDifferentUserIdIsProvided() {
            // when
            val correctAmount = Instancio.of(Money::class.java)
                .create()
            val chargedTransaction = PointTransaction.charge(sut.userId + 1, correctAmount)
            val exception = assertThrows<CoreException> { sut.addTransaction(chargedTransaction) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("id가 ${sut.userId} 와 ${sut.userId + 1} 가 달라 충전이 불가능합니다.")
        }
    }
}
