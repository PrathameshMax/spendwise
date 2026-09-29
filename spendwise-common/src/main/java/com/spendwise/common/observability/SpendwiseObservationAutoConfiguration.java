package com.spendwise.common.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ObservationPredicate.class, ServerRequestObservationContext.class})
public class SpendwiseObservationAutoConfiguration {

    private static final String ACTUATOR_PREFIX = "/actuator";

    @Bean(name = "actuatorObservationExclusion")
    @ConditionalOnMissingBean(name = "actuatorObservationExclusion")
    public ObservationPredicate actuatorObservationExclusion() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                String uri = serverContext.getCarrier().getRequestURI();
                return uri == null || !uri.startsWith(ACTUATOR_PREFIX);
            }
            return true;
        };
    }
}