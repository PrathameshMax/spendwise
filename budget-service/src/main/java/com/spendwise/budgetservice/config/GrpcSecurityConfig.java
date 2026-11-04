package com.spendwise.budgetservice.config;

import com.spendwise.budgetservice.grpc.JwtGrpcServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GlobalServerInterceptorConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Applies {@link JwtGrpcServerInterceptor} to every gRPC service this application
 * exposes, reusing the {@link JwtDecoder} the HTTP resource server uses.
 */
@Configuration
public class GrpcSecurityConfig {

    @Bean
    public GlobalServerInterceptorConfigurer jwtGrpcServerInterceptorConfigurer(JwtDecoder jwtDecoder) {
        JwtGrpcServerInterceptor interceptor = new JwtGrpcServerInterceptor(jwtDecoder);
        return interceptors -> interceptors.add(interceptor);
    }
}
