package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm chứng các wrapper dry-run của Phase 3.
 *
 * Phase 3 KHÔNG có màn hình nào, nên quy ước "drive the real app" của dự án
 * (xem .claude/skills/verify) không với tới được hai method này — test tích hợp
 * là bằng chứng thực thi duy nhất. Việc drive app vẫn được thực hiện riêng để
 * chứng minh các luồng throw cũ không đổi.
 *
 * Điều cần chứng minh, và cũng là cam kết của Phase 3: với CÙNG một input,
 * bản dry-run trả về đúng thông điệp mà luồng throw ném ra — không diễn giải
 * lại, không nuốt lỗi kỹ thuật.
 *
 * Chạy trên database busmanagement_test (Phase 0), @Transactional nên mọi bản
 * ghi dựng lên đều được rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class TripServiceValidationDryRunTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusRepository busRepository;

    private Bus bus;
    private Driver driver;
    private LocalDateTime departure;

    @BeforeEach
    void setUp() {
        // Xe sạch: READY, vừa bảo trì xong (kmSinceLastMaintenance = 0)
        bus = new Bus();
        bus.setLicensePlate("DRYRUN-01");
        bus.setBrand("TestBrand");
        bus.setOdometer(1000.0);
        bus.setLastMaintenanceOdometer(1000.0);
        bus.setMaintenanceThreshold(10000.0);
        bus.setStatus(BusStatus.READY);
        bus = busRepository.save(bus);

        // Tài xế sạch: đang hoạt động, bằng lái còn hạn, chưa có giờ lái nền
        User user = new User();
        user.setUsername("dryrun-driver");
        user.setFullName("Tài Xế Dry Run");
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNumber("DR-0001");
        driver.setExperienceYears(5);
        driver.setTotalDrivingHours24h(0.0);
        driver.setIsActive(true);
        driver.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(driver);

        user = userRepository.save(user); // cascade = ALL → lưu luôn Driver
        driver = user.getDriver();

        // Ngày mai, để totalDrivingHours24h (chỉ cộng cho ngày hôm nay) không nhiễu
        departure = LocalDateTime.now().plusDays(1).withHour(8)
                .withMinute(0).withSecond(0).withNano(0);
    }

    /** Chuyến 4h hợp lệ, không gán route (validator xử lý route null → 0 km). */
    private Trip validTrip() {
        Trip trip = new Trip();
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(departure.plusHours(4));
        trip.setTotalSeats(40);
        return trip;
    }

    // =====================================================================
    // Đường đi hợp lệ
    // =====================================================================

    @Test
    void busDryRun_cleanBus_returnsPassWithNoWarning() {
        ValidationResult result = tripService.validateBusForTripDryRun(bus, validTrip(), null);

        assertTrue(result.isValid(), "Xe sạch phải hợp lệ");
        assertNull(result.getFailureReason());
        // Kênh warning của validateBusForTrip() hiện luôn null — giữ nguyên, không tự sinh
        assertNull(result.getWarning());
    }

    @Test
    void staffDryRun_cleanDriver_returnsPass() {
        ValidationResult result = tripService.validateStaffForTripDryRun(validTrip(), null);

        assertTrue(result.isValid(), "Tài xế sạch phải hợp lệ");
        assertNull(result.getFailureReason());
    }

    // =====================================================================
    // Vi phạm nghiệp vụ → fail, và fail ĐÚNG BẰNG thông điệp của luồng throw
    // =====================================================================

    @Test
    void busDryRun_repairingBus_failsWithSameMessageAsThrowingPath() {
        bus.setStatus(BusStatus.REPAIRING);
        Trip trip = validTrip();

        ValidationResult result = tripService.validateBusForTripDryRun(bus, trip, null);

        assertFalse(result.isValid());
        assertNotNull(result.getFailureReason());

        // Cùng input → luồng throw phải ném ra ĐÚNG thông điệp đó
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> tripService.createManualTrip(validTrip()));
        assertEquals(thrown.getMessage(), result.getFailureReason(),
                "Dry-run phải phản chiếu nguyên văn thông điệp của validator gốc");
    }

    @Test
    void staffDryRun_missingPrimaryDriver_failsWithSameMessageAsThrowingPath() {
        Trip trip = validTrip();
        trip.setDriver(null);

        ValidationResult result = tripService.validateStaffForTripDryRun(trip, null);

        assertFalse(result.isValid());
        assertEquals("Chuyến xe bắt buộc phải có tài xế chính!", result.getFailureReason());

        Trip sameInput = validTrip();
        sameInput.setDriver(null);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> tripService.createManualTrip(sameInput));
        assertEquals(thrown.getMessage(), result.getFailureReason());
    }

    /**
     * Fail-fast được giữ nguyên: chuyến vi phạm NHIỀU ràng buộc cùng lúc vẫn chỉ
     * báo về ràng buộc ĐẦU TIÊN. Đây là hành vi đã chốt của Phase 3, không phải
     * thiếu sót — test này khóa nó lại để không ai "sửa" thành gom nhiều lỗi mà
     * không đọc roadmap.
     */
    @Test
    void staffDryRun_multipleViolations_reportsOnlyTheFirst() {
        Trip trip = validTrip();
        trip.setDriver(null); // vi phạm 1: thiếu tài xế chính
        trip.setArrivalTimeExpected(departure.plusHours(10)); // vi phạm 2: >8h mà không có phụ xe

        ValidationResult result = tripService.validateStaffForTripDryRun(trip, null);

        assertFalse(result.isValid());
        assertEquals("Chuyến xe bắt buộc phải có tài xế chính!", result.getFailureReason(),
                "Chỉ ràng buộc đầu tiên được báo — validator gốc throw ngay, không đi tiếp");
    }

    // =====================================================================
    // Lỗi kỹ thuật KHÔNG bị nuốt thành lý do nghiệp vụ
    // =====================================================================

    /**
     * Wrapper chỉ bắt IllegalArgumentException. bus == null khiến validator gốc
     * ném NullPointerException (nó dereference bus.getStatus() ngay dòng đầu) —
     * lỗi này phải thoát ra ngoài, vì biến một bug thành "không hợp lệ" sẽ khiến
     * Decision Support ở Phase 7 âm thầm loại một xe hợp lệ mà không ai biết.
     */
    @Test
    void busDryRun_nullBus_propagatesNpeInsteadOfFailing() {
        Trip trip = validTrip();
        trip.setBus(null);

        assertThrows(NullPointerException.class,
                () -> tripService.validateBusForTripDryRun(null, trip, null));
    }
}
