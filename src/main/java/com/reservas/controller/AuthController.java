package com.reservas.controller;

import com.reservas.dto.request.ActualizarPerfilRequest;
import com.reservas.dto.request.GoogleAuthRequest;
import com.reservas.dto.request.LoginRequest;
import com.reservas.dto.request.RegisterRequest;
import com.reservas.dto.request.ReenviarEmailRequest;
import com.reservas.dto.request.VerificarEmailRequest;
import com.reservas.dto.response.LoginResponse;
import com.reservas.dto.response.UserResponse;
import com.reservas.dto.response.ApiResponse;
import com.reservas.service.AuthService;
import com.reservas.service.EmailVerificationService;
import com.reservas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Value("${rate.limit.enabled:true}")
    private boolean rateLimitEnabled;

    /** true en producción (HTTPS), false en desarrollo (HTTP) */
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    /** Strict en desarrollo, None en producción (cross-site DO domains) */
    @Value("${app.cookie.same-site:Strict}")
    private String cookieSameSite;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // Rate limiting en registro
        if (rateLimitEnabled) {
            String clientIp = getClientIP(httpRequest);
            if (!rateLimitService.tryConsume(clientIp + ":register")) {
                log.warn("Rate limit excedido para IP: {} en endpoint /register", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.builder().success(false)
                                .message("Demasiados intentos. Por favor, intenta más tarde.").build());
            }
        }

        try {
            var response = authService.registrar(request, httpRequest);
            setAuthCookie(httpResponse, response.getToken());
            response.setToken(null); // No exponer el token en el body
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.builder()
                            .success(true)
                            .message("Usuario registrado exitosamente")
                            .data(response)
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // Rate limiting por IP (solo si está habilitado)
        if (rateLimitEnabled) {
            String clientIp = getClientIP(httpRequest);
            String rateLimitKey = clientIp + ":login";

            if (!rateLimitService.tryConsume(rateLimitKey)) {
                log.warn("Rate limit excedido para IP: {} en endpoint /login", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message("Demasiados intentos de inicio de sesión. Por favor, intenta más tarde.")
                            .build());
            }
        }

        try {
            LoginResponse response = authService.login(request);
            setAuthCookie(httpResponse, response.getToken());
            response.setToken(null); // No exponer el token en el body
            log.info("Login exitoso para usuario: {}", request.getEmail());
            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Login exitoso")
                    .data(response)
                    .build());
        } catch (Exception e) {
            log.error("Intento de login fallido para: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message("Credenciales inválidas")
                            .build());
        }
    }

    /**
     * Obtener IP del cliente considerando proxies
     */
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    /**
     * Establece la cookie httpOnly con el JWT.
     * SameSite=Strict previene CSRF sin necesidad de token adicional.
     */
    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Invalida la cookie de sesión enviando una cookie vacía con maxAge=0.
     */
    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletResponse httpResponse) {
        clearAuthCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Sesión cerrada exitosamente")
                .build());
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<?>> test() {
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("API funcionando correctamente")
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> getCurrentUser() {
        try {
            UserResponse userResponse = authService.obtenerUsuarioActual();

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Usuario obtenido exitosamente")
                    .data(userResponse)
                    .build());
        } catch (Exception e) {
            log.error("Error al obtener usuario actual: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * Autenticación con Google OAuth2
     * Login o Registro automático con cuenta de Google
     */
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<?>> googleAuth(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // Rate limiting por IP (solo si está habilitado)
        if (rateLimitEnabled) {
            String clientIp = getClientIP(httpRequest);
            String rateLimitKey = clientIp + ":google-auth";

            if (!rateLimitService.tryConsume(rateLimitKey)) {
                log.warn("Rate limit excedido para IP: {} en endpoint /google", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.builder()
                                .success(false)
                                .message("Demasiados intentos. Por favor, intenta más tarde.")
                                .build());
            }
        }

        try {
            LoginResponse response = authService.googleAuth(request, httpRequest);
            setAuthCookie(httpResponse, response.getToken());
            response.setToken(null); // No exponer el token en el body
            log.info("Autenticación con Google exitosa para: {}", response.getEmail());
            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Autenticación con Google exitosa")
                    .data(response)
                    .build());
        } catch (Exception e) {
            log.error("Error en autenticación con Google: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message("Error en autenticación con Google: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Verificar email con token
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<?>> verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        try {
            boolean verificado = emailVerificationService.verificarEmail(request.getToken());

            if (verificado) {
                return ResponseEntity.ok(ApiResponse.builder()
                        .success(true)
                        .message("Email verificado exitosamente")
                        .build());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.builder()
                                .success(false)
                                .message("Token inválido o expirado")
                                .build());
            }
        } catch (Exception e) {
            log.error("Error verificando email: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message("Error al verificar email")
                            .build());
        }
    }

    /**
     * Actualizar perfil del usuario autenticado
     */
    @PutMapping("/perfil")
    public ResponseEntity<ApiResponse<?>> actualizarPerfil(@Valid @RequestBody ActualizarPerfilRequest request) {
        try {
            var response = authService.actualizarPerfil(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Perfil actualizado exitosamente")
                    .data(response)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error al actualizar perfil: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message("Error al actualizar el perfil")
                            .build());
        }
    }

    /**
     * Reenviar email de verificación
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<?>> reenviarVerificacion(
            @Valid @RequestBody ReenviarEmailRequest request,
            HttpServletRequest httpRequest) {

        // Rate limiting en reenvío de verificación
        if (rateLimitEnabled) {
            String clientIp = getClientIP(httpRequest);
            if (!rateLimitService.tryConsume(clientIp + ":resend-verification")) {
                log.warn("Rate limit excedido para IP: {} en endpoint /resend-verification", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.builder().success(false)
                                .message("Demasiados intentos. Por favor, intenta más tarde.").build());
            }
        }

        try {
            emailVerificationService.reenviarEmailVerificacion(request.getEmail());

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Email de verificación reenviado")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error reenviando email de verificación: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .message("Error al reenviar email")
                            .build());
        }
    }

}
