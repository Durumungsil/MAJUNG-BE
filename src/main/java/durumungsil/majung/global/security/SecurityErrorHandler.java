package durumungsil.majung.global.security;

import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 시큐리티 필터 단계의 인증/인가 실패(401/403)를 GlobalExceptionHandler 로 넘겨서
 * 다른 API 와 같은 ErrorResponse 형식으로 응답한다.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityErrorHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /** 401 - 토큰이 없으면 UNAUTHORIZED, 토큰이 있는데 잘못됐거나 만료됐으면 INVALID_TOKEN */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) {
        ErrorCode code = (e instanceof OAuth2AuthenticationException) ? ErrorCode.INVALID_TOKEN : ErrorCode.UNAUTHORIZED;
        resolver.resolveException(request, response, null, new BusinessException(code));
    }

    /** 403 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) {
        resolver.resolveException(request, response, null, new BusinessException(ErrorCode.ACCESS_DENIED));
    }
}
