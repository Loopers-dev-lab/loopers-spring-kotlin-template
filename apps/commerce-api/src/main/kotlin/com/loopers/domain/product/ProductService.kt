package com.loopers.domain.product

import com.loopers.application.order.OrderCommand
import com.loopers.application.product.ProductInfo
import com.loopers.application.ranking.ProductWithBrand
import com.loopers.domain.product.stock.StockRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
) {

    fun getProducts(pageable: Pageable, brandId: Long?): Page<ProductInfo> =
        productRepository.findAllProductInfos(
            pageable,
            brandId,
        )

    @Transactional
    fun occupyStocks(command: OrderCommand) {
        command.orderItems.forEach {
            val stock = stockRepository.getStockByRefProductIdWithPessimisticLock(it.productId)
            stock.occupy(it.quantity)

            stockRepository.save(stock)
        }
    }


    fun getProductsByIdsWithBrand(ids: List<Long>): List<ProductWithBrand> =
        productRepository.findByIdsInWithBrand(ids)
}
