package vn.conganh.commercial.security.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    @Test
    void filter_generatesServerIdAndAlwaysClearsMdc() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "forged-client-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).matches("[0-9a-f-]{36}"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .matches("[0-9a-f-]{36}")
                .isNotEqualTo("forged-client-id");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
