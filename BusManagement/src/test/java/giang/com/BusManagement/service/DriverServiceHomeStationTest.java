package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.StationRepository;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bến nhà của tài xế là bắt buộc ở cả lối tạo lẫn lối sửa (QĐ-23, CM-12 trong
 * docs/development/route_pairs_decisions.md, CT5a) — anh em của các test bến nhà trong
 * BusServiceTest. Chạy trên busmanagement_test, @Transactional nên mọi bản ghi được rollback.
 */
@SpringBootTest
@Transactional
class DriverServiceHomeStationTest {

    @Autowired
    private DriverService driverService;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StationRepository stationRepository;

    @Test
    @DisplayName("Tạo tài xế có bến nhà → lưu đúng bến")
    void create_savesTheHomeStation() {
        Station home = station("Bến test CT5a");

        driverService.createDriver(user("ct5a-home-1"), driver(home));

        Driver saved = driverRepository.findAll().stream()
                .filter(d -> "ct5a-home-1".equals(d.getUser().getUsername())).findFirst().orElseThrow();
        assertEquals(home.getId(), saved.getHomeStation().getId());
    }

    @Test
    @DisplayName("Tạo tài xế thiếu bến nhà → bị từ chối TRƯỚC mọi lần ghi: không tài khoản, không hồ sơ")
    void create_rejectsMissingHomeStation_beforeWritingAnything() {
        long users = userRepository.count();
        long drivers = driverRepository.count();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> driverService.createDriver(user("ct5a-home-2"), driver(null)));

        assertTrue(ex.getMessage().contains("bến nhà"), ex.getMessage());
        assertEquals(users, userRepository.count());
        assertEquals(drivers, driverRepository.count());
    }

    @Test
    @DisplayName("Sửa tài xế mà bỏ trống bến nhà → bị từ chối, bến nhà cũ giữ nguyên")
    void update_rejectsMissingHomeStation_andKeepsTheOldOne() {
        Station home = station("Bến test CT5a");
        Driver existing = created("ct5a-home-3", home);

        assertThrows(IllegalArgumentException.class, () -> driverService.updateDriver(
                existing.getUserId(), user("ct5a-home-3"), driver(null), null));

        assertEquals(home.getId(), driverRepository.findById(existing.getUserId()).orElseThrow()
                .getHomeStation().getId());
    }

    @Test
    @DisplayName("Sửa tài xế sang bến nhà khác → lưu đúng bến mới")
    void update_changesHomeStation() {
        Driver existing = created("ct5a-home-4", station("Bến test CT5a"));
        Station other = station("Bến test CT5a khác");

        driverService.updateDriver(existing.getUserId(), user("ct5a-home-4"), driver(other), null);

        assertEquals(other.getId(), driverRepository.findById(existing.getUserId()).orElseThrow()
                .getHomeStation().getId());
    }

    // =====================================================================

    private Driver created(String username, Station home) {
        driverService.createDriver(user(username), driver(home));
        return driverRepository.findAll().stream()
                .filter(d -> username.equals(d.getUser().getUsername())).findFirst().orElseThrow();
    }

    private Station station(String name) {
        Station s = new Station();
        s.setStationName(name);
        s.setAddress("UNIT-TEST");
        return stationRepository.save(s);
    }

    /** Đúng thứ AdminDriverController dựng từ form: object rời, chưa có id. */
    private static User user(String username) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setFullName("Test " + username);
        return u;
    }

    private static Driver driver(Station home) {
        Driver d = new Driver();
        d.setLicenseNumber("DL-CT5A");
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        d.setExperienceYears(3);
        d.setIsActive(true);
        d.setHomeStation(home);
        return d;
    }
}
