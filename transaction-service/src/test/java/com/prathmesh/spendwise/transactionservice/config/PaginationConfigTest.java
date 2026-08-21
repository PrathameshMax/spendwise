package com.prathmesh.spendwise.transactionservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.*;

public class PaginationConfigTest {

    private final PaginationConfig config =
            new PaginationConfig();

    @Test
    void pageableResolver_shouldUseDefaultPageAndSize() {

        PageableHandlerMethodArgumentResolver resolver =
                config.pageableResolver();

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        Pageable pageable =
                resolve(
                        resolver,
                        request
                );

        assertEquals(
                0,
                pageable.getPageNumber()
        );

        assertEquals(
                20,
                pageable.getPageSize()
        );
    }


    @Test
    void pageableResolver_shouldAcceptRequestedPageAndSize() {

        PageableHandlerMethodArgumentResolver resolver =
                config.pageableResolver();

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addParameter(
                "page",
                "2"
        );

        request.addParameter(
                "size",
                "50"
        );

        Pageable pageable =
                resolve(
                        resolver,
                        request
                );

        assertEquals(
                2,
                pageable.getPageNumber()
        );

        assertEquals(
                50,
                pageable.getPageSize()
        );
    }


    @Test
    void pageableResolver_shouldLimitMaximumPageSize() {

        PageableHandlerMethodArgumentResolver resolver =
                config.pageableResolver();

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addParameter(
                "page",
                "0"
        );

        request.addParameter(
                "size",
                "5000"
        );

        Pageable pageable =
                resolve(
                        resolver,
                        request
                );

        assertEquals(
                100,
                pageable.getPageSize()
        );
    }


    @Test
    void pageableResolver_shouldUseDefaultSizeWhenSizeIsMissing() {

        PageableHandlerMethodArgumentResolver resolver =
                config.pageableResolver();

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addParameter(
                "page",
                "3"
        );

        Pageable pageable =
                resolve(
                        resolver,
                        request
                );

        assertEquals(
                3,
                pageable.getPageNumber()
        );

        assertEquals(
                20,
                pageable.getPageSize()
        );
    }


    private Pageable resolve(
            PageableHandlerMethodArgumentResolver resolver,
            MockHttpServletRequest request) {

        try {

            var methodParameter =
                    new org.springframework.core.MethodParameter(
                            TestController.class
                                    .getDeclaredMethod(
                                            "test",
                                            Pageable.class
                                    ),
                            0
                    );

            return resolver.resolveArgument(
                    methodParameter,
                    null,
                    new ServletWebRequest(request),
                    null
            );

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


    static class TestController {

        public void test(Pageable pageable) {
        }
    }
}
