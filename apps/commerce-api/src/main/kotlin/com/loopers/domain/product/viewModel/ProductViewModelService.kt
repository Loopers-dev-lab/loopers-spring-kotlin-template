package com.loopers.domain.product.viewModel

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.support.cache.CacheKeys
import com.loopers.support.cache.CacheTemplate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductViewModelService(
    private val productViewModelRepository: ProductViewModelRepository,
    private val cacheTemplate: CacheTemplate,
) {
    companion object {
        private val TYPE_PRODUCT_VIEW_MODEL_PAGE = object : TypeReference<Page<ProductViewModel>>() {}
        private const val MAX_CACHED_PAGE = 4 // 0~4페이지 = 총 5페이지
    }

    fun getProductViewModels(pageable: Pageable, brandId: Long?): Page<ProductViewModel> {
        // 5페이지 이하만 캐시 적용
        return if (pageable.pageNumber <= MAX_CACHED_PAGE) {
            cacheTemplate.cacheAside(
                CacheKeys.ProductViewModelPage(pageable, brandId),
                TYPE_PRODUCT_VIEW_MODEL_PAGE,
            ) {
                productViewModelRepository.findAllWithPaging(pageable, brandId)
            }
        } else {
            // 5페이지 초과는 캐시 없이 직접 조회
            productViewModelRepository.findAllWithPaging(pageable, brandId)
        }
    }
}
