package vn.vnpost.example.common.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import vn.vnpost.example.common.utils.AuthUtils;
import vn.vnpost.example.common.utils.RequestContext;
import vn.vnpost.shared.sercurity.CheckPermission;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckPermissionAspect {

    final PermissionClient permissionClient;

    @Around("@annotation(vn.vnpost.shared.sercurity.CheckPermission)")
    public Object checkAccess(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
//        return proceedingJoinPoint.proceed();
        Object result = proceedingJoinPoint.proceed();

        String requestId = RequestContext.getRequestId();
        String methodName = proceedingJoinPoint.getSignature().toShortString();

        MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
        CheckPermission checkPermission = signature.getMethod().getAnnotation(CheckPermission.class);

        if (checkPermission == null) {
            return result;
        }

        String api = String.format("%s/%s",
                proceedingJoinPoint.getTarget().getClass().getSimpleName().toLowerCase(),
                checkPermission.index());

        Mono<Void> checkPermissionMono = AuthUtils.isSuperUser()
                .flatMap(isSuperUser -> {

                    if (Boolean.TRUE.equals(isSuperUser)) {
                        return Mono.empty();
                    }

                    return AuthUtils.getJwt()
                            .switchIfEmpty(Mono.error(
                                    new AccessDeniedException("Bạn chưa đăng nhập hoặc token không hợp lệ")
                            ))
                            .flatMap(jwt -> {
                                log.info("START checkAccess: requestId={}, method={}, permissionTitle={}, permissionIndex={}",
                                        requestId,
                                        methodName,
                                        checkPermission.title(),
                                        checkPermission.index());

                                return permissionClient.getPermissions(jwt.getTokenValue())
                                        .onErrorResume(e -> {
                                            log.error("Lỗi gọi permission API: requestId={}, method={}, error={}",
                                                    requestId, methodName, e.getMessage(), e);

                                            if (e instanceof WebClientResponseException wcre
                                                    && wcre.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                                                return Mono.error(new ResponseStatusException(
                                                        HttpStatus.UNAUTHORIZED,
                                                        "Token không hợp lệ hoặc đã hết hạn, vui lòng đăng nhập lại"
                                                ));
                                            }

                                            return Mono.error(new AccessDeniedException(
                                                    "Không thể xác thực quyền truy cập, vui lòng thử lại sau"
                                            ));
                                        })
                                        .flatMap(userPermissions -> {
                                            if (!userPermissions.contains(api)) {
                                                log.warn("ACCESS DENIED: method={}, requiredApi={}, userPermissions={}, requestId={}",
                                                        methodName,
                                                        api,
                                                        userPermissions,
                                                        requestId);

                                                return Mono.error(new AccessDeniedException(
                                                        "Bạn không có quyền truy cập chức năng: " + checkPermission.title()
                                                ));
                                            }

                                            return Mono.empty();
                                        });
                            });
                });

        if (result instanceof Mono<?> mono) {
            return checkPermissionMono.then(mono);
        }

        if (result instanceof Flux<?> flux) {
            return checkPermissionMono.thenMany(flux);
        }

        return result;
    }

}
