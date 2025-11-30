package com.loopers.domain.product.viewModel

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductViewModelRepository {

    fun save(productViewModel: ProductViewModel): ProductViewModel

    fun findAllWithPaging(pageable: Pageable, brandId: Long?): Page<ProductViewModel>
}
