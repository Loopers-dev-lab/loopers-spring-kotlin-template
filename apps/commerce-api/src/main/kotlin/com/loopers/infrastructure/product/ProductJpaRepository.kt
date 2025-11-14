package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long>
