package giang.com.BusManagement.service;

import org.springframework.stereotype.Service;

import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;

    /**
     * Tạo mới một tài khoản KHÔNG phải tài xế (ROLE_ADMIN / ROLE_USER).
     *
     * Ba ràng buộc, cùng lớp với các service khác (lỗi #21, #25):
     * <ul>
     * <li>entity phải chưa có id — tripwire khuôn BusService.saveBus(): controller
     * bind User bằng @ModelAttribute không có @InitBinder, nên một POST tự chế mang
     * id có sẵn sẽ khiến save() thành MERGE (đã tái hiện: một user tài xế bị đổi
     * thành ROLE_ADMIN kèm mật khẩu mới bằng một request);</li>
     * <li>username chưa tồn tại — trước đây không kiểm, để ràng buộc unique của DB
     * ném DataIntegrityViolationException và controller (không try/catch) trả HTTP
     * 500 (lỗi #25);</li>
     * <li>KHÔNG nhận ROLE_DRIVER — Driver dùng @MapsId nên một user tài xế phải đi
     * kèm một hồ sơ Driver, và Phase 1 đã chốt (owner-approved) rằng cặp đó được
     * tạo bằng MỘT form gộp ở DriverService.createDriver(). Tạo user ROLE_DRIVER
     * ở đây sinh ra đúng bản ghi "mồ côi" mà DriverService.deleteDriver() cảnh
     * báo: không màn nào thấy, không màn nào sửa được, không gắn được hồ sơ.</li>
     * </ul>
     * Ném IllegalArgumentException như mọi vi phạm đầu vào khác, để controller hiện
     * flash "Lỗi: …".
     */
    @Transactional
    public void createNewUser(User user) {
        if (user.getId() != null) {
            throw new IllegalArgumentException(
                    "createNewUser() chỉ dùng để tạo tài khoản mới (id phải trống). Tài khoản #" + user.getId()
                            + " đã tồn tại.");
        }
        if (user.getRole() == Role.ROLE_DRIVER) {
            throw new IllegalArgumentException(
                    "Tài khoản tài xế phải được tạo ở màn Quản Lý Tài Xế (/admin/drivers/create), nơi tạo kèm hồ sơ"
                            + " Driver — tạo riêng ở đây sẽ sinh ra tài khoản tài xế không có hồ sơ, không màn nào quản lý được.");
        }
        requireUsernameAvailable(user.getUsername(), null);
        // Thực tế nên mã hóa password tại đây:
        // user.setPassword(passwordEncoder.encode(...));
        userRepository.save(user);
    }

    /**
     * Luật "tên đăng nhập là duy nhất" — MỘT chỗ cho cả hai đường tạo/sửa tài
     * khoản trong hệ thống (màn này và DriverService, vốn từng giữ bản riêng).
     * Kiểm ở tầng service thay vì để ràng buộc unique của DB ném ngoại lệ kỹ
     * thuật, để người dùng nhận một câu nghiệp vụ.
     *
     * @param currentUserId id của tài khoản đang sửa (null khi tạo mới) — để không
     *                      tự báo trùng với chính mình.
     * @throws IllegalArgumentException nếu username đã thuộc về tài khoản khác
     */
    public void requireUsernameAvailable(String username, Long currentUserId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Tên đăng nhập không được để trống!");
        }
        userRepository.findByUsername(username).ifPresent(found -> {
            if (!found.getId().equals(currentUserId)) {
                throw new IllegalArgumentException(
                        "Tên đăng nhập '" + username + "' đã tồn tại, vui lòng chọn tên khác!");
            }
        });
    }
}
