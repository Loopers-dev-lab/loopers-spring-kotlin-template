package com.loopers.infrastructure.product

import com.loopers.domain.product.Brand
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BrandJpaRepository : JpaRepository<Brand, Long>
