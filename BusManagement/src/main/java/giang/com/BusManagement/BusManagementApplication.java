package giang.com.BusManagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bảo mật (Spring Security) ở trạng thái permit-all có chủ đích — xem
 * config/SecurityConfig: một filter chain duy nhất, mọi request được phép,
 * CSRF / form-login / http-basic đều tắt. Hệ thống KHÔNG có đường đăng nhập.
 *
 * Vì thế user in-memory mặc định của Boot (UserDetailsServiceAutoConfiguration —
 * thứ sinh "Using generated security password: …" ở mỗi lần khởi động) bị loại
 * ngay tại đây: không có ai để đăng nhập thì không có user mặc định. Loại bằng
 * annotation chứ không bằng spring.autoconfigure.exclude trong properties, vì
 * property trỏ sai tên class thì Boot bỏ qua IM LẶNG (đã xảy ra khi lên Boot 4 —
 * xem docs/todo/current_bugs_found.md, mục nhỏ 2026-09-19), còn annotation trỏ
 * sai class thì không biên dịch được.
 *
 * Các auto-config security khác giữ nguyên: SecurityConfig cần bean HttpSecurity
 * do ServletWebSecurityAutoConfiguration cung cấp.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class BusManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(BusManagementApplication.class, args);
	}

}
