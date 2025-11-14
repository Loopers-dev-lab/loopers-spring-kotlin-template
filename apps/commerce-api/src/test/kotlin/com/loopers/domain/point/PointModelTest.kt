package com.loopers.domain.point

import com.loopers.domain.common.vo.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class PointModelTest {

    @DisplayName("포인트 충전")
    @Nested
    inner class Charge {

        @DisplayName("요청한 금액만큼 포인트가 충전된다")
        @Test
        fun chargeSuccess() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.valueOf(1000))

            // act
            val newBalance = pointModel.charge(Money(BigDecimal.valueOf(500)))

            // assert
            assertThat(newBalance.amount).isEqualTo(BigDecimal.valueOf(1500))
            assertThat(pointModel.balance.amount).isEqualTo(BigDecimal.valueOf(1500))
        }

        @DisplayName("0원으로 포인트를 충전할 수 없다")
        @Test
        fun chargeFails_whenAmountIsZero() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.ZERO)

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                pointModel.charge(Money(BigDecimal.ZERO))
            }
            assertThat(exception.message).isEqualTo("충전 금액은 0보다 커야 합니다.")
        }
    }

    @DisplayName("포인트 사용")
    @Nested
    inner class Pay {

        @DisplayName("요청한 금액만큼 포인트가 차감된다")
        @Test
        fun paySuccess() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.valueOf(1000))

            // act
            val newBalance = pointModel.pay(Money(BigDecimal.valueOf(300)))

            // assert
            assertThat(newBalance.amount).isEqualTo(BigDecimal.valueOf(700))
            assertThat(pointModel.balance.amount).isEqualTo(BigDecimal.valueOf(700))
        }

        @DisplayName("잔액과 동일한 금액을 사용할 수 있다")
        @Test
        fun paySuccess_whenAmountEqualsBalance() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.valueOf(1000))

            // act
            val newBalance = pointModel.pay(Money(BigDecimal.valueOf(1000)))

            // assert
            assertThat(newBalance.amount).isEqualTo(BigDecimal.ZERO)
            assertThat(pointModel.balance.amount).isEqualTo(BigDecimal.ZERO)
        }

        @DisplayName("0원으로 포인트를 사용할 수 없다")
        @Test
        fun payFails_whenAmountIsZero() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.valueOf(1000))

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                pointModel.pay(Money(BigDecimal.ZERO))
            }
            assertThat(exception.message).isEqualTo("사용 금액은 0보다 커야 합니다.")
        }

        @DisplayName("잔액보다 많은 금액을 사용할 수 없다")
        @Test
        fun payFails_whenAmountExceedsBalance() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.valueOf(1000))

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                pointModel.pay(Money(BigDecimal.valueOf(1500)))
            }
            assertThat(exception.message).isEqualTo("잔액이 부족합니다.")
        }

        @DisplayName("잔액이 0원일 때 포인트를 사용할 수 없다")
        @Test
        fun payFails_whenBalanceIsZero() {
            // arrange
            val pointModel = PointFixture.create(userId = 1L, balance = BigDecimal.ZERO)

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                pointModel.pay(Money(BigDecimal.valueOf(100)))
            }
            assertThat(exception.message).isEqualTo("잔액이 부족합니다.")
        }
    }
}
