package com.ecourtier.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String API_ERROR_SCHEMA_NAME = "ApiErrorResponse";

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

    @Bean
    public OpenApiCustomizer apiErrorResponseCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(API_ERROR_SCHEMA_NAME, buildApiErrorSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getResponses() == null) {
                        return;
                    }
                    operation.getResponses().forEach((code, response) -> {
                        if ((code.startsWith("4") || code.startsWith("5")) && !hasJsonContent(response)) {
                            Content content = response.getContent() != null ? response.getContent() : new Content();
                            MediaType mediaType = new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR_SCHEMA_NAME));
                            content.addMediaType("application/json", mediaType);
                            response.setContent(content);
                        }
                    });
                })
            );
        };
    }

    @SuppressWarnings("rawtypes")
    private Schema buildApiErrorSchema() {
        return new ObjectSchema()
            .description("Format standard d'erreur REST retourné par GlobalExceptionHandler")
            .addProperty("timestamp", new DateTimeSchema().description("Horodatage de l'erreur (UTC/Dakar)"))
            .addProperty("status", new IntegerSchema().description("Code HTTP (ex: 400, 401, 403, 404, 409, 429, 500)"))
            .addProperty("error", new StringSchema().description("Message d'erreur principal lisible"))
            .addProperty("erreurs", new MapSchema()
                .additionalProperties(new StringSchema())
                .description("Détails de validation par champ (présent sur les erreurs 400 MethodArgumentNotValidException)"));
    }

    private boolean hasJsonContent(ApiResponse response) {
        return response.getContent() != null && response.getContent().containsKey("application/json");
    }
}
