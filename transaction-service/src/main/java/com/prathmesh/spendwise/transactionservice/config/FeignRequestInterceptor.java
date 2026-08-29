package com.prathmesh.spendwise.transactionservice.config;

import com.prathmesh.spendwise.transactionservice.constants.RequestIdConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class FeignRequestInterceptor implements RequestInterceptor {


    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    public void apply(RequestTemplate template) {

        System.out.println(
                "AUTH = " +
                        SecurityContextHolder.getContext().getAuthentication()
        );

        String requestId = MDC.get(MDC_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()){
            template.header(RequestIdConstants.REQUEST_ID_HEADER, requestId);
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken){
            String tokenValue = jwtAuthenticationToken
                    .getToken()
                    .getTokenValue();

            System.out.println("Feign JWT found = " + !tokenValue.isBlank());

            template
                    .header("Authorization","Bearer "+ tokenValue);
        } else {
            System.out.println("Feign JWT NOT FOUND");
        }

    }
}
