package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.ProductDetailResult
import com.loopers.domain.product.viewModel.ProductViewModel
import org.springframework.data.domain.Page
import java.math.BigDecimal

sealed class ProductDto {

    data class GetProduct(
        val id: Long,
        val brandId: Long,
        val brandName: String,
        val price: Money,
        val stockAmount: Long,
        val likeCount: Long,
        val rank: Long?,
    ) {
        companion object {
            fun from(result: ProductDetailResult): GetProduct {
                return GetProduct(
                    id = result.id,
                    brandId = result.brandId,
                    brandName = result.brandName,
                    price = result.price,
                    stockAmount = result.stock,
                    likeCount = result.likeCount,
                    rank = result.rank,
                )
            }
        }
    }

    data class ProductViewModelResponse(
        val id: Long,
        val refProductId: Long,
        val productName: String,
        val price: BigDecimal,
        val refBrandId: Long,
        val brandName: String,
        val stockAmount: Long,
        val likeCount: Long,
    ) {
        companion object {
            fun from(productViewModel: ProductViewModel): ProductViewModelResponse = ProductViewModelResponse(
                id = productViewModel.id,
                refProductId = productViewModel.refProductId,
                productName = productViewModel.productName,
                price = productViewModel.price.amount,
                refBrandId = productViewModel.refBrandId,
                brandName = productViewModel.brandName,
                stockAmount = productViewModel.stockAmount,
                likeCount = productViewModel.likeCount,
            )
        }
    }

    data class ProductInfoResponse(
        val id: Long,
        val name: String,
        val stock: Long,
        val price: BigDecimal,
        val likeCount: Long,
        val brandId: Long,
        val brandName: String,
    ) {
        companion object {
            fun from(productInfo: ProductInfo): ProductInfoResponse = ProductInfoResponse(
                id = productInfo.id,
                name = productInfo.name,
                stock = productInfo.stock,
                price = productInfo.price,
                likeCount = productInfo.likeCount,
                brandId = productInfo.brandId,
                brandName = productInfo.brandName,
            )
        }
    }

    data class PageResponse<T>(
        val content: List<T>,
        val pageNumber: Int,
        val pageSize: Int,
        val totalElements: Long,
        val totalPages: Int,
        val last: Boolean,
    ) {
        companion object {
            fun <T, R> from(page: Page<T>, mapper: (T) -> R): PageResponse<R> = PageResponse(
                content = page.content.map(mapper),
                pageNumber = page.number,
                pageSize = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                last = page.isLast,
            )
        }
    }
}
