package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.TripRepository;
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

    @Autowired
    private TripRepository tripRepository;

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

    /** Phụ xe (cũng là Driver) với ngày hết hạn bằng lái cho trước. */
    private Driver assistantWithLicenseExpiry(LocalDate expiry) {
        User user = new User();
        user.setUsername("dryrun-assistant-" + expiry);
        user.setFullName("Phụ Xe Dry Run");
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver assistant = new Driver();
        assistant.setUser(user);
        assistant.setLicenseNumber("AS-" + expiry);
        assistant.setExperienceYears(3);
        assistant.setTotalDrivingHours24h(0.0);
        assistant.setIsActive(true);
        assistant.setLicenseExpiryDate(expiry);
        user.setDriver(assistant);

        user = userRepository.save(user); // cascade = ALL → lưu luôn Driver
        return user.getDriver();
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
    // Lỗi #19 — cờ TRAVELING chỉ chặn khi KHÔNG có chuyến nào giải thích nó
    //
    // Bản cũ hỏi `excludeTripId.equals(trip.getId())` dưới cái tên
    // "travelingForThisTrip", nên guard sống ở createManualTrip (excludeTripId
    // null) và CHẾT ở ba lối còn lại: cùng một chiếc xe, /trips/create từ chối
    // còn /trips/approve nhận. Bốn test dưới chốt cả ba ca của luật mới, và ca
    // cuối chốt thẳng vào chính sự bất nhất đó.
    // =====================================================================

    /** Lưu một chuyến DEPARTED cho `bus` trong cửa sổ chỉ định. */
    private void giveBusARunningTrip(LocalDateTime dep, LocalDateTime arr) {
        Trip running = new Trip();
        running.setBus(bus);
        running.setDriver(driver);
        running.setDepartureTime(dep);
        running.setArrivalTimeExpected(arr);
        running.setTotalSeats(40);
        running.setStatus(TripStatus.DEPARTED);
        tripRepository.save(running);
    }

    /**
     * Ca dữ liệu LỆCH: xe mang cờ TRAVELING mà không chuyến nào đang chạy. Đúng
     * tình trạng của xe seed 51B-DAG.CH (TC_FSM_007) và của bất kỳ xe nào bị đặt
     * tay sang TRAVELING. Fail-closed: không xếp lịch trên trạng thái không nhất
     * quán.
     */
    @Test
    void busDryRun_travelingBusWithNoRunningTrip_fails() {
        bus.setStatus(BusStatus.TRAVELING);
        busRepository.save(bus);

        ValidationResult result = tripService.validateBusForTripDryRun(bus, validTrip(), null);

        assertFalse(result.isValid(), "Cờ TRAVELING không có chuyến nào giải thích thì phải bị chặn");
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("không nhất quán"),
                "Thông điệp phải nói đúng bệnh (trạng thái lệch), nhận được: " + result.getFailureReason());
    }

    /**
     * Ca mà phương án "siết" sẽ chặn nhầm: xe đang chạy một chuyến có thật, và
     * chuyến mới nằm ở cửa sổ KHÔNG giao nhau. Đây chính là hình dạng của hai
     * chuyến thật (8 và 14) đo được ngày 2026-08-12.
     */
    @Test
    void busDryRun_travelingBusExplainedByRunningTrip_nonOverlappingWindow_returnsPass() {
        bus.setStatus(BusStatus.TRAVELING);
        busRepository.save(bus);
        // Chuyến đang chạy nằm ở QUÁ KHỨ xa so với cửa sổ của validTrip() (ngày mai).
        giveBusARunningTrip(departure.minusDays(3), departure.minusDays(3).plusHours(4));

        ValidationResult result = tripService.validateBusForTripDryRun(bus, validTrip(), null);

        assertTrue(result.isValid(),
                "Xe đang chạy một chuyến khác, cửa sổ không giao nhau ⇒ vẫn xếp lịch được. Lý do: "
                        + result.getFailureReason());
    }

    /**
     * ĐỐI TRỌNG của ca trên: cùng tình huống nhưng cửa sổ GIAO nhau thì vẫn phải
     * bị chặn — bằng luật cửa sổ (isBusBusy), không phải bằng cái cờ. Thiếu ca
     * này thì một bản sửa "bỏ hẳn nhánh TRAVELING" cũng qua được ca trên.
     */
    @Test
    void busDryRun_travelingBusExplainedByRunningTrip_overlappingWindow_fails() {
        bus.setStatus(BusStatus.TRAVELING);
        busRepository.save(bus);
        // Chuyến đang chạy TRÙNG cửa sổ với validTrip().
        giveBusARunningTrip(departure, departure.plusHours(4));

        ValidationResult result = tripService.validateBusForTripDryRun(bus, validTrip(), null);

        assertFalse(result.isValid(), "Cửa sổ giao nhau thì phải bị chặn");
        assertTrue(result.getFailureReason().contains("đang bận"),
                "Phải bị chặn bởi luật cửa sổ thời gian, nhận được: " + result.getFailureReason());
    }

    /**
     * CHỐT THẲNG VÀO LỖI #19: cùng một chiếc xe, cùng một cửa sổ, câu trả lời
     * phải GIỐNG NHAU dù đang tạo chuyến mới (excludeTripId = null, lối
     * createManualTrip) hay đang thao tác trên một chuyến đã có (excludeTripId =
     * id chuyến, lối updateManualTrip/approveTrip/confirmAutoAssignedTrip).
     *
     * Trước bản sửa, hai vế này KHÁC nhau: vế đầu từ chối, vế sau nhận.
     */
    @Test
    void busDryRun_travelingBus_sameAnswerWhetherNewAssignmentOrExistingTrip() {
        bus.setStatus(BusStatus.TRAVELING);
        busRepository.save(bus);

        Trip persisted = validTrip();
        persisted.setStatus(TripStatus.PENDING_APPROVAL);
        persisted = tripRepository.save(persisted);

        ValidationResult asNewAssignment = tripService.validateBusForTripDryRun(bus, validTrip(), null);
        ValidationResult asExistingTrip = tripService.validateBusForTripDryRun(bus, persisted, persisted.getId());

        assertEquals(asNewAssignment.isValid(), asExistingTrip.isValid(),
                "Cùng xe, cùng cửa sổ ⇒ tạo mới và sửa chuyến đã có phải cho cùng một câu trả lời");
        assertFalse(asNewAssignment.isValid(), "Và ở đây câu trả lời chung phải là TỪ CHỐI (cờ không được giải thích)");
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
    // Phụ xe (assistant) cũng phải còn bằng lái vào NGÀY KHỞI HÀNH (Decision A)
    // =====================================================================

    /**
     * Phụ xe cũng là Driver, nên cũng phải còn bằng lái vào ngày khởi hành — cùng
     * quy tắc như tài xế chính/phụ. Trước Decision A, validateStaffForTrip chỉ
     * kiểm isActive + trùng lịch cho phụ xe, nên một phụ xe hết hạn bằng lái lọt
     * qua tầng gate ở server (dù dropdown và AI đều đã lọc). Test khóa lỗ hổng đó.
     */
    @Test
    void staffDryRun_expiredAssistantLicense_failsWithSameMessageAsThrowingPath() {
        Driver assistant = assistantWithLicenseExpiry(departure.toLocalDate().minusDays(1));
        Trip trip = validTrip();
        trip.setAssistant(assistant);

        ValidationResult result = tripService.validateStaffForTripDryRun(trip, null);

        assertFalse(result.isValid(), "Phụ xe hết hạn bằng lái phải bị loại");
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().startsWith("Bằng lái của phụ xe"),
                "Phải là lý do về bằng lái phụ xe, không phải ràng buộc khác");

        // Cùng input → luồng throw (createManualTrip) phải ném ra ĐÚNG thông điệp đó
        Trip sameInput = validTrip();
        sameInput.setAssistant(assistant);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> tripService.createManualTrip(sameInput));
        assertEquals(thrown.getMessage(), result.getFailureReason(),
                "Dry-run phải phản chiếu nguyên văn thông điệp của validator gốc");
    }

    /** Không over-eager: phụ xe còn hạn bằng lái vào ngày khởi hành vẫn hợp lệ. */
    @Test
    void staffDryRun_validAssistantLicense_returnsPass() {
        Driver assistant = assistantWithLicenseExpiry(departure.toLocalDate().plusYears(1));
        Trip trip = validTrip();
        trip.setAssistant(assistant);

        ValidationResult result = tripService.validateStaffForTripDryRun(trip, null);

        assertTrue(result.isValid(), "Phụ xe còn hạn bằng lái phải hợp lệ");
        assertNull(result.getFailureReason());
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
