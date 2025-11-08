package com.loopers.application.product

import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductQueryService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.StockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val likeRepository: LikeRepository,
    private val productQueryService: ProductQueryService,
) {
    fun getProducts(brandId: Long?, sort: String, pageable: Pageable): Page<ProductListInfo> {
        val productsWithLikeCount = productQueryService.findProducts(brandId, sort, pageable)
        return productsWithLikeCount.map { ProductListInfo.from(it.product, it.likeCount) }
    }

    fun getProductDetail(productId: Long): ProductDetailInfo {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        val stock = stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")

        val likeCount = likeRepository.countByProductId(productId)

        return ProductDetailInfo.from(product, stock, likeCount)
    }
}
