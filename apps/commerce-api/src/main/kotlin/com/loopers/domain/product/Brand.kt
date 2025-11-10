package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brand")
class Brand(
    @Column(nullable = false, unique = true, columnDefinition = "varchar(100)")
    val name: String,

    ) : BaseEntity() {
    companion object {
        fun create(name: String): Brand {
            return Brand(
                name = name,
            )
        }
    }
}
