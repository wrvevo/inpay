package br.com.includelabs.inpay.bank;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Log de borda HTTP, sem cabeçalho de autorização e sem corpo. */
public final class BankRequestLog implements HandlerInterceptor {
    private static final Logger LOG = LogManager.getLogger(BankRequestLog.class);

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        LOG.info(
                "HTTP bank request started method={} path={}",
                request.getMethod(),
                route(request));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        if (exception == null) {
            LOG.info(
                    "HTTP bank request completed method={} path={} status={}",
                    request.getMethod(),
                    route(request),
                    response.getStatus());
            return;
        }
        LOG.error(
                "HTTP bank request failed method={} path={}",
                request.getMethod(),
                route(request));
    }

    /** Registra somente a rota declarada; parâmetros e URI fornecida pelo cliente ficam de fora. */
    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? "unmapped" : pattern.toString();
    }
}
