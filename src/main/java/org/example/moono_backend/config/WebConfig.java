package org.example.moono_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS 설정
 * 
 * 프론트엔드(localhost:5173, localhost:5174)에서 백엔드 API 호출을 위한 설정
 * application.yml의 cors.allowed-origins 값을 사용
 */
@Configuration
@ConfigurationProperties(prefix = "cors")
@Getter
@Setter
public class WebConfig implements WebMvcConfigurer {

    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:5174");

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // /api로 시작하는 모든 경로에 적용
                .allowedOrigins(allowedOrigins.toArray(new String[0])) // yml에서 읽어온 origins
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(false) // 인증 사용 안함 (쿠키, 인증 헤더 전송 안함)
                .maxAge(3600); // preflight 요청 캐시 시간 (1시간)
    }
}
