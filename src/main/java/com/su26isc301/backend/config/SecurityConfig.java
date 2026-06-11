package com.su26isc301.backend.config;

import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// CÁC DÒNG IMPORT MỚI CHO CORS
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/uploads/**");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/vendors/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/vendors/register/start").permitAll()
                        .requestMatchers(HttpMethod.POST, "/vendors/register/verify-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/vendors/register/complete").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/identity/cccd/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/identity/cccd/verify-with-face").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/subscription/webhook/payos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority(Roles.admin.name())
                        .requestMatchers(
                                "/api/auth/**",
                                "/uploads/**",
                                "/vendors/register/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                    {"success":false,"message":"Access denied: %s","path":"%s"}
                    """.formatted(accessDeniedException.getMessage(), request.getRequestURI()));
        };
    }

    // THÊM TOÀN BỘ CỤC NÀY VÀO ĐỂ TRỊ DỨT ĐIỂM LỖI CORS CỦA SPRING SECURITY
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép Swagger/local frontend gọi API trong lúc dev.
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://5bros.vercel.app"
        ));

        // Cho phép các method
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Cho phép tất cả các header
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Cho phép gửi cookie/token
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Áp dụng cấu hình trên cho tất cả các đường dẫn API
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
