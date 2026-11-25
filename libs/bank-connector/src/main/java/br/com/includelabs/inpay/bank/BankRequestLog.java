package br.com.includelabs.inpay.bank;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.servlet.HandlerInterceptor;

/** Log de borda HTTP, sem cabeçalho de autorização e sem corpo. */
public final class BankRequestLog implements HandlerInterceptor {
    private static final Logger LOG = LogManager.getLogger(BankRequestLog.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LOG.info("HTTP bank request started method={} path={}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (exception == null) {
            LOG.info(
                    "HTTP bank request completed method={} path={} status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
            return;
        }
        LOG.error(
                "HTTP bank request failed method={} path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
    }
}
