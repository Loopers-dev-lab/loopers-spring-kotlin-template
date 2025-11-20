package com.loopers.domain.like

import com.loopers.IntegrationTestSupport
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.signal.ProductTotalSignalRepository
import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LikeServiceTest(
    private val userRepository: UserJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val productTotalSignalRepository: ProductTotalSignalRepository,
    private val likeService: LikeService,
    private val databaseCleanUp: DatabaseCleanUp,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 등록")
    @Nested
    inner class Like {

        @DisplayName("좋아요가 정상적으로 등록된다.")
        @Test
        fun likeSuccess() {
            val testUser = UserModel(
                "testUser",
                "testUser@naver.com",
                "1996-03-15",
                gender = "FEMALE",
            )
            userRepository.save(testUser)

            val testProduct = ProductModel.create(
                name = "testProduct",
                price = Money(BigDecimal.valueOf(5000L)),
                refBrandId = 12,
            )
            productRepository.save(testProduct)
            val isNewLike = likeService.like(testUser.id, testProduct.id)

            assertThat(isNewLike).isTrue()
        }
    }
}
