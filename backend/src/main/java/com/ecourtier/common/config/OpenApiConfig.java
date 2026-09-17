package com.ecourtier.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("E-Courtier API — Sénégal")
                .version("1.0.0")
                .description("API REST pour la plateforme intelligente de recherche de logement au Sénégal.")
                .contact(new Contact()
                    .name("Équipe E-Courtier")
                    .email("contact@e-courtier.sn"))
                .license(new License()
                    .name("Propriétaire")
                    .url("https://e-courtier.sn")))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Saisissez le token JWT (obtenu lors de la connexion) pour vous authentifier.")));
    }
}
