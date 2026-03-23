package com.splitmate.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "SplitMate API",
        version = "1.0.0",
        description = "Expense sharing REST API — Splitwise clone powered by Supabase Auth & PostgreSQL",
        contact = @Contact(name = "SplitMate")
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Paste your Supabase JWT access token"
)
public class OpenApiConfig {}
