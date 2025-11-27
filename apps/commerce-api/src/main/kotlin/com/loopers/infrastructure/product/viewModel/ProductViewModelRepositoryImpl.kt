package com.loopers.infrastructure.product.viewModel

import com.loopers.domain.product.viewModel.ProductViewModel
import com.loopers.domain.product.viewModel.ProductViewModelRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductViewModelRepositoryImpl(private val productViewModelJpaRepository: ProductViewModelJpaRepository) :
    ProductViewModelRepository {

    override fun save(productViewModel: ProductViewModel): ProductViewModel =
        productViewModelJpaRepository.saveAndFlush(productViewModel)

    override fun findAllWithPaging(pageable: Pageable, brandId: Long?): Page<ProductViewModel> =
        brandId?.let {
            productViewModelJpaRepository.findAllByRefBrandId(it, pageable)
        } ?: productViewModelJpaRepository.findAllBy(pageable)
}
