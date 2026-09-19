package com.ecourtier.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("!test")
@Slf4j
public class DatabaseConfig {


    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/ecourtier_db}")
    private String rawUrl;

    @Value("${spring.datasource.username:ecourtier_user}")
    private String username;

    @Value("${spring.datasource.password:ecourtier_secure_pass_2026}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        String formattedUrl = rawUrl;
        if (formattedUrl.startsWith("postgres://")) {
            formattedUrl = "jdbc:" + formattedUrl;
        } else if (formattedUrl.startsWith("postgresql://")) {
            formattedUrl = "jdbc:" + formattedUrl;
        }

        log.info("[DATABASE CONFIG] Initialisation du DataSource avec l'URL : {}", formattedUrl.replaceAll(":([^@]+)@", ":****@"));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(formattedUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);

        return new HikariDataSource(config);
    }
}
