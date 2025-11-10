package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

data class ProductDetailData(
    val product: Product,
    val stock: Stock,
)

@Service
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
) {
    fun findProducts(brandId: Long?, sort: String, pageable: Pageable): Page<Product> {
        return productRepository.findAll(brandId, sort, pageable)
    }

    fun getProductDetail(productId: Long): ProductDetailData {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        val stock = stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")

        return ProductDetailData(product, stock)
    }

    fun getProductsByIds(productIds: List<Long>): List<Product> {
        return productRepository.findAllById(productIds)
    }
}
