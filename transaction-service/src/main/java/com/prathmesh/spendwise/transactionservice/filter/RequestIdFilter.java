package com.prathmesh.spendwise.transactionservice.filter;

import com.prathmesh.spendwise.transactionservice.constants.RequestIdConstants;
import com.prathmesh.spendwise.transactionservice.util.RequestIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(RequestIdConstants.REQUEST_ID_HEADER);

        if (requestId == null || requestId.isBlank()){
            requestId = RequestIdGenerator.generate();
        }
        try {
            MDC.put(MDC_REQUEST_ID, requestId);

            response.setHeader(RequestIdConstants.REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }

    }
}
