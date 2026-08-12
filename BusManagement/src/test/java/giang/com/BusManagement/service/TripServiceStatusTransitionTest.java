package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chốt hợp đồng tác dụng phụ của {@link TripService#updateTripStatus} —
 * regression cho lỗi #6 trong docs/todo/current_bugs_found.md.
 *
 * Hợp đồng nằm ở docs/architecture/trip_lifecycle_fsm.md: mọi tác dụng phụ được
 * ghi là "Side effects on ENTRY" (khi VÀO trạng thái), bảng Allowed Transitions
 * liệt kê "Same state set again" là hợp lệ nhưng đích là *(same)*, và state
 * diagram không có self-loop. Nghĩa là set lại đúng trạng thái đang có phải
 * KHÔNG ném lỗi và cũng KHÔNG chạy lại tác dụng phụ.
 *
 * Trước khi sửa, khối đồng bộ Bus chỉ xét newStatus nên gọi lại với COMPLETED
 * cộng route.distanceKm vào Bus.odometer thêm một lần nữa — tái hiện được chỉ
 * bằng double-click nút "Hoàn thành" ở bảng điều hành. Test này ở đây vì lỗi đó
 * hoàn toàn im lặng: hệ thống vẫn báo "cập nhật thành công", chỉ có số km sai,
 * và cái sai đó chảy tiếp vào kmSinceLastMaintenance → bộ lọc bảo trì của
 * findBestAvailableBus() → màn Đề Xuất Thay Xe.
 *
 * Hai test đi thành cặp có chủ đích: một cái chặn lỗi cộng lặp, một cái chứng
 * minh luồng DEPARTED→COMPLETED hợp lệ VẪN cộng đúng một lần — để việc sửa không
 * bị "quá tay" thành ra chẳng bao giờ cộng km.
 *
 * Chạy trên database busmanagement_test (Phase 0), @Transactional nên mọi bản
 * ghi dựng lên đều được rollback sau mỗi test.
 *
 * ---------------------------------------------------------------------------
 * NHÓM THỨ HAI (lỗi #20) — AI ĐƯỢC PHÉP VÀO ACTIVE.
 *
 * Cùng chủ đề "hợp lệ hoá transition" nên ở chung class, không dựng class song
 * song. Nhóm trên hỏi *"vào một trạng thái thì chạy tác dụng phụ nào"*; nhóm này
 * hỏi *"ai được vào ACTIVE"*.
 *
 * approveTrip()/confirmAutoAssignedTrip() kết thúc bằng changeStatusToActive(),
 * tức thực hiện transition X → ACTIVE trên một bản ghi ĐÃ TỒN TẠI, nhưng không
 * đi qua canTransition(). Whitelist chỉ cho PENDING_APPROVAL → ACTIVE, nên
 * trước bản sửa, một POST tự chế duyệt được cả chuyến CANCELLED/COMPLETED/
 * DEPARTED và app báo "thành công" — đã tái hiện thật trên chuyến 2749.
 *
 * Mỗi ca từ chối đi kèm một khẳng định rằng DB KHÔNG đổi, vì thiệt hại thật của
 * lỗi này không nằm ở việc ném hay không ném: nó nằm ở chỗ con trỏ xe bị dời
 * (họ lỗi #14) và ở chỗ một chuyến COMPLETED sống lại rồi được hoàn thành lần
 * hai, cộng odometer lần hai (họ lỗi #6).
 */
@SpringBootTest
@Transactional
class TripServiceStatusTransitionTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    private static final double DISTANCE_KM = 120.0;
    private static final double START_ODOMETER = 1000.0;

    private Bus bus;
    private Trip trip;

    /** Xe + tài xế sạch, chỉ dùng cho nhóm test #20. */
    private Bus approvalBus;
    private Driver approvalDriver;

    @BeforeEach
    void setUp() {
        // Xe đang trên đường, vừa bảo trì xong (kmSinceLastMaintenance = 0) và
        // ngưỡng rất xa, để phép cộng km không kích hoạt ràng buộc bảo trì nào.
        bus = new Bus();
        bus.setLicensePlate("FSM-ODO-01");
        bus.setBrand("TestBrand");
        bus.setOdometer(START_ODOMETER);
        bus.setLastMaintenanceOdometer(START_ODOMETER);
        bus.setMaintenanceThreshold(10000.0);
        bus.setStatus(BusStatus.TRAVELING);
        bus = busRepository.save(bus);

        Route route = new Route();
        route.setDistanceKm(DISTANCE_KM);
        route.setEstimatedDuration(120);
        route = routeRepository.save(route);

        // Chuyến đã khởi hành, chờ được đóng. Seed trạng thái trực tiếp qua
        // repository (không qua FSM) — cùng cách DataInitializer và
        // HistoricalDataBackfill dựng dữ liệu.
        trip = new Trip();
        trip.setRoute(route);
        trip.setBus(bus);
        trip.setDepartureTime(LocalDateTime.now().minusHours(3));
        trip.setArrivalTimeExpected(LocalDateTime.now().minusHours(1));
        trip.setTotalSeats(40);
        trip.setStatus(TripStatus.DEPARTED);
        trip = tripRepository.save(trip);
    }

    private double odometer() {
        return busRepository.findById(bus.getId()).orElseThrow().getOdometer();
    }

    private TripStatus statusInDb() {
        return tripRepository.findById(trip.getId()).orElseThrow().getStatus();
    }

    /**
     * Luồng hợp lệ: vào COMPLETED lần đầu phải cộng đúng quãng đường tuyến một
     * lần, đưa xe về READY. Đây là hành vi mà functional spec và FSM doc mô tả —
     * test này tồn tại để bản sửa không vô tình giết luôn nó.
     */
    @Test
    void departedToCompleted_addsRouteDistanceExactlyOnce() {
        tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);

        assertEquals(START_ODOMETER + DISTANCE_KM, odometer(), 0.0001);
        assertEquals(BusStatus.READY, busRepository.findById(bus.getId()).orElseThrow().getStatus());
        assertEquals(TripStatus.COMPLETED, statusInDb());
    }

    /**
     * Regression #6: hoàn thành lại một chuyến ĐÃ hoàn thành không được cộng km
     * thêm lần nào nữa, bất kể gọi bao nhiêu lần.
     */
    @Test
    void completingAnAlreadyCompletedTrip_doesNotAddOdometerAgain() {
        tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);
        double afterFirstCompletion = odometer();
        assertEquals(START_ODOMETER + DISTANCE_KM, afterFirstCompletion, 0.0001);

        // "Click thứ hai" và thứ ba trên nút Hoàn thành.
        tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);
        tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);

        assertEquals(afterFirstCompletion, odometer(), 0.0001);
        // Và kmSinceLastMaintenance — con số thật sự điều khiển ràng buộc bảo trì —
        // vẫn đúng bằng một lần quãng đường.
        assertEquals(DISTANCE_KM,
                busRepository.findById(bus.getId()).orElseThrow().getKmSinceLastMaintenance(), 0.0001);
    }

    /**
     * Hợp đồng FSM đã tài liệu hoá ("Same state set again → Always allowed") phải
     * được giữ: guard mới làm cho lệnh đó thành no-op, KHÔNG biến nó thành lỗi.
     */
    @Test
    void settingTheSameStatusAgain_isStillAllowedAndNotAnError() {
        tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);

        assertDoesNotThrow(() -> tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED));
        assertEquals(TripStatus.COMPLETED, statusInDb());
    }

    /**
     * Guard mới không được nới lỏng whitelist: một transition thật sự sai vẫn phải
     * bị FSM chặn (COMPLETED là trạng thái cuối).
     */
    @Test
    void invalidTransitionIsStillRejected() {
        tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);

        assertThrows(IllegalStateException.class,
                () -> tripService.updateTripStatus(trip.getId(), TripStatus.ACTIVE));
    }

    // =====================================================================
    // Nhóm #20 — chỉ chuyến PENDING_APPROVAL mới được phê duyệt
    // =====================================================================

    /**
     * Dựng xe + tài xế sạch cho nhóm #20.
     *
     * Phải sạch một cách CÓ CHỦ ĐÍCH: nếu xe quá hạn bảo trì hoặc tài xế hết bằng
     * thì validateBusForTrip()/validateStaffForTrip() sẽ chặn trước, và các test
     * từ chối bên dưới sẽ xanh vì lý do khác — tức vô nghĩa. Với fixture sạch
     * này, bỏ requirePendingApproval() ra khỏi code sẽ khiến lệnh duyệt CHẠY
     * THÀNH CÔNG, nên test đỏ đúng chỗ.
     */
    private void setUpApprovalFixture() {
        approvalBus = new Bus();
        approvalBus.setLicensePlate("APR-GUARD-01");
        approvalBus.setBrand("TestBrand");
        approvalBus.setOdometer(1000.0);
        approvalBus.setLastMaintenanceOdometer(1000.0);
        approvalBus.setMaintenanceThreshold(10000.0);
        approvalBus.setStatus(BusStatus.READY);
        approvalBus = busRepository.save(approvalBus);

        User user = new User();
        user.setUsername("approval-guard-driver");
        user.setFullName("Tài Xế Guard #20");
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("APR-0001");
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);

        user = userRepository.save(user); // cascade = ALL → lưu luôn Driver
        approvalDriver = user.getDriver();
    }

    /**
     * Chuyến 4h ở NGÀY MAI với xe + tài xế đã gán sẵn, seed thẳng qua repository
     * (không qua FSM) đúng cách DataInitializer/HistoricalDataBackfill vẫn làm.
     *
     * Ngày mai để tránh nhiễu: totalDrivingHours24h chỉ cộng cho hôm nay, và cửa
     * sổ tương lai thì không đụng chuyến DEPARTED của fixture gốc.
     */
    private Trip approvalTripWithStatus(TripStatus status) {
        LocalDateTime departure = LocalDateTime.now().plusDays(1)
                .withHour(8).withMinute(0).withSecond(0).withNano(0);

        Trip t = new Trip();
        t.setBus(approvalBus);
        t.setDriver(approvalDriver);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusHours(4));
        t.setTotalSeats(40);
        t.setStatus(status);
        return tripRepository.save(t);
    }

    /** Từ chối phải là IllegalStateException VÀ phải nói đúng bệnh. */
    private void assertRefusedForStatus(Executable call, Trip subject, TripStatus expectedUnchanged) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, call);
        assertTrue(ex.getMessage().contains("PENDING_APPROVAL"),
                "Thông điệp phải nêu đúng lý do trạng thái, nhận được: " + ex.getMessage());
        assertEquals(expectedUnchanged, tripRepository.findById(subject.getId()).orElseThrow().getStatus());
    }

    /**
     * Ca đã tái hiện được trên app thật (chuyến 2749): duyệt thủ công một chuyến
     * CANCELLED. CANCELLED là trạng thái CUỐI — trip_lifecycle_fsm.md §5 xếp nó
     * vào bảng "Invalid Transitions (Enforced)".
     */
    @Test
    void approveTrip_onCancelledTrip_isRefusedAndChangesNothing() {
        setUpApprovalFixture();
        Trip cancelled = approvalTripWithStatus(TripStatus.CANCELLED);

        assertRefusedForStatus(
                () -> tripService.approveTrip(cancelled.getId(), approvalBus.getId(),
                        approvalDriver.getUserId(), null, null),
                cancelled, TripStatus.CANCELLED);
    }

    /**
     * Cửa vào của chuỗi hỏng odometer: COMPLETED → ACTIVE rồi hoàn thành lần nữa
     * sẽ cộng route.distanceKm lần hai (họ lỗi #6, qua một cửa khác).
     */
    @Test
    void approveTrip_onCompletedTrip_isRefusedAndChangesNothing() {
        setUpApprovalFixture();
        Trip completed = approvalTripWithStatus(TripStatus.COMPLETED);

        assertRefusedForStatus(
                () -> tripService.approveTrip(completed.getId(), approvalBus.getId(),
                        approvalDriver.getUserId(), null, null),
                completed, TripStatus.COMPLETED);
    }

    /**
     * Ca nguy hiểm nhất, và là lý do guard phải nằm TRƯỚC setBus(): với chuyến
     * DEPARTED, dời con trỏ xe làm xe cũ kẹt TRAVELING vĩnh viễn (lỗi #14). Test
     * này khẳng định thẳng vào con trỏ xe, không chỉ vào trạng thái.
     */
    @Test
    void approveTrip_onDepartedTrip_isRefusedAndDoesNotMoveTheBusPointer() {
        setUpApprovalFixture();
        Trip departed = approvalTripWithStatus(TripStatus.DEPARTED);

        // Một chiếc xe KHÁC, để nếu guard thủng thì con trỏ xe đổi thấy rõ.
        Bus otherBus = new Bus();
        otherBus.setLicensePlate("APR-GUARD-02");
        otherBus.setBrand("TestBrand");
        otherBus.setOdometer(500.0);
        otherBus.setLastMaintenanceOdometer(500.0);
        otherBus.setMaintenanceThreshold(10000.0);
        otherBus.setStatus(BusStatus.READY);
        otherBus = busRepository.save(otherBus);
        Long otherBusId = otherBus.getId();

        assertRefusedForStatus(
                () -> tripService.approveTrip(departed.getId(), otherBusId,
                        approvalDriver.getUserId(), null, null),
                departed, TripStatus.DEPARTED);

        assertEquals(approvalBus.getId(),
                tripRepository.findById(departed.getId()).orElseThrow().getBus().getId(),
                "Chuyến DEPARTED bị từ chối duyệt thì xe của nó KHÔNG được đổi");
    }

    /** Cửa thứ hai — cùng lỗ, cùng cách vá. Cũng đã tái hiện thật trên 2749. */
    @Test
    void confirmAutoAssignedTrip_onCancelledTrip_isRefusedAndChangesNothing() {
        setUpApprovalFixture();
        Trip cancelled = approvalTripWithStatus(TripStatus.CANCELLED);

        assertRefusedForStatus(
                () -> tripService.confirmAutoAssignedTrip(cancelled.getId()),
                cancelled, TripStatus.CANCELLED);
    }

    /**
     * ĐỐI TRỌNG (1/2): guard không được biến thành "cấm duyệt tất". Luồng thật —
     * duyệt thủ công một chuyến PENDING_APPROVAL — phải vẫn kích hoạt được.
     */
    @Test
    void approveTrip_onPendingTrip_stillActivates() {
        setUpApprovalFixture();
        Trip pending = approvalTripWithStatus(TripStatus.PENDING_APPROVAL);

        assertDoesNotThrow(() -> tripService.approveTrip(pending.getId(), approvalBus.getId(),
                approvalDriver.getUserId(), null, null));

        Trip saved = tripRepository.findById(pending.getId()).orElseThrow();
        assertEquals(TripStatus.ACTIVE, saved.getStatus());
        // changeStatusToActive() phải đóng dấu mốc mở bán — tác dụng phụ khi VÀO ACTIVE.
        assertTrue(saved.getSaleOpenedAt() != null, "Vào ACTIVE phải đóng dấu saleOpenedAt");
    }

    /** ĐỐI TRỌNG (2/2): cửa xác nhận 1-click cũng phải còn chạy. */
    @Test
    void confirmAutoAssignedTrip_onPendingTrip_stillActivates() {
        setUpApprovalFixture();
        Trip pending = approvalTripWithStatus(TripStatus.PENDING_APPROVAL);

        assertDoesNotThrow(() -> tripService.confirmAutoAssignedTrip(pending.getId()));

        assertEquals(TripStatus.ACTIVE,
                tripRepository.findById(pending.getId()).orElseThrow().getStatus());
    }
}
