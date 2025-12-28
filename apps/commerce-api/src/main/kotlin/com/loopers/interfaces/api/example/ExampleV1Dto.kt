package com.loopers.interfaces.api.example

import com.loopers.application.example.ExampleInfo

class ExampleV1Dto {
    data class ExampleResponse(
        val id: Long,
        val name: String,
        val description: String,
    ) {
        companion object {
            fun from(info: ExampleInfo): ExampleResponse {
                return ExampleResponse(
                    id = info.id,
                    name = info.name,
                    description = info.description,
                )
            }
        }
    }
}
