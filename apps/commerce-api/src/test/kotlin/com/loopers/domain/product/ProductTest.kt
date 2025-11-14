package com.loopers.domain.product

import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class ProductTest {
    @DisplayName("상품 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("새 상품이 생성된다")
        @Test
        fun `create new product`() {
            // given
            val brand = createBrand()
            val name = "맥북 프로"
            val price = Money.krw(2000000)
            val stock = Stock.of(10)

            // when
            val product = createNewProduct(
                brand = brand,
                name = name,
                price = price,
                stock = stock,
            )

            // then
            assertThat(product.stock).isEqualTo(stock)
            assertThat(product.brandId).isEqualTo(brand.id)
            assertThat(product.name).isEqualTo(name)
            assertThat(product.price).isEqualTo(price)
        }

        @DisplayName("재고 0으로 생성하면 품절 상태다")
        @Test
        fun `create out of stock status product when stock is zero`() {
            // given
            val stock = Stock.of(0)

            // when
            val product = createNewProduct(
                stock = stock,
            )

            // then
            assertThat(product.status).isEqualTo(ProductStatus.OUT_OF_STOCK)
        }

        @DisplayName("재고를 지정하여 생성하면 활성 상태다")
        @Test
        fun `create active product when valid stock is provided`() {
            // given
            val stock = Stock.of(10)

            // when
            val product = createNewProduct(
                stock = stock,
            )

            // then
            assertThat(product.status).isEqualTo(ProductStatus.ACTIVE)
        }
    }

    @DisplayName("재고 감소 테스트")
    @Nested
    inner class DecreaseStock {

        @DisplayName("유효한 amount가 주어지면 재고가 감소한다.")
        @Test
        fun `decrease stock when valid amount is provided`() {
            // given
            val existingStock = 10
            val product = createProduct(
                stock = Stock.of(existingStock),
            )

            // when
            val decreaseAmount = 3
            product.decreaseStock(decreaseAmount)

            // then
            assertThat(product.stock.amount).isEqualTo(existingStock - decreaseAmount)
        }

        @DisplayName("재고가 0이 되면 품절 상태로 변경된다.")
        @Test
        fun `change status to OUT_OF_STOCK when stock becomes zero`() {
            // given
            val existingStock = 5
            val product = createProduct(
                stock = Stock.of(existingStock),
            )

            // when
            product.decreaseStock(existingStock)

            // then
            assertThat(product.stock.amount).isEqualTo(0)
            assertThat(product.status).isEqualTo(ProductStatus.OUT_OF_STOCK)
        }
    }

    private fun createNewProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(10),
        brand: Brand = createBrand(),
    ): Product {
        return Product.create(
            name = name,
            price = price,
            stock = stock,
            brand = brand,
        )
    }

    private fun createBrand(
        name: String = "테스트 브랜드",
    ): Brand {
        return Brand.of(name)
    }

    private fun createProduct(
        brandId: Long = 1L,
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        status: ProductStatus = ProductStatus.ACTIVE,
        stock: Stock = Stock.of(10),
    ): Product {
        return Product.of(
            brandId = brandId,
            name = name,
            price = price,
            status = status,
            stock = stock,
        )
    }
}
