package com.loopers.infrastructure.product

import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.ProductModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductJpaRepository : JpaRepository<ProductModel, Long> {
    @Query(
        """
          SELECT new com.loopers.application.product.ProductInfo(
              p.id,
              p.name,
              COALESCE(st.amount, 0),
              p.price,
              COALESCE(pts.likeCount, 0),
              p.refBrandId,
              b.name
          )
          FROM ProductModel p
          LEFT JOIN ProductTotalSignalModel pts ON pts.refProductId = p.id
          LEFT JOIN BrandModel b ON b.id = p.refBrandId
          LEFT JOIN StockModel st ON st.refProductId = p.id
      """,
    )
    fun findAllProductInfos(pageable: Pageable): Page<ProductInfo>
}
