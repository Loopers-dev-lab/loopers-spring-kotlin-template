package com.loopers.application.product

import com.loopers.application.dto.PageResult
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.util.TransactionUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
    private val productLikeService: ProductLikeService,
    private val userService: UserService,
) {
    @Transactional(readOnly = true)
    fun getProducts(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductResult.ListInfo> {
        // 1. 상품 리스트 조회
        val productPage = productService.getProducts(brandId, sort, pageable)

        // 2. 빈 페이지 처리
        if (productPage.isEmpty) {
            return Page.empty(pageable)
        }

        val products = productPage.content

        // 3. 상품 ID, 브랜드 ID 추출
        val productIds = products.map { it.id }
        val brandIds = products.map { it.brandId }.distinct()

        // 4. 좋아요 정보 수 조회
        val productLikeCounts = productLikeService.getCountAllBy(productIds)

        // 5. 브랜드 정보 일괄 조회
        val brands = brandService.getAllBrand(brandIds)

        // 6. 상품 정보 변환
        return productPage.map { product ->
            ProductResult.ListInfo.from(product, productLikeCounts, brands)
        }
    }

    @Transactional(readOnly = true)
    fun getProduct(productId: Long, userId: String?): ProductResult.DetailInfo {

        // 1. 상품 조회
        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        // 2. 상품의 브랜드 조회
        val brand = brandService.getBrand(product.brandId)

        // 3. 상품의 좋아요 수 정보 조회
        val productLikeCount = productLikeService.getCountBy(product.id)

        // 4. 유저가 좋아요 했는지 조회
        val userLiked = if (userId == null) {
            false
        } else {
            val user = userService.getMyInfo(userId)
            productLikeService.getBy(product.id, user.id) != null
        }

        return ProductResult.DetailInfo.from(product, userLiked, productLikeCount.likeCount, brand)
    }

    @Transactional(readOnly = true)
    fun getLikedProducts(userId: String, pageable: Pageable): Page<ProductResult.LikedInfo> {
        // 1. 사용자 존재 여부 확인
        val user = userService.getMyInfo(userId)

        // 2. 사용자가 좋아요한 상품 페이지 조회
        val productLikePage = productLikeService.getAllBy(user.id, pageable)

        // 3. 빈 페이지 처리
        if (productLikePage.isEmpty) {
            return Page.empty(pageable)
        }

        val productLikes = productLikePage.content

        // 4. 좋아요한 상품 ID 추출
        val likedProductIds = productLikes.map { it.productId }

        // 5. 상품 정보 조회
        val products = productService.getProducts(likedProductIds)

        // 6. 브랜드 ID 추출 및 브랜드 정보 조회
        val brandIds = products.map { it.brandId }.distinct()
        val brands = brandService.getAllBrand(brandIds)

        // 7. ProductLike 순서대로 결과 생성
        return productLikePage.map { like ->
            ProductResult.LikedInfo.from(like, products, brands)
        }
    }
}
