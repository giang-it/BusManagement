package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static final double DISTANCE_KM = 120.0;
    private static final double START_ODOMETER = 1000.0;

    private Bus bus;
    private Trip trip;

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
}
