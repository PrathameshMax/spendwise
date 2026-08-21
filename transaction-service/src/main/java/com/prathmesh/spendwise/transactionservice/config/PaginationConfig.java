package com.prathmesh.spendwise.transactionservice.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class PaginationConfig implements WebMvcConfigurer{

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    @Bean
    public PageableHandlerMethodArgumentResolver pageableResolver() {

        PageableHandlerMethodArgumentResolver resolver =
                new PageableHandlerMethodArgumentResolver();

        resolver.setFallbackPageable(
                PageRequest.of(
                        DEFAULT_PAGE,
                        DEFAULT_SIZE
                )
        );

        resolver.setMaxPageSize(MAX_SIZE);

        return resolver;
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers) {

        resolvers.add(pageableResolver());
    }
}
