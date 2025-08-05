package com.loopers.infrastructure.product

import com.loopers.domain.product.entity.ProductStock
import org.springframework.data.jpa.repository.JpaRepository

interface ProductStockJpaRepository : JpaRepository<ProductStock, Long>
