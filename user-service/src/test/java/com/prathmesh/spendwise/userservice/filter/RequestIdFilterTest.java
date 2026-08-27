package com.prathmesh.spendwise.userservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderIsMissing() throws Exception {

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("X-Request-ID"))
                .thenReturn(null);

        doAnswer(invocation -> {

            String requestId = MDC.get("requestId");

            assertNotNull(requestId);
            assertFalse(requestId.isBlank());

            return null;

        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(
                eq("X-Request-ID"),
                anyString()
        );

        verify(filterChain).doFilter(request, response);

        assertNull(MDC.get("requestId"));
    }

    @Test
    void shouldReuseExistingRequestId() throws Exception {

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        String requestId = "my-test-request-123";

        when(request.getHeader("X-Request-ID"))
                .thenReturn(requestId);

        doAnswer(invocation -> {

            assertEquals(
                    requestId,
                    MDC.get("requestId")
            );

            return null;

        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(
                "X-Request-ID",
                requestId
        );

        verify(filterChain).doFilter(request, response);

        assertNull(MDC.get("requestId"));
    }

    @Test
    void shouldRemoveMdcWhenFilterChainThrowsException() throws Exception {

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("X-Request-ID"))
                .thenReturn("failure-test-request");

        doThrow(new RuntimeException("Test failure"))
                .when(filterChain)
                .doFilter(request, response);

        assertThrows(
                RuntimeException.class,
                () -> filter.doFilterInternal(
                        request,
                        response,
                        filterChain
                )
        );

        assertNull(MDC.get("requestId"));
    }
}
