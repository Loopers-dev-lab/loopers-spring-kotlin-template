package com.loopers.domain.point

import com.loopers.domain.order.Money
import com.loopers.domain.product.Currency
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class PointServiceTest {
    private val pointRepository = mockk<PointRepository>()
    private val pointService = PointService(pointRepository)

    @DisplayName("포인트를 충전할 때,")
    @Nested
    inner class ChargePoint {
        @DisplayName("포인트가 존재하는 경우, 포인트를 충전한다")
        @Test
        fun chargePoint_whenPointExists_thenSuccess() {
            // given
            val userId = 1L
            val chargeAmount = Money(BigDecimal("10000"), Currency.KRW)
            val point = Point(
                userId = userId,
                balance = Money(BigDecimal("5000"), Currency.KRW),
            )

            every { pointRepository.findByUserId(userId) } returns point
            every { pointRepository.save(any()) } answers { firstArg() }

            // when
            val result = pointService.chargePoint(userId, chargeAmount)

            // then
            assertThat(result.balance.amount).isEqualTo(BigDecimal("15000"))
            verify(exactly = 1) { pointRepository.findByUserId(userId) }
            verify(exactly = 1) { pointRepository.save(any()) }
        }

        @DisplayName("포인트가 존재하지 않는 경우, 예외를 발생시킨다")
        @Test
        fun chargePoint_whenPointNotFound_thenThrowsException() {
            // given
            val userId = 999L
            every { pointRepository.findByUserId(userId) } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                pointService.chargePoint(
                    userId,
                    Money(BigDecimal("10000"), Currency.KRW),
                )
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("포인트 정보를 찾을 수 없습니다")
        }
    }

    @DisplayName("포인트를 조회할 때,")
    @Nested
    inner class GetPoint {
        @DisplayName("포인트가 존재하는 경우, 포인트 정보를 반환한다")
        @Test
        fun getPoint_whenPointExists_thenReturnsPoint() {
            // given
            val userId = 1L
            val point = Point(
                userId = userId,
                balance = Money(BigDecimal("20000"), Currency.KRW),
            )
            every { pointRepository.findByUserId(userId) } returns point

            // when
            val result = pointService.getPoint(userId)

            // then
            assertThat(result).isEqualTo(point)
            verify(exactly = 1) { pointRepository.findByUserId(userId) }
        }

        @DisplayName("포인트가 존재하지 않는 경우, 예외를 발생시킨다")
        @Test
        fun getPoint_whenPointNotFound_thenThrowsException() {
            // given
            val userId = 999L
            every { pointRepository.findByUserId(userId) } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                pointService.getPoint(userId)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("포인트 정보를 찾을 수 없습니다")
        }
    }

    @DisplayName("포인트를 차감할 때,")
    @Nested
    inner class DeductPoint {
        @DisplayName("충분한 포인트가 있는 경우, 포인트를 차감한다")
        @Test
        fun deductPoint_whenSufficientBalance_thenSuccess() {
            // given
            val userId = 1L
            val deductAmount = Money(BigDecimal("5000"), Currency.KRW)
            val point = Point(
                userId = userId,
                balance = Money(BigDecimal("10000"), Currency.KRW),
            )

            every { pointRepository.findByUserIdWithLock(userId) } returns point
            every { pointRepository.save(any()) } answers { firstArg() }

            // when
            val result = pointService.deductPoint(userId, deductAmount)

            // then
            assertThat(result.balance.amount).isEqualTo(BigDecimal("5000"))
            verify(exactly = 1) { pointRepository.findByUserIdWithLock(userId) }
            verify(exactly = 1) { pointRepository.save(any()) }
        }

        @DisplayName("포인트가 부족한 경우, 예외를 발생시킨다")
        @Test
        fun deductPoint_whenInsufficientBalance_thenThrowsException() {
            // given
            val userId = 1L
            val deductAmount = Money(BigDecimal("15000"), Currency.KRW)
            val point = Point(
                userId = userId,
                balance = Money(BigDecimal("10000"), Currency.KRW),
            )

            every { pointRepository.findByUserIdWithLock(userId) } returns point

            // when & then
            assertThrows<CoreException> {
                pointService.deductPoint(userId, deductAmount)
            }

            verify(exactly = 1) { pointRepository.findByUserIdWithLock(userId) }
            verify(exactly = 0) { pointRepository.save(any()) }
        }
    }
}
