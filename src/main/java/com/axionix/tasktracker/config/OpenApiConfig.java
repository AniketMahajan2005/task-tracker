package com.axionix.tasktracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI taskTrackerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Team Task Tracker API")
                        .version("1.0.0")
                        .description("REST API for projects, membership and tasks. "
                                + "Register, log in, then click 'Authorize' and paste the access_token."))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Applied to every operation; the public /auth endpoints opt out with @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
