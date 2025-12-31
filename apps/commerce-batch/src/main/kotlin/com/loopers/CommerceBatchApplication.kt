package com.loopers

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.TimeZone
import kotlin.system.exitProcess

@ConfigurationPropertiesScan
@SpringBootApplication
class CommerceBatchApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    val exitCode = SpringApplication.exit(runApplication<CommerceBatchApplication>(*args))
    exitProcess(exitCode)
}
