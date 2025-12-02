package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class Brand(
    name: String,
    description: String?,
): BaseEntity(){
    @Column(name="name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name="description", columnDefinition = "TEXT")
    var description: String? = description
        protected set
}
