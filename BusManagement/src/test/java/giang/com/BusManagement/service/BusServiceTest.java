package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Ghim bản sửa lỗi #16 (docs/todo/current_bugs_found.md): ranh giới giữa TẠO và
 * SỬA một chiếc xe, cùng ràng buộc tính hợp lệ của ba con số bảo trì.
 *
 * Trước bản sửa, saveBus() dùng chung cho cả hai luồng và biến null thành 0.0.
 * Chỉ cần xoá trắng ô "Odometer Hiện Tại" trên form sửa rồi bấm Lưu là odometer
 * về 0 kèm thông báo THÀNH CÔNG — đã tái hiện trên app thật: 8000 km → 0,
 * kmSinceLastMaintenance = −4000, tức xe không bao giờ tới hạn bảo trì nữa.
 *
 * Cặp test cốt lõi là "ô trống thì giữ nguyên" + "gõ số 0 thì vẫn ghi 0", theo
 * đúng khuôn cặp test của bản sửa lỗi #6 và #11: bản sửa không được đi quá tay
 * thành "bỏ qua mọi số 0".
 *
 * Chạy trên busmanagement_test, @Transactional nên mọi bản ghi được rollback.
 */
@SpringBootTest
@Transactional
class BusServiceTest {

    @Autowired
    private BusService busService;
    @Autowired
    private BusRepository busRepository;
    @Autowired
    private TripRepository tripRepository;

    /**
     * Chuyến tối thiểu chỉ đủ để guard REPAIRING nhìn thấy: guard hỏi
     * existsByBusIdAndStatusIn(busId, statuses), nên route/tài xế không liên quan
     * và để null. Ghi thẳng qua repository theo đúng tiền lệ DataInitializer —
     * đi qua TripService sẽ kéo theo toàn bộ validate nghiệp vụ không thuộc
     * phạm vi test này.
     */
    private Trip tripFor(Bus bus, TripStatus status) {
        Trip trip = new Trip();
        trip.setBus(bus);
        trip.setStatus(status);
        trip.setDepartureTime(java.time.LocalDateTime.now().plusHours(2));
        trip.setTotalSeats(40);
        return tripRepository.save(trip);
    }

    /** Xe đã có lịch sử vận hành: 8000 km trọn đời, bảo trì cuối lúc 4000 km. */
    private Bus persistedBus() {
        Bus bus = new Bus();
        bus.setLicensePlate("99T-UNIT.01");
        bus.setBrand("UNIT-TEST");
        bus.setStatus(BusStatus.READY);
        bus.setOdometer(8000.0);
        bus.setLastMaintenanceOdometer(4000.0);
        bus.setMaintenanceThreshold(5000.0);
        return busRepository.save(bus);
    }

    /** Đúng thứ AdminBusController dựng từ form: object rời, chỉ có field của form. */
    private Bus form(Double odometer, Double lastMaintenance, Double threshold) {
        Bus f = new Bus();
        f.setLicensePlate("99T-UNIT.01");
        f.setBrand("UNIT-TEST");
        f.setStatus(BusStatus.READY);
        f.setOdometer(odometer);
        f.setLastMaintenanceOdometer(lastMaintenance);
        f.setMaintenanceThreshold(threshold);
        return f;
    }

    // =========================================================================
    // SỬA: ô để trống nghĩa là GIỮ NGUYÊN
    // =========================================================================

    @Test
    @DisplayName("Sửa xe với ô số để trống → giữ nguyên giá trị cũ, KHÔNG về 0")
    void update_blankNumberKeepsExistingValue() {
        Bus bus = persistedBus();

        busService.updateBus(bus.getId(), form(null, null, null));

        Bus after = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(8000.0, after.getOdometer(), 1e-9, "odometer bị xoá mất — đây chính là lỗi #16");
        assertEquals(4000.0, after.getLastMaintenanceOdometer(), 1e-9);
        assertEquals(5000.0, after.getMaintenanceThreshold(), 1e-9);
        assertEquals(4000.0, after.getKmSinceLastMaintenance(), 1e-9, "km kể từ bảo trì không được thành âm");
    }

    /**
     * ĐỐI TRỌNG. "null = giữ nguyên" không được trượt thành "bỏ qua mọi số 0":
     * một chiếc xe mới tinh vẫn phải đặt được odometer = 0 bằng cách GÕ số 0.
     */
    @Test
    @DisplayName("ĐỐI TRỌNG: gõ số 0 thì vẫn ghi 0 — chỉ ô TRỐNG mới là giữ nguyên")
    void update_explicitZeroIsStillWritten() {
        Bus bus = persistedBus();

        busService.updateBus(bus.getId(), form(0.0, 0.0, 5000.0));

        Bus after = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(0.0, after.getOdometer(), 1e-9);
        assertEquals(0.0, after.getLastMaintenanceOdometer(), 1e-9);
    }

    @Test
    @DisplayName("ĐỐI TRỌNG: sửa bình thường vẫn ghi đủ mọi field")
    void update_stillWritesEverythingItShould() {
        Bus bus = persistedBus();

        Bus f = form(9500.0, 9000.0, 6000.0);
        f.setLicensePlate("99T-UNIT.02");
        f.setBrand("ĐÃ ĐỔI");
        f.setStatus(BusStatus.REPAIRING);
        busService.updateBus(bus.getId(), f);

        Bus after = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals("99T-UNIT.02", after.getLicensePlate());
        assertEquals("ĐÃ ĐỔI", after.getBrand());
        assertEquals(BusStatus.REPAIRING, after.getStatus());
        assertEquals(9500.0, after.getOdometer(), 1e-9);
        assertEquals(9000.0, after.getLastMaintenanceOdometer(), 1e-9);
        assertEquals(6000.0, after.getMaintenanceThreshold(), 1e-9);
    }

    /**
     * Ràng buộc "không chuyển sang REPAIRING khi còn chuyến ACTIVE/PENDING" được
     * DỜI từ saveBus() sang updateBus() nguyên văn. Test này chốt nó không báo
     * nhầm cho xe không có chuyến nào; nhánh chặn thật đã được kiểm bằng cách
     * drive app (xe có chuyến chưa kết thúc).
     */
    @Test
    @DisplayName("Guard REPAIRING không chặn nhầm xe chưa có chuyến nào")
    void update_toRepairingIsAllowedWhenBusHasNoTrips() {
        Bus bus = persistedBus();

        Bus f = form(8000.0, 4000.0, 5000.0);
        f.setStatus(BusStatus.REPAIRING);

        assertDoesNotThrow(() -> busService.updateBus(bus.getId(), f));
        assertEquals(BusStatus.REPAIRING, busRepository.findById(bus.getId()).orElseThrow().getStatus());
    }

    /**
     * Ghim bản sửa lỗi #15. DEPARTED từng THIẾU trong danh sách chặn, nên Admin
     * đánh dấu bảo trì cho một chiếc xe đang lăn bánh vẫn nhận "thành công" — rồi
     * side-effect của COMPLETED (TripService:618) đặt lại READY và xoá dấu đó
     * không một dòng cảnh báo.
     *
     * Chặn ở đây làm đường xoá âm thầm biến mất: không còn cách nào để xe ở
     * REPAIRING trong lúc còn chuyến chưa kết thúc.
     */
    @Test
    @DisplayName("Chặn REPAIRING khi xe đang có chuyến DEPARTED — lỗi #15")
    void update_toRepairingIsBlockedWhenBusIsOnADepartedTrip() {
        Bus bus = persistedBus();
        tripFor(bus, TripStatus.DEPARTED);

        Bus f = form(8000.0, 4000.0, 5000.0);
        f.setStatus(BusStatus.REPAIRING);

        assertThrows(RuntimeException.class, () -> busService.updateBus(bus.getId(), f));
        assertEquals(BusStatus.READY, busRepository.findById(bus.getId()).orElseThrow().getStatus(),
                "trạng thái xe không được đổi khi guard đã chặn");
    }

    /**
     * ĐỐI TRỌNG. Danh sách chặn là phần bù của tập trạng thái CUỐI, nên bản sửa
     * không được trượt thành "cứ có chuyến là chặn": một chiếc xe chỉ còn lịch sử
     * COMPLETED/CANCELLED vẫn phải đưa đi bảo trì được — nếu không thì sau vài
     * tháng vận hành sẽ không xe nào bảo trì được nữa.
     */
    @Test
    @DisplayName("ĐỐI TRỌNG: chuyến COMPLETED/CANCELLED không chặn — chỉ chuyến CHƯA kết thúc mới chặn")
    void update_toRepairingIsAllowedWhenOnlyTerminalTrips() {
        Bus bus = persistedBus();
        tripFor(bus, TripStatus.COMPLETED);
        tripFor(bus, TripStatus.CANCELLED);

        Bus f = form(8000.0, 4000.0, 5000.0);
        f.setStatus(BusStatus.REPAIRING);

        assertDoesNotThrow(() -> busService.updateBus(bus.getId(), f));
        assertEquals(BusStatus.REPAIRING, busRepository.findById(bus.getId()).orElseThrow().getStatus());
    }

    // =========================================================================
    // Ràng buộc tính hợp lệ của ba con số
    // =========================================================================

    @Test
    @DisplayName("Chặn số âm — odometer/km bảo trì âm không có nghĩa vật lý")
    void update_rejectsNegativeNumbers() {
        Bus bus = persistedBus();

        assertThrows(IllegalArgumentException.class,
                () -> busService.updateBus(bus.getId(), form(-500.0, 4000.0, 5000.0)));
        assertThrows(IllegalArgumentException.class,
                () -> busService.updateBus(bus.getId(), form(8000.0, -1.0, 5000.0)));
    }

    /**
     * odometer < lastMaintenanceOdometer ⇒ kmSinceLastMaintenance ÂM ⇒
     * needsMaintenance()/isNearMaintenance() vĩnh viễn false: xe không bao giờ
     * được đưa đi bảo trì nữa. Đây là dạng hỏng mà cú xoá ô ở lỗi #16 tạo ra.
     */
    @Test
    @DisplayName("Chặn odometer nhỏ hơn km bảo trì cuối — nếu không, xe không bao giờ tới hạn bảo trì")
    void update_rejectsOdometerBelowLastMaintenance() {
        Bus bus = persistedBus();

        assertThrows(IllegalArgumentException.class,
                () -> busService.updateBus(bus.getId(), form(3999.0, 4000.0, 5000.0)));
    }

    /**
     * Ngưỡng 0 khoá xe VĨNH VIỄN: needsMaintenance() là {@code km >= threshold},
     * mà {@code 0 >= 0} luôn đúng, nên ngay cả thao tác "vừa bảo trì xong" cũng
     * không gỡ được. Đã tái hiện trên app thật.
     */
    @Test
    @DisplayName("Chặn ngưỡng ≤ 0 — ngưỡng 0 khiến xe bị coi là quá hạn vĩnh viễn")
    void update_rejectsNonPositiveThreshold() {
        Bus bus = persistedBus();

        assertThrows(IllegalArgumentException.class,
                () -> busService.updateBus(bus.getId(), form(8000.0, 4000.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> busService.updateBus(bus.getId(), form(8000.0, 4000.0, -1.0)));
    }

    // =========================================================================
    // TẠO: mặc định vẫn giữ nguyên hành vi cũ
    // =========================================================================

    @Test
    @DisplayName("Tạo xe bỏ trống ba ô số → vẫn nhận mặc định 0 / 0 / 5000")
    void create_appliesDefaultsWhenNumbersOmitted() {
        Bus f = form(null, null, null);
        f.setLicensePlate("99T-UNIT.09");

        busService.saveBus(f);

        Bus saved = busRepository.findById(f.getId()).orElseThrow();
        assertEquals(0.0, saved.getOdometer(), 1e-9);
        assertEquals(0.0, saved.getLastMaintenanceOdometer(), 1e-9);
        assertEquals(5000.0, saved.getMaintenanceThreshold(), 1e-9);
    }

    @Test
    @DisplayName("Tạo xe cũng chịu ràng buộc — số âm bị chặn ngay từ lúc tạo")
    void create_rejectsInvalidNumbers() {
        Bus f = form(-1.0, 0.0, 5000.0);
        f.setLicensePlate("99T-UNIT.10");

        assertThrows(IllegalArgumentException.class, () -> busService.saveBus(f));
    }

    /**
     * Tripwire: nếu ai đó gọi lại saveBus() cho một entity đã có id, JPA sẽ merge
     * và ghi đè mọi cột bằng giá trị của form — tức lỗi #16 quay lại bằng cửa sau,
     * đồng thời đi vòng qua ràng buộc REPAIRING nay nằm trong updateBus().
     */
    @Test
    @DisplayName("Tripwire: saveBus() từ chối entity đã có id — cập nhật phải qua updateBus()")
    void saveBus_refusesEntityThatAlreadyHasAnId() {
        Bus bus = persistedBus();

        Bus f = form(0.0, 0.0, 5000.0);
        f.setId(bus.getId());

        assertThrows(IllegalArgumentException.class, () -> busService.saveBus(f));
        assertEquals(8000.0, busRepository.findById(bus.getId()).orElseThrow().getOdometer(), 1e-9);
    }
}
