package com.loopers.application.product

import com.loopers.domain.product.ProductDetailResult
import com.loopers.domain.product.ProductDetailService
import com.loopers.domain.product.ProductEventPublisher
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductViewedEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class ProductFacade(
    private val productService: ProductService,
    private val productDetailService: ProductDetailService,
    private val productEventPublisher: ProductEventPublisher,
) {

    @Transactional(readOnly = true)
    fun getProductDetail(productId: Long, userId: Long?): ProductDetailResult {
        val userId = userId ?: -999L
        val now = LocalDateTime.now()

        val detail = productDetailService.getProductDetailBy(productId, now)

        productEventPublisher.publish(ProductViewedEvent(productId, userId))

        return detail
    }


    fun getProducts(pageable: Pageable, brandId: Long?): Page<ProductInfo> =
        productService.getProducts(
            pageable,
            brandId,
        )
}
