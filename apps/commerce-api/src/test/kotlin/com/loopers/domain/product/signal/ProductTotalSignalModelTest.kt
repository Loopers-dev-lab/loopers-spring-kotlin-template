package com.loopers.domain.product.signal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProductTotalSignalModelTest {

    @DisplayName("좋아요 수 증가")
    @Test
    fun increaseLikeCountSuccess() {
        val productTotalSignalModel = ProductTotalSignalModel(1)

        productTotalSignalModel.incrementLikeCount()
        assertThat(productTotalSignalModel.likeCount).isEqualTo(1)

        productTotalSignalModel.incrementLikeCount()
        assertThat(productTotalSignalModel.likeCount).isEqualTo(2)

        productTotalSignalModel.incrementLikeCount()
        assertThat(productTotalSignalModel.likeCount).isEqualTo(3)

        productTotalSignalModel.incrementLikeCount()
        assertThat(productTotalSignalModel.likeCount).isEqualTo(4)
    }

    @DisplayName("좋아요 수 감소")
    @Nested
    inner class DecreaseLikeCount {

        @DisplayName("좋아요 수 감소가 정상적으로 이루어진다.")
        @Test
        fun decreaseLikeCountSuccess() {
            val productTotalSignalModel = ProductTotalSignalModel(1)
            productTotalSignalModel.incrementLikeCount()
            productTotalSignalModel.incrementLikeCount()
            productTotalSignalModel.incrementLikeCount()
            productTotalSignalModel.incrementLikeCount() // 3 -> 4

            productTotalSignalModel.decrementLikeCount() // 4 -> 3
            assertThat(productTotalSignalModel.likeCount).isEqualTo(3)
        }

        @DisplayName("좋아요 수 감소가 0 이하로 떨어지지 않는다.")
        @Test
        fun decreaseLikeCountFails_whenQuantityIsLessThanZero() {
            val productTotalSignalModel = ProductTotalSignalModel(1)
            productTotalSignalModel.decrementLikeCount()
            assertThat(productTotalSignalModel.likeCount).isEqualTo(0)
        }
    }
}
