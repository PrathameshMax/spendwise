package com.prathmesh.spendwise.transactionservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanUp(){
        MDC.clear();
    }

    @Test
    void shouldGenerateAndReturnRequestIdWhenHeaderIsMissing()
            throws Exception {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        FilterChain filterChain =
                mock(FilterChain.class);

        when(request.getHeader("X-Request-ID"))
                .thenReturn(null);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        ArgumentCaptor<String> captor =
                ArgumentCaptor.forClass(String.class);

        verify(response).setHeader(
                eq("X-Request-ID"),
                captor.capture()
        );

        String generatedRequestId =
                captor.getValue();

        assertNotNull(generatedRequestId);

        assertDoesNotThrow(() ->
                java.util.UUID.fromString(generatedRequestId)
        );

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void shouldReuseExistingRequestId() throws Exception {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        FilterChain filterChain =
                mock(FilterChain.class);

        when(request.getHeader("X-Request-ID"))
                .thenReturn("test-request-id");

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnExistingRequestIdInResponseHeader()
            throws Exception {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        FilterChain filterChain =
                mock(FilterChain.class);

        String requestId = "my-test-request-123";

        when(request.getHeader("X-Request-ID"))
                .thenReturn(requestId);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(response).setHeader(
                "X-Request-ID",
                requestId
        );

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void shouldClearRequestIdFromMdcAfterRequest()
            throws Exception {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        FilterChain filterChain =
                mock(FilterChain.class);

        when(request.getHeader("X-Request-ID"))
                .thenReturn("test-request-id");

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(MDC.get("requestId"));
    }
}
