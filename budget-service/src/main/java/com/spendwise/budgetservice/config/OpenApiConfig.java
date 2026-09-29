package com.spendwise.budgetservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi metadata (Milestone 7) — {@code /v3/api-docs} and
 * {@code /swagger-ui.html} are auto-exposed by the springdoc starter on the
 * classpath with zero further configuration; this bean only supplies the
 * human-readable title/description/version shown in Swagger UI's header.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SpendWise :: Budget Service")
                        .version("v1")
                        .description("Spending caps and budget tracking. "
                                + "URI-based versioning (/api/v1/...) — see the platform README's "
                                + "\"API Versioning & Deprecation Policy\" section for how a future "
                                + "/api/v2/... would coexist and be deprecated."));
    }
}
