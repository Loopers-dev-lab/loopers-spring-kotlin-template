package com.loopers.infrastructure.product.viewModel

import com.loopers.domain.product.viewModel.ProductViewModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductViewModelJpaRepository : JpaRepository<ProductViewModel, Long> {
    fun findByRefProductId(productId: Long): ProductViewModel?

    fun findAllBy(pageable: Pageable): Page<ProductViewModel>

    fun findAllByRefBrandId(refBrandId: Long, pageable: Pageable): Page<ProductViewModel>
}
