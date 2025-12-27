package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

@Configuration
class MySqlTestContainersConfig {
    companion object {
        private val mySqlContainer: MySQLContainer<Nothing> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("loopers")
            .withUsername("test")
            .withPassword("test")
            .withExposedPorts(3306)
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_general_ci",
                "--skip-character-set-client-handshake",
            )
            .apply {
                start()
            }

        init {
            val mySqlJdbcUrl =
                "jdbc:mysql://${mySqlContainer.host}:${mySqlContainer.firstMappedPort}/${mySqlContainer.databaseName}"
            System.setProperty("datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl)
            System.setProperty("datasource.mysql-jpa.main.username", mySqlContainer.username)
            System.setProperty("datasource.mysql-jpa.main.password", mySqlContainer.password)
        }
    }
}
