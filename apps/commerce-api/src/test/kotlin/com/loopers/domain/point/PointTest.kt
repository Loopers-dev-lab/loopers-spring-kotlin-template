package com.loopers.domain.point

import com.loopers.domain.order.Money
import com.loopers.domain.product.Currency
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PointTest {
    @Test
    fun `포인트를 생성할 수 있다`() {
        // given
        val userId = 1L
        val balance = Money(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when
        val point = Point(userId = userId, balance = balance)

        // then
        assertThat(point.userId).isEqualTo(userId)
        assertThat(point.balance).isEqualTo(balance)
    }

    @Test
    fun `포인트를 충전할 수 있다`() {
        // given
        val point = Point(userId = 1L, balance = Money(BigDecimal("10000"), Currency.KRW))
        val chargeAmount = Money(BigDecimal("5000"), Currency.KRW)

        // when
        point.charge(chargeAmount)

        // then
        assertThat(point.balance.amount).isEqualTo(BigDecimal("15000"))
    }

    @Test
    fun `포인트를 차감할 수 있다`() {
        // given
        val point = Point(userId = 1L, balance = Money(BigDecimal("10000"), Currency.KRW))
        val deductAmount = Money(BigDecimal("3000"), Currency.KRW)

        // when
        point.deduct(deductAmount)

        // then
        assertThat(point.balance.amount).isEqualTo(BigDecimal("7000"))
    }

    @Test
    fun `잔액보다 많이 차감하면 예외가 발생한다`() {
        // given
        val point = Point(userId = 1L, balance = Money(BigDecimal("10000"), Currency.KRW))
        val deductAmount = Money(BigDecimal("11000"), Currency.KRW)

        // when & then
        assertThatThrownBy {
            point.deduct(deductAmount)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("포인트 부족")
    }

    @Test
    fun `차감 가능 여부를 확인할 수 있다`() {
        // given
        val point = Point(userId = 1L, balance = Money(BigDecimal("10000"), Currency.KRW))

        // when & then
        assertThat(point.canDeduct(Money(BigDecimal("5000"), Currency.KRW))).isTrue()
        assertThat(point.canDeduct(Money(BigDecimal("10000"), Currency.KRW))).isTrue()
        assertThat(point.canDeduct(Money(BigDecimal("10001"), Currency.KRW))).isFalse()
    }
}
