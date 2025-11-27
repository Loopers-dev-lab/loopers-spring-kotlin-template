package com.loopers.domain.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.signal.ProductTotalSignalRepository
import com.loopers.domain.product.stock.StockRepository
import com.loopers.support.cache.CacheKeys
import com.loopers.support.cache.CacheTemplate
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductDetailService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productTotalSignalRepository: ProductTotalSignalRepository,
    private val stockRepository: StockRepository,
    private val cacheTemplate: CacheTemplate,
) {
    companion object {
        private val TYPE_PRODUCT_DETAIL = object : TypeReference<ProductDetailResult>() {}
    }

    @Transactional(readOnly = true)
    fun getProductDetailBy(
        productId: Long,
    ): ProductDetailResult = cacheTemplate.cacheAside(CacheKeys.ProductDetail(productId), TYPE_PRODUCT_DETAIL) {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 상품은 존재하지 않습니다.")

        val brand = brandRepository.findById(product.refBrandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 브랜드는 존재하지 않습니다.")

        val productTotalSignal = productTotalSignalRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 상품의 통계 조회가 존재하지 않습니다.")

        val stock = stockRepository.findByRefProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 상품의 재고가 존재하지 않습니다.")

        ProductDetailResult.from(product, brand, productTotalSignal.likeCount, stock.amount)
    }
}

data class ProductDetailResult(
    val id: Long,
    val name: String,
    val stock: Long,
    val likeCount: Long,
    val brandId: Long,
    val brandName: String,
) {
    companion object {
        fun from(product: ProductModel, brand: BrandModel, likeCount: Long, stockQuantity: Long): ProductDetailResult =
            ProductDetailResult(
                product.id,
                product.name,
                stockQuantity,
                likeCount,
                brand.id,
                brand.name,
            )
    }
}
