package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
    private val productLikeService: ProductLikeService,
) {
    @Transactional(readOnly = true)
    fun getProducts(
        brandId: Long?,
        sort: ProductSort,
        pageable: Pageable,
    ): Page<ProductResult.ListInfo> {
        // 1. 상품 리스트 조회 (정렬/브랜드 필터 포함)
        val page = productService.getProducts(brandId, sort, pageable)
        if (page.isEmpty) return Page.empty()
        val products = page.content

        // 2. 조회된 상품에서 ID, 브랜드 ID 추출
        val productIds = products.map { it.id }.toList()
        val brandIds = products.map { it.brandId }.toList()

        // 3. 해당 상품들의 좋아요 정보 조회
        val productLikes = productLikeService.findAllBy(productIds)

        // 4. 해당 브랜드 정보 조회
        val brands = brandService.getAllBrand(brandIds)

        // 5. 각 상품 정보 변환 (브랜드명, 좋아요 수 포함)
        return page.map { ProductResult.ListInfo.from(it, productLikes, brands) }
    }

    @Transactional(readOnly = true)
    fun getProduct(
        productId: Long,
        userId: String?,
    ): ProductResult.DetailInfo {
        // 1. 상품 조회
        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        // 2. 상품의 브랜드 조회
        val brand = brandService.getBrand(product.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: $product.brandId")

        // 3. 상품의 좋아요 정보 조회
        val productLikes = productLikeService.findAllBy(product.id)

        return ProductResult.DetailInfo.from(product, productLikes, brand, userId)
    }
}
