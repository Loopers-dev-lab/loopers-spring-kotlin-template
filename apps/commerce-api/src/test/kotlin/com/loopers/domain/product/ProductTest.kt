package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductTest {

    @DisplayName("상품을 생성할 수 있다")
    @Test
    fun createProduct() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "상품 설명", Money.of(15000L), Stock.of(100), brand)

        assertAll(
            { assertThat(product.name).isEqualTo("상품1") },
            { assertThat(product.description).isEqualTo("상품 설명") },
            { assertThat(product.price.amount).isEqualTo(15000L) },
            { assertThat(product.stock.quantity).isEqualTo(100) },
            { assertThat(product.brand).isEqualTo(brand) },
            { assertThat(product.likesCount).isEqualTo(0) },
        )
    }

    @DisplayName("좋아요 수를 증가시킬 수 있다")
    @Test
    fun increaseLikeCount() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)

        product.increaseLikesCount()
        product.increaseLikesCount()

        assertThat(product.likesCount).isEqualTo(2)
    }

    @DisplayName("좋아요 수를 감소시킬 수 있다")
    @Test
    fun decreaseLikeCount() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)

        product.increaseLikesCount()
        product.increaseLikesCount()
        product.decreaseLikesCount()

        assertThat(product.likesCount).isEqualTo(1)
    }

    @DisplayName("좋아요 수가 0일 때 감소시키면 0을 유지한다")
    @Test
    fun decreaseLikeCountWhenLikesCountIsZero() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)

        product.decreaseLikesCount()

        assertThat(product.likesCount).isEqualTo(0)
    }

    @DisplayName("재고를 감소시킬 수 있다")
    @Test
    fun decreaseStock() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)

        product.decreaseStock(Quantity.of(30))

        assertThat(product.stock.quantity).isEqualTo(70)
    }

    @DisplayName("재고가 충분한지 확인할 수 있다")
    @Test
    fun checkHasEnoughStock() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)

        assertAll(
            { assertThat(product.hasEnoughStock(Quantity.of(30))).isTrue() },
            { assertThat(product.hasEnoughStock(Quantity.of(101))).isFalse() },
            { assertThat(product.hasEnoughStock(Quantity.of(100))).isTrue() }
        )
    }

    @DisplayName("재고가 부족할 경우 검증 시 예외가 발생한다")
    @Test
    fun failToValidateStockWhenInsufficient() {
        val brand = Brand("테스트브랜드", "설명")
        val product = Product("상품1", "설명", Money.of(10000L), Stock.of(10), brand)

        val exception = assertThrows<CoreException> {
            product.validateStock(Quantity.of(20))
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_STOCK)
    }

    @DisplayName("재고 검증 후 감소시킬 수 있다")
    @Test
    fun decreaseStockWithValidation() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)

        product.decreaseStockWithValidation(Quantity.of(30))

        assertThat(product.stock.quantity).isEqualTo(70)
    }

    @DisplayName("재고가 부족할 경우 검증 후 감소 시 예외가 발생한다")
    @Test
    fun failToDecreaseStockWithValidationWhenInsufficient() {
        val brand = Brand("테스트브랜드", "설명")
        val product = Product("상품1", "설명", Money.of(10000L), Stock.of(10), brand)

        val exception = assertThrows<CoreException> {
            product.decreaseStockWithValidation(Quantity.of(20))
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_STOCK)
        assertThat(product.stock.quantity).isEqualTo(10)
    }



}
