package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.signal.ProductTotalSignalRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class ProductDetailService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productTotalSignalRepository: ProductTotalSignalRepository,
) {

    fun getProductDetailBy(productId: Long): ProductDetailResult {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 상품은 존재하지 않습니다.")
        val brand = brandRepository.findById(product.refBrandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 브랜드는 존재하지 않습니다.")
        val productTotalSignal = productTotalSignalRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "해당 상품의 통계 조회가 존재하지 않습니다.")

        return ProductDetailResult.from(product, brand, productTotalSignal.likeCount)
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
        fun from(product: ProductModel, brand: BrandModel, likeCount: Long): ProductDetailResult = ProductDetailResult(
            product.id,
            product.name,
            product.stock,
            likeCount,
            brand.id,
            brand.name,
        )
    }
}
