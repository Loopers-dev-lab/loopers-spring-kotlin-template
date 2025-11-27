package com.loopers.domain.product.viewModel

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductViewModelService(private val productViewModelRepository: ProductViewModelRepository) {

    fun getProductViewModels(pageable: Pageable, brandId: Long?): Page<ProductViewModel> =
        productViewModelRepository.findAllWithPaging(pageable, brandId)
}
