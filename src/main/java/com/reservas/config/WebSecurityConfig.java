package com.reservas.config;

import com.reservas.security.JwtAuthenticationFilter;
import com.reservas.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // Orígenes adicionales de producción (landing vs. app subdomain)
    @Value("${app.frontend.extra-origins:}")
    private String extraOriginsRaw;

    // Swagger/OpenAPI: solo accesible cuando está habilitado (dev). En prod: false
    @Value("${springdoc.api-docs.enabled:true}")
    private boolean swaggerEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Orígenes base (desarrollo)
        List<String> allowed = new java.util.ArrayList<>(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:3000"
        ));

        // Origen principal de producción (env var FRONTEND_URL)
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            allowed.add(frontendUrl);
        }

        // Orígenes adicionales de producción separados por coma
        // Ejemplo: FRONTEND_EXTRA_ORIGINS=https://citaclick.com.mx,https://www.citaclick.com.mx
        if (extraOriginsRaw != null && !extraOriginsRaw.isBlank()) {
            for (String origin : extraOriginsRaw.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isBlank()) allowed.add(trimmed);
            }
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowed);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-Correlation-Id",
            "Accept"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write("{\"error\": \"Unauthorized\"}");
                        })
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> {
                    // Rutas públicas - Sin /api porque context-path=/api ya lo agrega
                    authz.requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                         .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                         .requestMatchers(HttpMethod.POST, "/auth/google").permitAll()
                         .requestMatchers(HttpMethod.POST, "/auth/verify-email").permitAll()
                         .requestMatchers(HttpMethod.POST, "/auth/resend-verification").permitAll()
                         .requestMatchers("/auth/test").permitAll();

                    // Swagger/OpenAPI UI — solo accesible si está habilitado (dev).
                    // En producción, application-prod.yml lo desactiva con springdoc.api-docs.enabled=false
                    if (swaggerEnabled) {
                        authz.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                    }

                    // Stripe Webhooks (NO debe tener autenticación JWT)
                    authz.requestMatchers(HttpMethod.POST, "/webhooks/stripe").permitAll()
                         // Twilio Webhooks (NO debe tener autenticación JWT)
                         .requestMatchers(HttpMethod.POST, "/webhooks/twilio/**").permitAll()
                         // Endpoint público para que el cliente final cargue datos del pago
                         .requestMatchers(HttpMethod.GET, "/v1/payments/public/**").permitAll()
                         // Admin scheduler: sin JWT, protegido por X-Admin-Key header
                         .requestMatchers(HttpMethod.GET, "/admin/scheduler/**").permitAll()
                         // Todas las demás rutas requieren autenticación
                         .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}