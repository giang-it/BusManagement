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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE 7 (bước 2) — Test TƯƠNG ĐƯƠNG giữa nhánh đánh giá in-memory (dùng
 * AvailabilityContext + predicate Java) và nhánh SQL gốc, ở các trường hợp BIÊN.
 *
 * Recommendation Engine chọn tài nguyên trên dữ liệu preload thay vì truy vấn DB
 * từng xe/tài xế. Rủi ro của cách này là predicate "giao khoảng thời gian / vai
 * trò / loại trừ" viết bằng Java có thể lệch khỏi điều kiện SQL. Test này khóa
 * sự tương đương: với CÙNG một tập chuyến và CÙNG tham số, hai nhánh
 * (ctx == null → SQL, ctx != null → Java) phải trả về ĐÚNG cùng kết quả — nếu ai
 * đó sửa một bên mà quên bên kia, test vỡ.
 *
 * Đây là cam kết mà chủ dự án yêu cầu khi duyệt phương án "an toàn" (giữ đường
 * phân công cũ trên SQL, chỉ thêm nhánh in-memory cho Recommendation): predicate
 * Java được chấp nhận vì là phép toán nền tảng, với điều kiện có test đối chiếu
 * biên đảm bảo hai cách đánh giá luôn khớp.
 *
 * Chạy trên busmanagement_test, @Transactional nên mọi bản ghi được rollback.
 */
@SpringBootTest
@Transactional
class TripServiceAvailabilityContextTest {

    @Autowired
    private TripService tripService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusRepository busRepository;
    @Autowired
    private TripRepository tripRepository;
    @PersistenceContext
    private EntityManager em;

    private Driver driver;
    private Bus bus;
    private AvailabilityContext ctx;

    /** Mốc gốc: 00:00 của ngày mai (tương lai, tránh nhiễu giờ nền mock của "hôm nay"). */
    private LocalDateTime ref;

    private Long tDriverId; // chuyến D1 lái chính, B1, [ref+10h, ref+12h], ACTIVE

    @BeforeEach
    void setUp() {
        ref = LocalDate.now().plusDays(1).atStartOfDay();

        bus = new Bus();
        bus.setLicensePlate("AVAIL-B1");
        bus.setBrand("TestBrand");
        bus.setOdometer(1000.0);
        bus.setLastMaintenanceOdometer(1000.0);
        bus.setMaintenanceThreshold(10000.0);
        bus.setStatus(BusStatus.READY);
        bus = busRepository.save(bus);

        driver = saveDriver("avail-d1", "AV-D1");
        Driver other = saveDriver("avail-d2", "AV-D2"); // tài xế chính "mồi" cho các vai phụ của D1

        // Các chuyến dàn dựng đủ vai trò / trạng thái / biên
        Trip tDriver = save(trip(driver, null, null, bus, ref.plusHours(10), ref.plusHours(12), TripStatus.ACTIVE));
        tDriverId = tDriver.getId();
        save(trip(driver, null, null, bus, ref.plusHours(10), ref.plusHours(12), TripStatus.CANCELLED)); // phải bị bỏ qua
        Trip coTrip = save(trip(other, null, null, null,
                ref.plusDays(1).plusHours(10), ref.plusDays(1).plusHours(12), TripStatus.PENDING_APPROVAL));
        save(trip(other, null, driver, null,
                ref.plusDays(2).plusHours(10), ref.plusDays(2).plusHours(12), TripStatus.DEPARTED)); // D1 phụ xe
        save(trip(driver, null, null, null,
                ref.plusDays(3).plusHours(10), null, TripStatus.ACTIVE)); // thiếu giờ đến (null arrival)
        save(trip(driver, null, null, bus,
                ref.minusDays(5).plusHours(10), ref.minusDays(5).plusHours(12), TripStatus.COMPLETED)); // quá khứ

        tripRepository.flush(); // đẩy các chuyến xuống DB trước khi chèn hàng bảng nối

        // Gắn D1 làm TÀI XẾ PHỤ (co-driver) của coTrip bằng SQL trực tiếp vào bảng
        // nối, KHÔNG qua @ManyToMany của ORM. Chèn thẳng để cả hai nhánh đều thấy
        // quan hệ này: SQL existsOverlappingTripForDriver JOIN coDrivers, và nhánh
        // preload lazy-load coDrivers khi duyệt vai trò.
        //
        // LƯU Ý (cập nhật 2026-07-24): ban đầu cách này là BẮT BUỘC, vì flush một
        // collection coDrivers qua ORM sẽ chạm hashCode đệ quy do @Data sinh ra
        // (Driver.hashCode ↔ User.hashCode). Bug đó ĐÃ ĐƯỢC SỬA (Hidden Cost #9 —
        // xem EntityEqualsHashCodeCycleTest), nên đường ORM giờ cũng chạy được.
        // Vẫn giữ nguyên cách chèn thẳng: test này đang xanh và mục tiêu của nó là
        // đối chiếu predicate Java ≡ SQL, không phải trình diễn cách lưu co-driver.
        em.createNativeQuery("INSERT INTO trip_co_drivers (trip_id, user_id) VALUES (?, ?)")
                .setParameter(1, coTrip.getId())
                .setParameter(2, driver.getUserId())
                .executeUpdate();

        // Xóa persistence context: coTrip đang được cache với collection coDrivers
        // rỗng đã khởi tạo lúc save, sẽ KHÔNG phản ánh hàng vừa chèn thẳng. Clear để
        // buildAvailabilityContext (và cả nhánh SQL) nạp TƯƠI từ DB. driver/bus sau
        // đó ở trạng thái detached nhưng vẫn dùng được làm tham số đọc (Hibernate
        // dùng id) và cho getUserId()/getId().
        em.flush();
        em.clear();

        // Dựng context với span ĐỦ RỘNG phủ mọi chuyến — test này kiểm chứng
        // predicate, không kiểm chứng việc thu hẹp span (đó là chuyện của query).
        ctx = tripService.buildAvailabilityContext(ref.minusDays(10), ref.plusDays(10));
    }

    // =====================================================================
    // BẬN — XE
    // =====================================================================

    @Test
    void busBusy_boundaryAndRoleCases_matchSql() {
        // Chạm đúng biên arrival → KHÔNG trùng
        assertBusEquivalent("touch-at-arrival", ref.plusHours(12), ref.plusHours(14), null);
        // Chạm đúng biên departure → KHÔNG trùng
        assertBusEquivalent("touch-at-departure", ref.plusHours(8), ref.plusHours(10), null);
        // Nhích 1 phút qua biên → trùng
        assertBusEquivalent("overlap-by-1min-end", ref.plusHours(11).plusMinutes(59), ref.plusHours(14), null);
        assertBusEquivalent("overlap-by-1min-start", ref.plusHours(8), ref.plusHours(10).plusMinutes(1), null);
        // Bao trọn / giao một phần
        assertBusEquivalent("fully-containing", ref.plusHours(9), ref.plusHours(13), null);
        // Rời hẳn
        assertBusEquivalent("disjoint", ref.plusDays(8), ref.plusDays(8).plusHours(2), null);
        // Loại trừ chính chuyến đang trùng → hết bận
        assertBusEquivalent("exclude-the-overlapping-trip", ref.plusHours(11), ref.plusHours(11).plusMinutes(30),
                tDriverId);
        // Chuyến COMPLETED quá khứ không tính là bận
        assertBusEquivalent("completed-not-busy", ref.minusDays(5).plusHours(11), ref.minusDays(5).plusHours(13),
                null);
        // Neo chống "pass rỗng nghĩa": nếu setUp không lưu được dữ liệu thì mọi ca
        // đều false==false và test vẫn xanh mà chẳng kiểm gì. Khẳng định ca dương
        // thật sự dương để đảm bảo test đang thực sự vận hành trên dữ liệu.
        assertTrue(tripService.isBusBusy(bus, ref.plusHours(11), ref.plusHours(13), null, null),
                "Sanity: xe B1 phải BẬN ở khung trùng chuyến ACTIVE — nếu không, dữ liệu setUp hỏng");
    }

    // =====================================================================
    // BẬN — TÀI XẾ (mọi vai trò: chính / phụ / phụ xe)
    // =====================================================================

    @Test
    void driverBusy_allRolesAndBoundaries_matchSql() {
        // Vai trò tài xế chính
        assertDriverEquivalent("main-touch-arrival", ref.plusHours(12), ref.plusHours(14), null);
        assertDriverEquivalent("main-overlap", ref.plusHours(11), ref.plusHours(13), null);
        // Vai trò tài xế phụ (co-driver)
        assertDriverEquivalent("codriver-overlap",
                ref.plusDays(1).plusHours(11), ref.plusDays(1).plusHours(13), null);
        // Vai trò phụ xe (assistant)
        assertDriverEquivalent("assistant-overlap",
                ref.plusDays(2).plusHours(11), ref.plusDays(2).plusHours(13), null);
        // Chuyến thiếu giờ đến (null arrival) → không bao giờ tính là trùng (mirror SQL)
        assertDriverEquivalent("null-arrival-never-busy",
                ref.plusDays(3).plusHours(9), ref.plusDays(3).plusHours(15), null);
        // Loại trừ chính chuyến đang trùng → hết bận
        assertDriverEquivalent("exclude-the-overlapping-trip",
                ref.plusHours(11), ref.plusHours(11).plusMinutes(30), tDriverId);
        // Chuyến bị hủy không gây bận
        assertDriverEquivalent("cancelled-not-busy", ref.plusHours(11), ref.plusHours(11).plusMinutes(30),
                tDriverId); // loại chuyến ACTIVE → chỉ còn chuyến CANCELLED cùng khung, phải ra false cả hai
        // Rời hẳn
        assertDriverEquivalent("disjoint", ref.plusDays(8), ref.plusDays(8).plusHours(2), null);
        // Neo chống "pass rỗng nghĩa" (xem giải thích ở busBusy).
        assertTrue(tripService.isDriverBusyInWindow(driver, ref.plusHours(11), ref.plusHours(13), null, null),
                "Sanity: D1 phải BẬN ở khung trùng chuyến — bảo vệ test khỏi pass rỗng nghĩa");
    }

    // =====================================================================
    // GIỜ LÁI/NGÀY
    // =====================================================================

    @Test
    void drivingHours_variousDaysAndRoles_matchSql() {
        assertHoursEquivalent("main-driver-day", ref, null);
        assertHoursEquivalent("codriver-day-shared", ref.plusDays(1), null);
        assertHoursEquivalent("assistant-day-counts-zero", ref.plusDays(2), null);
        assertHoursEquivalent("null-arrival-day-uses-fallback", ref.plusDays(3), null);
        assertHoursEquivalent("completed-past-day", ref.minusDays(5), null);
        assertHoursEquivalent("exclude-trip-on-main-day", ref, tDriverId);
        assertHoursEquivalent("empty-day", ref.plusDays(6), null);
        // Neo chống "pass rỗng nghĩa": ngày ref có chuyến D1 lái chính nên giờ > 0.
        assertTrue(tripService.getDrivingHoursForDate(driver, ref, null, null) > 0,
                "Sanity: D1 phải có giờ lái > 0 ở ngày ref — bảo vệ test khỏi pass rỗng nghĩa");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void assertBusEquivalent(String label, LocalDateTime ws, LocalDateTime we, Long excludeId) {
        boolean sql = tripService.isBusBusy(bus, ws, we, excludeId, null);
        boolean mem = tripService.isBusBusy(bus, ws, we, excludeId, ctx);
        assertEquals(sql, mem, "isBusBusy lệch ở ca [" + label + "] (SQL=" + sql + ", ctx=" + mem + ")");
    }

    private void assertDriverEquivalent(String label, LocalDateTime ws, LocalDateTime we, Long excludeId) {
        boolean sql = tripService.isDriverBusyInWindow(driver, ws, we, excludeId, null);
        boolean mem = tripService.isDriverBusyInWindow(driver, ws, we, excludeId, ctx);
        assertEquals(sql, mem, "isDriverBusyInWindow lệch ở ca [" + label + "] (SQL=" + sql + ", ctx=" + mem + ")");
    }

    private void assertHoursEquivalent(String label, LocalDateTime date, Long excludeId) {
        double sql = tripService.getDrivingHoursForDate(driver, date, excludeId, null);
        double mem = tripService.getDrivingHoursForDate(driver, date, excludeId, ctx);
        assertEquals(sql, mem, 1e-9, "getDrivingHoursForDate lệch ở ca [" + label + "] (SQL=" + sql + ", ctx=" + mem + ")");
    }

    private Driver saveDriver(String username, String licenseNo) {
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
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);

        return userRepository.save(user).getDriver();
    }

    private Trip trip(Driver mainDriver, List<Driver> coDrivers, Driver assistant, Bus onBus,
            LocalDateTime dep, LocalDateTime arr, TripStatus status) {
        Trip t = new Trip();
        t.setDriver(mainDriver);
        if (coDrivers != null) {
            t.getCoDrivers().addAll(coDrivers);
        }
        t.setAssistant(assistant);
        t.setBus(onBus);
        t.setDepartureTime(dep);
        t.setArrivalTimeExpected(arr);
        t.setStatus(status);
        t.setTotalSeats(40);
        return t;
    }

    private Trip save(Trip t) {
        return tripRepository.save(t);
    }
}
