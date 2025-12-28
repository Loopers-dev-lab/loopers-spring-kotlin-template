package com.loopers.interfaces.api.example

import com.loopers.application.example.ExampleFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/examples")
class ExampleV1Controller(
    private val exampleFacade: ExampleFacade,
) : ExampleV1ApiSpec {
    @GetMapping("/{exampleId}")
    override fun getExample(
        @PathVariable(value = "exampleId") exampleId: Long,
    ): ApiResponse<ExampleV1Dto.ExampleResponse> {
        return exampleFacade.getExample(exampleId)
            .let { ExampleV1Dto.ExampleResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
