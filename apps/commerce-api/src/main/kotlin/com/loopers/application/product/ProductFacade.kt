package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
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
    ): Page<ProductResult.Info> {
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
        return page.map { ProductResult.Info.from(it, productLikes, brands) }
    }
}
