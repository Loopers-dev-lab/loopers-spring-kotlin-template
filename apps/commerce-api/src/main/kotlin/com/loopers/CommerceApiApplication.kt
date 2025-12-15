package com.loopers

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableAsync
class CommerceApiApplication {

    @PostConstruct
    fun started() {
        // set timezone
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    }
}

fun main(args: Array<String>) {
    runApplication<CommerceApiApplication>(*args)
}
