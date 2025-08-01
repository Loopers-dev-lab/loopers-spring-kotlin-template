package com.loopers.domain.product

import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.domain.product.entity.ProductStock
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductStockServiceIntegrationTest @Autowired constructor(
    private val productStockService: ProductStockService,
    private val productStockRepository: ProductStockRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("재고 차감")
    inner class DecreaseStock {

        @Test
        fun `정상적으로 재고를 차감한다`() {
            // given
            val stock = productStockRepository.save(ProductStock.create(1L, 10))
            val command = ProductStockCommand.DecreaseStocks(
                listOf(ProductStockCommand.DecreaseStocks.DecreaseStock(stock.productOptionId, 3)),
            )

            // when
            val decreaseStocks = productStockService.decreaseStock(command)

            // then
            assertThat(decreaseStocks.first().stockQuantity.quantity).isEqualTo(7)
        }

        @Test
        fun `재고 수량이 부족한 경우 예외가 발생한다`() {
            // given
            val stock = productStockRepository.save(ProductStock.create(1L, 2))
            val command = ProductStockCommand.DecreaseStocks(
                listOf(ProductStockCommand.DecreaseStocks.DecreaseStock(stock.productOptionId, 5)),
            )

            // when & then
            assertThrows<CoreException> {
                productStockService.decreaseStock(command)
            }
        }
    }
}
