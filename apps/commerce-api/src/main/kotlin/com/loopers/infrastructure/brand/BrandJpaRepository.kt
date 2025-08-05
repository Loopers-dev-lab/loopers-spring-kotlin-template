package com.loopers.infrastructure.brand

import com.loopers.domain.brand.entity.Brand
import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<Brand, Long>
