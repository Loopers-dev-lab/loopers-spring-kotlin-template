package com.loopers.domain.coupon

import com.loopers.support.fixtures.CouponFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CouponIssue 도메인 테스트")
class CouponIssueTest {

    @Nested
    @DisplayName("쿠폰 발급")
    inner class Issue {

        @Test
        fun `쿠폰을 발급한다`() {
            // given
            val couponId = 1L
            val userId = 100L

            // when
            val couponIssue = CouponFixtures.createCouponIssue(
                couponId = couponId,
                userId = userId,
            )

            // then
            assertSoftly {
                it.assertThat(couponIssue.couponId).isEqualTo(couponId)
                it.assertThat(couponIssue.userId).isEqualTo(userId)
                it.assertThat(couponIssue.status).isEqualTo(CouponStatus.ISSUED)
                it.assertThat(couponIssue.usedAt).isNull()
                it.assertThat(couponIssue.issuedAt).isNotNull()
            }
        }

        @Test
        fun `발급된 쿠폰의 초기 상태는 ISSUED이다`() {
            // when
            val couponIssue = CouponFixtures.createCouponIssue()

            // then
            assertThat(couponIssue.status).isEqualTo(CouponStatus.ISSUED)
        }
    }

    @Nested
    @DisplayName("쿠폰 사용")
    inner class Use {

        @Test
        fun `발급된 쿠폰을 사용한다`() {
            // given
            val couponIssue = CouponFixtures.createCouponIssue()

            // when
            couponIssue.use()

            // then
            assertSoftly {
                it.assertThat(couponIssue.status).isEqualTo(CouponStatus.USED)
                it.assertThat(couponIssue.usedAt).isNotNull()
            }
        }

        @Test
        fun `이미 사용된 쿠폰을 다시 사용하면 예외가 발생한다`() {
            // given
            val couponIssue = CouponFixtures.createCouponIssue()
            couponIssue.use()

            // when & then
            assertThatThrownBy { couponIssue.use() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("이미 사용된 쿠폰입니다")
        }
    }
}
