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
import java.net.URI;

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
        String finalUrl = formatJdbcUrl(rawUrl);
        String finalUsername = extractUsername(rawUrl, username);
        String finalPassword = extractPassword(rawUrl, password);

        log.info("[DATABASE CONFIG] Initialisation du DataSource avec l'URL JDBC : {}", finalUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(finalUrl);
        config.setUsername(finalUsername);
        config.setPassword(finalPassword);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setInitializationFailTimeout(30000); // 30s timeout pour les cold starts cloud

        return new HikariDataSource(config);
    }

    public static String formatJdbcUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "jdbc:postgresql://localhost:5432/ecourtier_db";
        }
        String urlToParse = rawUrl.trim();
        if (urlToParse.startsWith("jdbc:")) {
            urlToParse = urlToParse.substring(5);
        }

        if (urlToParse.startsWith("postgres://") || urlToParse.startsWith("postgresql://")) {
            try {
                URI uri = new URI(urlToParse);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath();
                String query = uri.getQuery();

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + (path != null && !path.isEmpty() ? path : "/ecourtier_db");
                if (query != null && !query.isEmpty()) {
                    jdbcUrl += "?" + query;
                }
                return jdbcUrl;
            } catch (Exception e) {
                log.warn("[DATABASE CONFIG] Impossible de parser l'URI PostgreSQL : {}, fallback URL brute", e.getMessage());
            }
        }

        if (!rawUrl.startsWith("jdbc:")) {
            return "jdbc:" + rawUrl;
        }
        return rawUrl;
    }

    public static String extractUsername(String rawUrl, String defaultUser) {
        if (rawUrl != null && rawUrl.contains("://")) {
            String urlToParse = rawUrl.trim();
            if (urlToParse.startsWith("jdbc:")) urlToParse = urlToParse.substring(5);
            try {
                URI uri = new URI(urlToParse);
                if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
                    String user = uri.getUserInfo().split(":", 2)[0];
                    if (user != null && !user.isEmpty()) return user;
                }
            } catch (Exception ignored) {}
        }
        return defaultUser;
    }

    public static String extractPassword(String rawUrl, String defaultPass) {
        if (rawUrl != null && rawUrl.contains("://")) {
            String urlToParse = rawUrl.trim();
            if (urlToParse.startsWith("jdbc:")) urlToParse = urlToParse.substring(5);
            try {
                URI uri = new URI(urlToParse);
                if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
                    String pass = uri.getUserInfo().split(":", 2)[1];
                    if (pass != null && !pass.isEmpty()) return pass;
                }
            } catch (Exception ignored) {}
        }
        return defaultPass;
    }
}
