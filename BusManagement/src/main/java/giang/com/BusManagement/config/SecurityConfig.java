package giang.com.BusManagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Cho phép tất cả truy cập không cần mật khẩu
                )
                .csrf(csrf -> csrf.disable()) // Tắt bảo vệ CSRF để không bị lỗi khi làm Form
                .formLogin(form -> form.disable()) // Tắt trang login mặc định
                .httpBasic(basic -> basic.disable()); // Tắt hộp thoại nhập user/pass của trình duyệt

        return http.build();
    }
}