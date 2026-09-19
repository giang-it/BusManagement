package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lỗi #25 — màn tạo tài khoản cũ: trùng username từng rơi thẳng xuống DB (HTTP 500),
 * và ROLE_DRIVER từng tạo được user tài xế không có hồ sơ Driver.
 *
 * Luật "username duy nhất" nay nằm MỘT chỗ ở AdminService.requireUsernameAvailable()
 * và DriverService uỷ quyền sang — test cuối chốt việc uỷ quyền đó, để không ai vô
 * tình dựng lại bản riêng.
 */
@SpringBootTest
@Transactional
class AdminServiceTest {

    @Autowired private AdminService adminService;
    @Autowired private DriverService driverService;
    @Autowired private UserRepository userRepository;

    @Test
    void createNewUser_savesAnAdminOrUserAccount() {
        User u = user("ops-admin", Role.ROLE_ADMIN);
        adminService.createNewUser(u);
        assertTrue(userRepository.findByUsername("ops-admin").isPresent());
    }

    @Test
    void createNewUser_refusesADuplicateUsername_beforeTheDatabaseDoes() {
        adminService.createNewUser(user("taken", Role.ROLE_USER));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adminService.createNewUser(user("taken", Role.ROLE_USER)));
        assertTrue(ex.getMessage().contains("đã tồn tại"), ex.getMessage());
        assertEquals(1, userRepository.findAll().stream().filter(x -> "taken".equals(x.getUsername())).count());
    }

    @Test
    void createNewUser_refusesABlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> adminService.createNewUser(user("  ", Role.ROLE_USER)));
    }

    @Test
    void createNewUser_refusesRoleDriver_becauseADriverNeedsAProfile() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adminService.createNewUser(user("lonely-driver", Role.ROLE_DRIVER)));
        assertTrue(ex.getMessage().contains("/admin/drivers/create"), ex.getMessage());
        assertTrue(userRepository.findByUsername("lonely-driver").isEmpty(), "không được để lại user mồ côi");
    }

    @Test
    void requireUsernameAvailable_allowsAnAccountToKeepItsOwnName() {
        adminService.createNewUser(user("keep-me", Role.ROLE_USER));
        Long id = userRepository.findByUsername("keep-me").orElseThrow().getId();

        assertDoesNotThrow(() -> adminService.requireUsernameAvailable("keep-me", id));
        assertThrows(IllegalArgumentException.class, () -> adminService.requireUsernameAvailable("keep-me", id + 1));
    }

    @Test
    void driverService_delegatesTheUsernameRule_soBothDoorsAgree() {
        adminService.createNewUser(user("shared-name", Role.ROLE_USER));

        User u = user("shared-name", Role.ROLE_DRIVER);
        Driver d = new Driver();
        d.setLicenseNumber("DL-1");
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        d.setIsActive(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> driverService.createDriver(u, d));
        assertTrue(ex.getMessage().contains("đã tồn tại"), ex.getMessage());
    }

    private User user(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setFullName("Test " + username);
        u.setRole(role);
        return u;
    }
}
