package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@Configuration
class MySqlTestContainersConfig {
    companion object {
        private val mySqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .apply {
                withDatabaseName("loopers")
                withUsername("test")
                withPassword("test")
                withExposedPorts(3306)
                withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--skip-character-set-client-handshake",
                )
                start()
            }

        init {
            val mySqlJdbcUrl = mySqlContainer.let { "jdbc:mysql://${it.host}:${it.firstMappedPort}/${it.databaseName}" }
            System.setProperty("datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl)
            System.setProperty("datasource.mysql-jpa.main.username", mySqlContainer.username)
            System.setProperty("datasource.mysql-jpa.main.password", mySqlContainer.password)
        }
    }
}
