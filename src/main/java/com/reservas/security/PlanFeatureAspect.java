package com.reservas.security;

import com.reservas.exception.LimiteExcedidoException;
import com.reservas.service.ModuloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspecto para validar acceso a módulos antes de ejecutar un método.
 * Verifica que el negocio del usuario autenticado tenga activo el módulo
 * indicado en la anotación {@link RequiresPlanFeature}.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PlanFeatureAspect {

    private final ModuloService moduloService;

    @Before("@annotation(com.reservas.security.RequiresPlanFeature)")
    public void checkPlanFeature(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresPlanFeature annotation = method.getAnnotation(RequiresPlanFeature.class);

        if (annotation == null) {
            return;
        }

        String clave = annotation.value();
        String customMessage = annotation.message();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Usuario no autenticado intentando acceder al módulo: {}", clave);
            throw new LimiteExcedidoException("Debe iniciar sesión para acceder a esta funcionalidad", 0, 0);
        }

        String email = authentication.getName();
        log.info("Verificando módulo '{}' para usuario: {}", clave, email);

        boolean tieneAcceso = moduloService.tieneModuloPorEmail(email, clave);

        if (!tieneAcceso) {
            String mensaje = customMessage.isEmpty()
                    ? String.format("El módulo '%s' no está activo en tu cuenta. Actívalo desde el Marketplace.", clave)
                    : customMessage;

            log.warn("Acceso denegado al módulo '{}' para usuario: {}", clave, email);
            throw new LimiteExcedidoException(mensaje, 0, 0);
        }

        log.info("Acceso concedido al módulo '{}' para usuario: {}", clave, email);
    }
}
