package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Quản lý hồ sơ tài xế (CRUD).
 *
 * Driver dùng @MapsId — khóa chính của Driver CHÍNH LÀ User.id, nên không thể
 * tồn tại Driver mà không có User. Service này tạo/xóa cả cặp User+Driver như
 * một đơn vị (giống cách DataInitializer.createDriver() đang làm), vì giao diện
 * quản lý tài xế xem đây là một hồ sơ duy nhất.
 */
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;

    /**
     * Các trạng thái khiến một tài xế được coi là "còn ràng buộc công việc".
     * Dùng đúng tập trạng thái mà TripService.isDriverBusyInWindow() đã định
     * nghĩa là "bận" — không định nghĩa lại khái niệm này ở tầng khác.
     */
    private static final List<TripStatus> BUSY_STATUSES = List.of(
            TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE, TripStatus.DEPARTED);

    public List<Driver> findAllWithUser() {
        return driverRepository.findAllWithUser();
    }

    public Optional<Driver> findById(Long userId) {
        return driverRepository.findById(userId);
    }

    /**
     * Tạo mới hồ sơ tài xế: tạo User (role tự gán ROLE_DRIVER) rồi tạo Driver
     * trỏ vào User đó.
     */
    @Transactional
    public void createDriver(User user, Driver driver) {
        normalizeUser(user);
        validateUsernameAvailable(user.getUsername(), null);

        user.setRole(Role.ROLE_DRIVER);
        user.setStatus(Boolean.TRUE);
        User savedUser = userRepository.save(user);

        driver.setUser(savedUser);
        if (driver.getIsActive() == null) {
            driver.setIsActive(Boolean.TRUE);
        }
        driverRepository.save(driver);
    }

    /**
     * Cập nhật hồ sơ tài xế.
     *
     * @param rawPassword mật khẩu mới; để trống thì giữ nguyên mật khẩu cũ.
     */
    @Transactional
    public void updateDriver(Long userId, User formUser, Driver formDriver, String rawPassword) {
        Driver existing = driverRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế với ID: " + userId));

        normalizeUser(formUser);
        validateUsernameAvailable(formUser.getUsername(), userId);

        // RÀNG BUỘC: Không cho khóa tài xế đang còn chuyến dở dang.
        // Cùng nguyên tắc với BusService.saveBus() — chặn chuyển xe sang REPAIRING
        // khi xe còn được phân công cho chuyến chưa kết thúc.
        boolean isBeingDeactivated = Boolean.TRUE.equals(existing.getIsActive())
                && !Boolean.TRUE.equals(formDriver.getIsActive());
        if (isBeingDeactivated && tripRepository.existsTripForDriverWithStatusIn(userId, BUSY_STATUSES)) {
            throw new RuntimeException(
                    "Không thể khóa tài xế này vì đang được phân công cho chuyến xe chưa kết thúc "
                            + "(chờ duyệt / đang bán vé / đang trên đường). Hãy gỡ tài xế khỏi các chuyến đó trước!");
        }

        User user = existing.getUser();
        user.setUsername(formUser.getUsername());
        user.setFullName(formUser.getFullName());
        user.setEmail(formUser.getEmail());
        user.setPhone(formUser.getPhone());
        if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPassword(rawPassword);
        }

        existing.setLicenseNumber(formDriver.getLicenseNumber());
        existing.setLicenseExpiryDate(formDriver.getLicenseExpiryDate());
        existing.setExperienceYears(formDriver.getExperienceYears());
        existing.setIsActive(Boolean.TRUE.equals(formDriver.getIsActive()));

        userRepository.save(user);
        driverRepository.save(existing);
    }

    /**
     * Xóa hồ sơ tài xế.
     *
     * Chặn xóa cứng nếu tài xế đã từng tham gia bất kỳ chuyến nào — giữ nguyên
     * lịch sử vận hành, đúng nguyên tắc BusService.deleteBus() đang áp dụng cho
     * xe. Trường hợp đó Admin dùng chức năng khóa (isActive=false) thay thế.
     *
     * Xóa qua userRepository: User.driver khai báo cascade=ALL nên Hibernate tự
     * xóa bản ghi Driver kèm theo. Nếu chỉ xóa Driver, User (ROLE_DRIVER) sẽ trở
     * thành bản ghi mồ côi không còn giao diện nào quản lý được.
     */
    @Transactional
    public void deleteDriver(Long userId) {
        Driver driver = driverRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế cần xóa với ID: " + userId));

        if (tripRepository.existsAnyTripForDriver(userId)) {
            throw new RuntimeException(
                    "Không thể xóa tài xế này vì đã có dữ liệu lịch sử vận hành (từng được phân công cho chuyến xe). "
                            + "Hãy khóa hồ sơ (bỏ tick 'Đang hoạt động') thay vì xóa cứng!");
        }

        userRepository.delete(driver.getUser());
    }

    /**
     * Email khai báo @Column(unique = true): để chuỗi rỗng sẽ khiến tài xế thứ
     * hai không có email vi phạm ràng buộc unique. Chuẩn hóa rỗng -> null.
     */
    private void normalizeUser(User user) {
        if (user.getEmail() != null && user.getEmail().isBlank()) {
            user.setEmail(null);
        }
        if (user.getPhone() != null && user.getPhone().isBlank()) {
            user.setPhone(null);
        }
    }

    /**
     * @param currentUserId id của tài xế đang sửa (null khi tạo mới) — để không
     *                      tự báo trùng với chính mình.
     */
    private void validateUsernameAvailable(String username, Long currentUserId) {
        userRepository.findByUsername(username).ifPresent(found -> {
            if (!found.getId().equals(currentUserId)) {
                throw new RuntimeException("Tên đăng nhập '" + username + "' đã tồn tại, vui lòng chọn tên khác!");
            }
        });
    }
}
