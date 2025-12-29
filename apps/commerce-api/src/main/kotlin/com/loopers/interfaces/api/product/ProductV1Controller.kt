package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.event.ProductBrowsedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.interfaces.api.ApiResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
    private val eventPublisher: ApplicationEventPublisher,
) : ProductV1ApiSpec {
    /**
     * 상품 목록 조회 (랭킹 정보 없음)
     */
    @GetMapping
    override fun getProducts(
        @RequestHeader(value = "X-USER-ID", required = false) memberId: String?,
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false, defaultValue = "LATEST") sort: ProductSortType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<Page<ProductV1Dto.ProductResponse>> {
        val pageable = PageRequest.of(page, size)

        val result = productFacade.getProducts(brandId, sort, pageable)
            .map { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }

        eventPublisher.publishEvent(
            ProductBrowsedEvent(
                memberId = memberId,
                brandId = brandId,
                sortType = sort.name, // Enum을 String으로 변환
                page = page,
                browsedAt = Instant.now(),
            ),
        )

        return result
    }

    /**
     * 상품 상세 조회 (랭킹 정보 포함)
     */
    @GetMapping("/{productId}")
    override fun getProduct(
        @RequestHeader(value = "X-USER-ID", required = false) memberId: String?,
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        // 상품 정보 + 랭킹 정보 조회
        val productWithRanking = productFacade.getProductWithRanking(productId)

        val result = ProductV1Dto.ProductResponse.fromWithRanking(
            info = productWithRanking.product,
            rank = productWithRanking.rank,
            score = productWithRanking.score,
        ).let { ApiResponse.success(it) }

        eventPublisher.publishEvent(
            ProductViewedEvent(
                aggregateId = productId,
                productId = productId,
                memberId = memberId,
                viewedAt = Instant.now(),
            ),
        )

        return result
    }


}
