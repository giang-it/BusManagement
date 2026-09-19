package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khóa hành vi của dropdown PHỤ XE ở form TẠO chuyến — regression cho lỗi #13
 * trong docs/todo/current_bugs_found.md.
 *
 * Trước bản sửa, {@code TripRestController} chỉ trả về MỘT danh sách
 * {@code drivers} lấy từ {@code getAvailableDriversForTimeRange()} — vốn có bộ
 * lọc "giờ lái + phần chia ≤ 8h" — rồi template đổ chính danh sách đó vào cả
 * dropdown Tài xế chính lẫn dropdown Phụ xe. Hậu quả: người mà
 * {@code validateStaffForTrip()} sẵn sàng chấp nhận làm phụ xe lại không hề xuất
 * hiện để Admin chọn, và đúng lúc chuyến dài (> 8h) — nơi phụ xe là ràng buộc
 * BẮT BUỘC — danh sách bị co lại mạnh nhất.
 *
 * Test này chốt cả hai chiều, vì nới lỏng sai hướng cũng là lỗi:
 * <ul>
 * <li>Phụ xe KHÔNG bị trần 8h (điều bản sửa mở ra) — nếu ai đó thêm lại bộ lọc
 * giờ lái vào {@code getAvailableAssistantsForTimeRange()}, test đỏ.</li>
 * <li>Ba ràng buộc còn lại VẪN áp (còn hoạt động / bằng lái còn hạn vào ngày
 * khởi hành / không trùng lịch) — nếu ai đó "dọn" nốt chúng, test cũng đỏ.</li>
 * </ul>
 *
 * Bằng chứng mạnh nhất nằm ở {@link #validatorAcceptsTheAssistantTheOldListHid()}:
 * nó không suy luận mà chạy thẳng validator lên đúng con người bị giấu, và kèm
 * ca đối trọng chứng minh trần 8h vẫn cắn thật ở vai TÀI XẾ CHÍNH — tức sự bất
 * đối xứng giữa hai vai là có thật trong code, không phải giả định của bản sửa.
 *
 * Chạy trên busmanagement_test (ddl-auto=create-drop, không seed), @Transactional
 * nên mọi bản ghi dựng lên đều được rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class TripServiceAssistantAvailabilityTest {

    @Autowired
    private TripService tripService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TripRepository tripRepository;

    /** 00:00 ngày mai — tương lai, để giờ nền mock (chỉ cộng cho HÔM NAY) không nhiễu. */
    private LocalDateTime ref;

    /** Khung giờ đang xét: 16:00–20:00 ngày mai, dài 4h ⇒ phần chia 4h/người. */
    private LocalDateTime departure;
    private LocalDateTime arrival;

    /** Đã lái 6h sáng cùng ngày ⇒ 6 + 4 > 8: trượt trần 8h, nhưng thừa sức làm phụ xe. */
    private Driver loaded;
    /** Sạch hoàn toàn — mốc đối chiếu dương. */
    private Driver free;
    /** Bị khóa. */
    private Driver inactive;
    /** Bằng lái hết hạn trước ngày khởi hành. */
    private Driver expired;
    /** Bận ở một chuyến GIAO với đúng khung giờ đang xét. */
    private Driver clashing;

    @BeforeEach
    void setUp() {
        ref = LocalDate.now().plusDays(1).atStartOfDay();
        departure = ref.plusHours(16);
        arrival = ref.plusHours(20);

        loaded = saveDriver("asst-loaded", "AS-LOADED", true, LocalDate.now().plusYears(1));
        free = saveDriver("asst-free", "AS-FREE", true, LocalDate.now().plusYears(1));
        inactive = saveDriver("asst-inactive", "AS-INACTIVE", false, LocalDate.now().plusYears(1));
        expired = saveDriver("asst-expired", "AS-EXPIRED", true, LocalDate.now().minusDays(1));
        clashing = saveDriver("asst-clashing", "AS-CLASH", true, LocalDate.now().plusYears(1));

        // 06:00–12:00 cùng ngày: 6 giờ lái, KHÔNG giao với khung 15:30–20:30
        // (đã tính cả đệm nghỉ ±30 phút) ⇒ chỉ đụng bộ lọc giờ, không đụng bộ lọc bận.
        saveTrip(loaded, ref.plusHours(6), ref.plusHours(12));

        // 17:00–19:00: nằm gọn trong khung đang xét ⇒ bận thật.
        saveTrip(clashing, ref.plusHours(17), ref.plusHours(19));
    }

    // =====================================================================
    // Lỗi #13 nằm ở đây
    // =====================================================================

    @Test
    @DisplayName("#13: người trượt trần 8h bị giấu khỏi danh sách TÀI XẾ, nhưng phải có mặt ở danh sách PHỤ XE")
    void loadedDriver_isHiddenFromDriverList_butOfferedAsAssistant() {
        // Neo chống "pass rỗng nghĩa": nếu fixture không lưu được thì mọi bộ lọc
        // đều rỗng và test vẫn xanh mà chẳng kiểm gì.
        assertEquals(6.0, tripService.getDrivingHoursForDate(loaded, departure, null, null), 1e-9,
                "Sanity: 'loaded' phải có đúng 6h lái trong ngày — nếu không, dữ liệu setUp hỏng");

        Set<Long> drivers = userIdsOf(tripService.getAvailableDriversForTimeRange(departure, arrival));
        Set<Long> assistants = userIdsOf(tripService.getAvailableAssistantsForTimeRange(departure, arrival));

        assertFalse(drivers.contains(loaded.getUserId()),
                "6h + 4h = 10h > 8h ⇒ không được mời làm tài xế");
        assertTrue(assistants.contains(loaded.getUserId()),
                "Phụ xe không cầm lái nên trần 8h không áp — đây chính là lỗi #13");

        // Mốc dương: người sạch phải có mặt ở CẢ HAI danh sách.
        assertTrue(drivers.contains(free.getUserId()));
        assertTrue(assistants.contains(free.getUserId()));
    }

    @Test
    @DisplayName("Bất biến: danh sách phụ xe luôn là SIÊU TẬP của danh sách tài xế")
    void assistantList_isAlwaysSupersetOfDriverList() {
        Set<Long> drivers = userIdsOf(tripService.getAvailableDriversForTimeRange(departure, arrival));
        Set<Long> assistants = userIdsOf(tripService.getAvailableAssistantsForTimeRange(departure, arrival));

        assertTrue(assistants.containsAll(drivers),
                "Hai danh sách chỉ được lệch nhau đúng bộ lọc giờ lái, nên phụ xe phải bao trùm tài xế");
        assertTrue(assistants.size() > drivers.size(),
                "Với fixture này phải lệch ít nhất một người ('loaded') — nếu bằng nhau, bộ lọc giờ đã lọt vào nhánh phụ xe");
    }

    // =====================================================================
    // Chiều ngược lại: KHÔNG được nới lỏng những ràng buộc còn lại
    // =====================================================================

    @Test
    @DisplayName("Phụ xe vẫn bị loại nếu bị khóa, hết bằng lái, hoặc trùng lịch")
    void assistantList_stillRejectsInactive_expiredLicence_andScheduleClash() {
        Set<Long> assistants = userIdsOf(tripService.getAvailableAssistantsForTimeRange(departure, arrival));

        assertFalse(assistants.contains(inactive.getUserId()), "Tài xế bị khóa không được làm phụ xe");
        assertFalse(assistants.contains(expired.getUserId()),
                "Bằng lái hết hạn vào ngày khởi hành ⇒ loại, giống hệt validateStaffForTrip()");
        assertFalse(assistants.contains(clashing.getUserId()),
                "Đang bận ở chuyến giao khung giờ ⇒ loại (phụ xe vẫn phải có mặt trên xe)");

        // Neo: bộ lọc không loại nhầm tất cả.
        assertTrue(assistants.contains(free.getUserId()));
    }

    // =====================================================================
    // Đối chiếu thẳng với validator — quy tắc một chiều của §8
    // =====================================================================

    @Test
    @DisplayName("Validator CHẤP NHẬN đúng người mà danh sách cũ đã giấu — và vẫn từ chối họ ở vai tài xế chính")
    void validatorAcceptsTheAssistantTheOldListHid() {
        Trip asAssistant = new Trip();
        asAssistant.setDriver(free);
        asAssistant.setAssistant(loaded);
        asAssistant.setDepartureTime(departure);
        asAssistant.setArrivalTimeExpected(arrival);
        asAssistant.setTotalSeats(40);

        ValidationResult ok = tripService.validateStaffForTripDryRun(asAssistant, null);
        assertTrue(ok.isValid(),
                "Validator chấp nhận 'loaded' làm phụ xe ⇒ dropdown cũ đã giấu một lựa chọn hợp lệ: "
                        + ok.getFailureReason());

        // ĐỐI TRỌNG: cùng con người, cùng khung giờ, đổi sang vai TÀI XẾ CHÍNH thì
        // trần 8h cắn thật. Đây là bằng chứng sự bất đối xứng giữa hai vai có thật
        // trong validator, chứ không phải giả định của bản sửa.
        Trip asMainDriver = new Trip();
        asMainDriver.setDriver(loaded);
        asMainDriver.setDepartureTime(departure);
        asMainDriver.setArrivalTimeExpected(arrival);
        asMainDriver.setTotalSeats(40);

        ValidationResult rejected = tripService.validateStaffForTripDryRun(asMainDriver, null);
        assertFalse(rejected.isValid(), "Cùng người đó ở vai tài xế chính phải bị trần 8h từ chối");
        assertTrue(rejected.getFailureReason().contains("8h/ngày"),
                "Phải trượt đúng vì trần giờ lái, không phải vì lý do khác: " + rejected.getFailureReason());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Set<Long> userIdsOf(List<Driver> drivers) {
        return drivers.stream().map(Driver::getUserId).collect(Collectors.toSet());
    }

    private Driver saveDriver(String username, String licenseNo, boolean active, LocalDate licenceExpiry) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber(licenseNo);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(active);
        d.setLicenseExpiryDate(licenceExpiry);
        user.setDriver(d);

        return userRepository.save(user).getDriver(); // cascade = ALL → lưu luôn Driver
    }

    private void saveTrip(Driver mainDriver, LocalDateTime dep, LocalDateTime arr) {
        Trip t = new Trip();
        t.setDriver(mainDriver);
        t.setDepartureTime(dep);
        t.setArrivalTimeExpected(arr);
        t.setStatus(TripStatus.ACTIVE);
        t.setTotalSeats(40);
        tripRepository.save(t);
    }
}
