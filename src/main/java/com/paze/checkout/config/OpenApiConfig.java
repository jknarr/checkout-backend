package com.paze.checkout.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pazeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Paze Checkout API")
                        .version("1.0")
                        .description("Embedded checkout system with phone + OTP + CVV authentication"));
    }
}
