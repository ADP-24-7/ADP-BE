package com.adp.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "adpApiKey";

    @Bean
    OpenAPI adpOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("ADP Gateway Runtime API")
                .description("Policy enforcement, privacy transform, egress, and controlled delivery APIs")
                .version("BE-7"))
            .components(new Components().addSecuritySchemes(
                API_KEY_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-ADP-API-Key")
            ))
            .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
