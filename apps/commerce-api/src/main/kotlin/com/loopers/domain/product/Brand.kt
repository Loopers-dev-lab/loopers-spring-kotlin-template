package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class Brand(
    name: String,
) : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = name
        private set

    init {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.")
        }
    }

    companion object {
        fun create(name: String): Brand {
            return Brand(name)
        }

        fun of(name: String): Brand {
            return Brand(name)
        }
    }
}
