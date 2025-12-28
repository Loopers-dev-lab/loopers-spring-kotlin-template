package com.loopers.infrastructure.example

import com.loopers.domain.example.ExampleModel
import com.loopers.domain.example.ExampleRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ExampleRepositoryImpl(
    private val exampleJpaRepository: ExampleJpaRepository,
) : ExampleRepository {
    override fun find(id: Long): ExampleModel? {
        return exampleJpaRepository.findByIdOrNull(id)
    }
}
