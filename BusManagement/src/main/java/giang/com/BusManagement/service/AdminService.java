package giang.com.BusManagement.service;

import org.springframework.stereotype.Service;

import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;

    /**
     * Tạo mới một tài khoản. Entity phải chưa có id — tripwire cùng khuôn
     * BusService.saveBus() (lỗi #16), áp cho đường này vì lỗi #21: AdminController
     * bind User bằng @ModelAttribute không có @InitBinder, nên một POST tự chế mang
     * id có sẵn sẽ khiến save() thành MERGE — đã tái hiện thật: một user tài xế bị
     * đổi thành ROLE_ADMIN kèm mật khẩu mới bằng một request.
     */
    @Transactional
    public void createNewUser(User user) {
        if (user.getId() != null) {
            throw new IllegalArgumentException(
                    "createNewUser() chỉ dùng để tạo tài khoản mới (id phải trống). Tài khoản #" + user.getId()
                            + " đã tồn tại.");
        }
        // Thực tế nên mã hóa password tại đây:
        // user.setPassword(passwordEncoder.encode(...));
        userRepository.save(user);
    }
}