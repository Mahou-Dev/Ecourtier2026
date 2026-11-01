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

    @Value("${spring.datasource.url:}")
    private String rawUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        boolean isH2Driver = driverClassName != null && driverClassName.toLowerCase().contains("h2");
        String finalUrl = resolveJdbcUrl(rawUrl, driverClassName);
        String finalUsername = isH2Driver && (username == null || username.isBlank())
                ? "sa"
                : extractUsername(rawUrl, username);
        String finalPassword = isH2Driver && password == null
                ? ""
                : extractPassword(rawUrl, password);

        log.info("[DATABASE CONFIG] Initialisation du DataSource ({}) avec l'URL JDBC : {}", driverClassName, finalUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(finalUrl);
        config.setUsername(finalUsername);
        config.setPassword(finalPassword);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setInitializationFailTimeout(30000); // 30s timeout pour les cold starts cloud

        return new HikariDataSource(config);
    }

    public static String resolveJdbcUrl(String rawUrl, String driverClassName) {
        boolean isH2Driver = driverClassName != null && driverClassName.toLowerCase().contains("h2");
        if (isH2Driver) {
            if (rawUrl != null && rawUrl.trim().startsWith("jdbc:h2:")) {
                return rawUrl.trim();
            }
            return "jdbc:h2:mem:ecourtier_mem;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        }
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Démarrage refusé : SPRING_DATASOURCE_URL (spring.datasource.url) est obligatoire."
            );
        }
        return formatJdbcUrl(rawUrl);
    }

    public static String formatJdbcUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
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
