package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.IssuedCouponSortType
import com.loopers.domain.coupon.UsageStatus
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IssuedCouponV1ApiE2ETest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
    private val testRestTemplate: TestRestTemplate,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/issued-coupons/{couponId}")
    @Nested
    inner class IssueCoupon {

        @DisplayName("쿠폰 발급에 성공하면 200 OK와 발급된 쿠폰 정보를 반환한다")
        @Test
        fun returnIssuedCoupon_whenValidRequest() {
            // given
            val userId = 1L
            val coupon = createCoupon()

            // when
            val response = issueCoupon(
                userId = userId,
                couponId = coupon.id,
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.userId).isEqualTo(userId) },
                { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
                { assertThat(response.body?.data?.status).isEqualTo(UsageStatus.AVAILABLE) },
            )
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하려고 하면 404 Not Found를 반환한다")
        @Test
        fun returnNotFound_whenCouponDoesNotExist() {
            // given
            val userId = 1L
            val nonExistentCouponId = 999L

            // when
            val response = issueCoupon(
                userId = userId,
                couponId = nonExistentCouponId,
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.message).contains("존재하지 않는 쿠폰입니다") },
            )
        }

        @DisplayName("이미 발급된 쿠폰을 다시 발급하려고 하면 409 Conflict를 반환한다")
        @Test
        fun returnConflict_whenCouponAlreadyIssued() {
            // given
            val userId = 1L
            val coupon = createCoupon()
            createIssuedCoupon(userId = userId, coupon = coupon)

            // when
            val response = issueCoupon(
                userId = userId,
                couponId = coupon.id,
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.message).contains("이미 발급된 쿠폰입니다") },
            )
        }

        @DisplayName("user id 없이 요청하면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenUserIdNotProvided() {
            // given
            val coupon = createCoupon()

            // when
            val response = issueCoupon(
                userId = null,
                couponId = coupon.id,
            )

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/issued-coupons")
    @Nested
    inner class GetUserCoupons {

        @DisplayName("보유한 쿠폰이 있으면 200 OK와 쿠폰 목록을 반환한다")
        @Test
        fun returnCoupons_whenUserHasCoupons() {
            // given
            val userId = 1L
            val coupon1 = createCoupon(
                name = "5,000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 5000,
            )
            val coupon2 = createCoupon(
                name = "10% 할인 쿠폰",
                discountType = DiscountType.RATE,
                discountValue = 10,
            )
            createIssuedCoupon(userId = userId, coupon = coupon1)
            createIssuedCoupon(userId = userId, coupon = coupon2)

            // when
            val response = getUserCoupons(userId = userId)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.coupons).hasSize(2) },
                { assertThat(response.body?.data?.coupons?.map { it.couponId }).containsExactlyInAnyOrder(coupon1.id, coupon2.id) },
                { assertThat(response.body?.data?.coupons?.all { it.status == UsageStatus.AVAILABLE }).isTrue() },
                { assertThat(response.body?.data?.hasNext).isFalse() },
            )
        }

        @DisplayName("보유한 쿠폰이 없으면 200 OK와 빈 목록을 반환한다")
        @Test
        fun returnEmptyList_whenUserHasNoCoupons() {
            // given
            val userId = 1L

            // when
            val response = getUserCoupons(userId = userId)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.coupons).isEmpty() },
                { assertThat(response.body?.data?.hasNext).isFalse() },
            )
        }

        @DisplayName("user id 없이 요청하면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenUserIdNotProvided() {
            // when
            val response = getUserCoupons(userId = null)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    private fun createCoupon(
        name: String = "테스트 쿠폰",
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 5000,
    ): Coupon {
        val discountAmount = DiscountAmount(
            type = discountType,
            value = discountValue,
        )
        val coupon = Coupon.of(name = name, discountAmount = discountAmount)
        return couponRepository.save(coupon)
    }

    private fun createIssuedCoupon(
        userId: Long,
        coupon: Coupon,
    ): IssuedCoupon {
        val issuedCoupon = coupon.issue(userId)
        return issuedCouponRepository.save(issuedCoupon)
    }

    private fun issueCoupon(
        userId: Long?,
        couponId: Long,
    ): ResponseEntity<ApiResponse<IssuedCouponV1Response.Issue>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        return testRestTemplate.exchange(
            "/api/v1/issued-coupons/$couponId",
            HttpMethod.POST,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<IssuedCouponV1Response.Issue>>() {},
        )
    }

    private fun getUserCoupons(
        userId: Long?,
        page: Int? = null,
        size: Int? = null,
        sort: IssuedCouponSortType? = null,
    ): ResponseEntity<ApiResponse<IssuedCouponV1Response.UserCoupons>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        val url = buildString {
            append("/api/v1/issued-coupons")
            val params = mutableListOf<String>()
            page?.let { params.add("page=$it") }
            size?.let { params.add("size=$it") }
            sort?.let { params.add("sort=$it") }
            if (params.isNotEmpty()) {
                append("?${params.joinToString("&")}")
            }
        }

        return testRestTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<IssuedCouponV1Response.UserCoupons>>() {},
        )
    }
}
