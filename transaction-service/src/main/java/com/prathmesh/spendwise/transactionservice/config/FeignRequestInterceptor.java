package com.prathmesh.spendwise.transactionservice.config;

import com.prathmesh.spendwise.transactionservice.constants.RequestIdConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class FeignRequestInterceptor implements RequestInterceptor {


    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    public void apply(RequestTemplate template) {

        String requestId = MDC.get(MDC_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()){
            template.header(RequestIdConstants.REQUEST_ID_HEADER, requestId);
        }
    }
}
