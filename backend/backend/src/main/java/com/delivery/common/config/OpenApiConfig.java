package com.delivery.common.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Configures OpenAPI metadata for the delivery marketplace REST API. */
@Configuration
public class OpenApiConfig {
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Bean
    OpenAPI deliveryMarketplaceOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Delivery Marketplace API")
                        .version("0.1.0")
                        .description("Simple layered MVP API under /api/v1 Developed by Xavier Francisco for the Delivery Marketplace project."))
                .components(new Components().addSecuritySchemes(schemeName, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }

    @Bean
    OpenApiCustomizer tenantHeaderOpenApiCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/tenants")) {
                return;
            }
            pathItem.readOperations().forEach(operation -> operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(TENANT_HEADER)
                    .required(true)
                    .description("Active tenant UUID. Not required when the JWT has a tenant_id claim.")
                    .schema(new StringSchema().format("uuid"))));
        });
    }
}
