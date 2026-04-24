package giang.com.BusManagement.config;

import giang.com.BusManagement.domain.*;
import giang.com.BusManagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final BusTypeRepository busTypeRepository;
    private final BusRepository busRepository;
    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final RouteStationRepository routeStationRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Xóa dữ liệu cũ để đảm bảo test case sạch
        tripRepository.deleteAll();
        driverRepository.deleteAll();
        userRepository.deleteAll();
        busRepository.deleteAll();
        busTypeRepository.deleteAll();
        routeStationRepository.deleteAll();
        routeRepository.deleteAll();
        stationRepository.deleteAll();

        System.out.println("🤖 [AI Test Setup] Đang khởi tạo dữ liệu test...");

        // =====================================================================
        // 1. LOẠI XE
        // =====================================================================
        BusType limousine = createBusType("Limousine", 22);
        BusType giuongNam = createBusType("Giường nằm", 40);
        BusType gheNgoi = createBusType("Ghế ngồi", 50);

        // =====================================================================
        // 2. ADMIN
        // =====================================================================
        createUser("admin", "Admin Hệ Thống", Role.ROLE_ADMIN);

        // =====================================================================
        // 3. TÀI XẾ — Tên mô tả rõ trạng thái để dễ nhìn trên giao diện
        // =====================================================================

        // Nhóm A: Tài xế rảnh 0h — AI thoải mái gán
        Driver txRanh1 = createDriver("tx_ranh1", "TX Rảnh 0h (A1)", "B2-001", 5, 0.0);
        Driver txRanh2 = createDriver("tx_ranh2", "TX Rảnh 0h (A2)", "B2-002", 8, 0.0);
        Driver txRanh3 = createDriver("tx_ranh3", "TX Rảnh 0h (A3)", "B2-003", 3, 0.0);
        Driver txRanh4 = createDriver("tx_ranh4", "TX Rảnh 0h (A4)", "B2-004", 6, 0.0);
        Driver txRanh5 = createDriver("tx_ranh5", "TX Rảnh 0h (A5)", "B2-005", 10, 0.0);
        Driver txRanh6 = createDriver("tx_ranh6", "TX Rảnh 0h (A6)", "B2-006", 4, 0.0);
        Driver txRanh7 = createDriver("tx_ranh7", "TX Rảnh 0h (A7)", "B2-007", 7, 0.0);
        Driver txRanh8 = createDriver("tx_ranh8", "TX Rảnh 0h (A8)", "B2-008", 2, 0.0);

        // Nhóm B: Tài xế đã lái một số giờ
        Driver txDaLai4h = createDriver("tx_4h", "TX Đã lái 4h", "B2-010", 5, 4.0);
        Driver txDaLai6h = createDriver("tx_6h", "TX Đã lái 6h", "B2-011", 5, 6.0);
        Driver txDaLai7h = createDriver("tx_7h", "TX Đã lái 7h", "B2-012", 5, 7.0);

        // Nhóm C: Tài xế sát ngưỡng / quá 8h — AI KHÔNG ĐƯỢC gán
        Driver txSatNguong = createDriver("tx_sat8h", "TX Sát 8h (7.9h)", "B2-020", 12, 7.9);
        Driver txQua8h_1 = createDriver("tx_qua8h_1", "TX Quá 8h (9h)", "B2-021", 5, 9.0);
        Driver txQua8h_2 = createDriver("tx_qua8h_2", "TX Quá 8h (10h)", "B2-022", 5, 10.0);
        Driver txQua8h_3 = createDriver("tx_qua8h_3", "TX Quá 8h (11h)", "B2-023", 5, 11.0);

        // Nhóm D: Tài xế bị khóa — AI tuyệt đối không gán
        Driver txBiKhoa = createDriver("tx_khoa", "TX Bị Khóa", "B2-030", 10, 0.0);
        txBiKhoa.setIsActive(false);
        driverRepository.save(txBiKhoa);

        // Nhóm E: Tài xế bằng lái sắp hết hạn (còn 1 ngày) — AI cảnh báo
        Driver txHetHanBL = createDriver("tx_hethan", "TX Bằng Lái Sắp Hết", "B2-040", 8, 2.0);
        txHetHanBL.setLicenseExpiryDate(java.time.LocalDate.now().plusDays(1));
        driverRepository.save(txHetHanBL);

        // Nhóm F: Tài xế bằng lái ĐÃ hết hạn — AI phải loại hoàn toàn
        Driver txHetHanHoanToan = createDriver("tx_expired", "TX Bằng Lái Đã Hết", "B2-041", 6, 0.0);
        txHetHanHoanToan.setLicenseExpiryDate(java.time.LocalDate.now().minusDays(5));
        driverRepository.save(txHetHanHoanToan);

        // Nhóm G: Tài xế mới tinh (0 kinh nghiệm)
        Driver txMoiTinh = createDriver("tx_moi", "TX Mới 0 Năm KN", "B2-050", 0, 0.0);

        // =====================================================================
        // 4. XE KHÁCH — Tên mô tả rõ trạng thái
        // =====================================================================

        // Xe sẵn sàng, odo thấp → AI ưu tiên
        Bus xeSanSang1 = createBus("29A-001.01", "Thaco-Mới", limousine, BusStatus.READY, 500.0);
        Bus xeSanSang2 = createBus("29A-001.02", "Thaco-Mới", limousine, BusStatus.READY, 800.0);
        Bus xeSanSang3 = createBus("29A-001.03", "Thaco-Mới", giuongNam, BusStatus.READY, 600.0);
        Bus xeSanSang4 = createBus("29A-001.04", "Thaco-Mới", giuongNam, BusStatus.READY, 700.0);
        Bus xeSanSang5 = createBus("29A-001.05", "Thaco-Mới", gheNgoi, BusStatus.READY, 300.0);

        // Xe sát ngưỡng bảo trì (4995km / 5000km) → Đi thêm > 5km là quá ngưỡng
        Bus xeSatBaoTri = createBus("51B-SAT.BT", "Xe Sát Bảo Trì", limousine, BusStatus.READY, 4995.0);

        // Xe đã quá ngưỡng bảo trì (6000km / 5000km) → AI fallback + cảnh báo
        Bus xeQuaBaoTri = createBus("51B-QUA.BT", "Xe Quá Bảo Trì", giuongNam, BusStatus.READY, 6000.0);

        // Xe đang sửa chữa → AI phải né
        Bus xeDangSua = createBus("51B-SUA.XE", "Xe Đang Sửa", giuongNam, BusStatus.REPAIRING, 1000.0);

        // Xe đang trên đường → AI không gán cho chuyến trùng giờ
        Bus xeDangChay = createBus("51B-DAG.CH", "Xe Đang Chạy", giuongNam, BusStatus.TRAVELING, 2000.0);

        // Xe mới tinh (Odo 0)
        Bus xeMoiTinh = createBus("51B-MOI.00", "Xe Mới Tinh", limousine, BusStatus.READY, 0.0);

        // =====================================================================
        // 5. TRẠM & TUYẾN ĐƯỜNG
        // =====================================================================
        Station hn = createStation("Bến xe Mỹ Đình", "Hà Nội");
        Station hp = createStation("Bến xe Niệm Nghĩa", "Hải Phòng");
        Station sg = createStation("Bến xe Miền Đông", "Sài Gòn");
        Station dl = createStation("Bến xe Đà Lạt", "Đà Lạt");
        Station dn = createStation("Bến xe Đà Nẵng", "Đà Nẵng");
        Station hue = createStation("Bến xe Huế", "Huế");
        Station ct = createStation("Bến xe Cần Thơ", "Cần Thơ");

        // Tuyến ngắn (2h) — Limousine
        Route tuyenHN_HP = createRoute("Hà Nội", "Hải Phòng", 120.0, 120, limousine);

        // Tuyến trung bình (6h) — Limousine
        Route tuyenSG_DL = createRoute("Sài Gòn", "Đà Lạt", 300.0, 360, limousine);

        // Tuyến ngắn (2h) — Giường nằm
        Route tuyenDN_Hue = createRoute("Đà Nẵng", "Huế", 100.0, 120, giuongNam);

        // Tuyến cao tốc ngắn (1.5h)
        Route tuyenHP_HN = createRoute("Hải Phòng", "Hà Nội", 120.0, 90, giuongNam);

        // Tuyến miền Tây (3.5h) — Limousine
        Route tuyenCT_SG = createRoute("Cần Thơ", "Sài Gòn", 180.0, 210, limousine);

        // Tuyến siêu dài (30h) — Giường nằm
        Route tuyenHN_SG = createRoute("Hà Nội", "Sài Gòn", 1700.0, 1800, giuongNam);

        // =====================================================================
        // 6. CÁC CHUYẾN XE — TẤT CẢ STATUS = ACTIVE
        // =====================================================================

        // ------------------------------------------------------------------
        // TRIP 1: AI Tăng Cường — Chuyến gần đầy ghế (37/40 = 92.5%)
        // → AI sẽ tự tạo chuyến tăng cường sau 10 giây
        // ------------------------------------------------------------------
        createTrip(tuyenHN_HP, xeSanSang1, txRanh1, txRanh2, 40, 37, "250000", TripStatus.ACTIVE, 5);

        // ------------------------------------------------------------------
        // TRIP 2: AI Tăng Cường — Chuyến sắp đầy (21/22 = 95.5%)
        // → AI sẽ tạo thêm chuyến extra
        // ------------------------------------------------------------------
        createTrip(tuyenCT_SG, xeMoiTinh, txMoiTinh, txRanh3, 22, 21, "200000", TripStatus.ACTIVE, 6);

        // ------------------------------------------------------------------
        // TRIP 3: Chuyến vừa đủ — Không cần tăng cường (20/40 = 50%)
        // → AI sẽ bỏ qua chuyến này
        // ------------------------------------------------------------------
        createTrip(tuyenSG_DL, xeSanSang2, txRanh4, txRanh5, 40, 20, "300000", TripStatus.ACTIVE, 4);

        // ------------------------------------------------------------------
        // TRIP 4: Chuyến trống — Chưa gán bus/driver
        // → Trên giao diện sẽ thấy "Chưa gán" cho xe/tài xế/phụ xe
        // ------------------------------------------------------------------
        createTrip(tuyenDN_Hue, null, null, null, 50, 0, "150000", TripStatus.ACTIVE, 8);

        // ------------------------------------------------------------------
        // TRIP 5: TX Sát 8h — Gán ép tài xế 7.9h vào chuyến 2h
        // → Tổng = 7.9 + 2.0 = 9.9h > 8h → Admin thấy vi phạm
        // ------------------------------------------------------------------
        createTrip(tuyenDN_Hue, xeSanSang3, txSatNguong, null, 50, 10, "150000", TripStatus.ACTIVE, 2);

        // ------------------------------------------------------------------
        // TRIP 6: TX Quá 8h — Gán ép tài xế đã lái 9h
        // → Trực quan trên giao diện: tài xế tên "TX Quá 8h (9h)"
        // ------------------------------------------------------------------
        createTrip(tuyenHP_HN, xeSanSang4, txQua8h_1, null, 60, 5, "120000", TripStatus.ACTIVE, 3);

        // ------------------------------------------------------------------
        // TRIP 7: TX Bằng Lái Sắp Hết — Tài xế bằng lái còn 1 ngày
        // → AI đã cảnh báo nhưng vẫn gán (fallback)
        // ------------------------------------------------------------------
        createTrip(tuyenHP_HN, xeSanSang5, txHetHanBL, txRanh6, 60, 30, "120000", TripStatus.ACTIVE, 10);

        // ------------------------------------------------------------------
        // TRIP 8: Chuyến siêu dài (30h) — Tuyến HN → SG
        // → AI phải tìm tài xế 0h lái, xe giường nằm tốt
        // → effectiveHours = min(30, 8) = 8h
        // ------------------------------------------------------------------
        createTrip(tuyenHN_SG, xeSanSang3, txRanh7, txRanh8, 40, 35, "900000", TripStatus.ACTIVE, 24);

        // ------------------------------------------------------------------
        // TRIP 9: TX Đã Lái 6h + Chuyến 6h
        // → Tổng = 6 + min(6,8) = 12h > 8h → Vi phạm nếu kiểm tra
        // ------------------------------------------------------------------
        createTrip(tuyenSG_DL, xeSanSang2, txDaLai6h, txDaLai4h, 22, 10, "300000", TripStatus.ACTIVE, 12);

        // ------------------------------------------------------------------
        // TRIP 10: Chuyến với xe sát bảo trì
        // → Tuyến 120km + Odo 4995km = 5115km > 5000km ngưỡng → Cảnh báo
        // ------------------------------------------------------------------
        createTrip(tuyenHN_HP, xeSatBaoTri, txDaLai7h, null, 22, 15, "250000", TripStatus.ACTIVE, 14);

        // =====================================================================
        System.out.println("✅ [Dữ liệu đã sẵn sàng]");
        System.out.println("📊 Tổng: 10 chuyến | 16 tài xế | 11 xe | 7 tuyến");
        System.out.println("🔍 Các test case trên giao diện:");
        System.out.println("   Trip 1,2  → AI tự tạo chuyến tăng cường (>90% ghế)");
        System.out.println("   Trip 3    → Bình thường (50% ghế) → AI bỏ qua");
        System.out.println("   Trip 4    → Chưa gán xe/tài xế → Hiển thị 'Chưa gán'");
        System.out.println("   Trip 5    → TX Sát 8h (7.9h) lái thêm 2h → Vi phạm");
        System.out.println("   Trip 6    → TX Quá 8h (9h) → Vi phạm rõ ràng");
        System.out.println("   Trip 7    → TX Bằng Lái Sắp Hết → Cảnh báo");
        System.out.println("   Trip 8    → Chuyến 30h HN→SG (>90% ghế) → AI tăng cường");
        System.out.println("   Trip 9    → TX 6h + Chuyến 6h = 12h → Vi phạm");
        System.out.println("   Trip 10   → Xe sát bảo trì + tuyến 120km → Quá ngưỡng");
    }

    // =====================================================================
    // CÁC HÀM TRỢ GIÚP (HELPER)
    // =====================================================================

    private Route createRoute(String from, String to, double dist, int duration, BusType type) {
        Route r = new Route();
        r.setDeparturePoint(from);
        r.setDestinationPoint(to);
        r.setDistanceKm(dist);
        r.setEstimatedDuration(duration);
        r.setSuitableBusType(type);
        return routeRepository.save(r);
    }

    private BusType createBusType(String name, int cap) {
        BusType bt = new BusType();
        bt.setTypeName(name);
        bt.setCapacity(cap);
        return busTypeRepository.save(bt);
    }

    private User createUser(String user, String name, Role role) {
        User u = new User();
        u.setUsername(user);
        u.setPassword("123456");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.save(u);
    }

    private Driver createDriver(String user, String name, String license, int exp, double hours) {
        User u = createUser(user, name, Role.ROLE_DRIVER);
        Driver d = new Driver();
        d.setUser(u);
        d.setLicenseNumber(license);
        d.setExperienceYears(exp);
        d.setTotalDrivingHours24h(hours);
        d.setIsActive(true);
        d.setLicenseExpiryDate(java.time.LocalDate.now().plusYears(1)); // Bằng lái còn hạn 1 năm
        return driverRepository.save(d);
    }

    private Bus createBus(String plate, String brand, BusType type, BusStatus status, double odo) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand(brand);
        b.setBusType(type);
        b.setStatus(status);
        b.setOdometer(odo);
        b.setLastMaintenanceOdometer(0.0);
        b.setMaintenanceThreshold(5000.0);
        return busRepository.save(b);
    }

    private Station createStation(String name, String addr) {
        Station s = new Station();
        s.setStationName(name);
        s.setAddress(addr);
        return stationRepository.save(s);
    }

    private void createTrip(Route r, Bus b, Driver mainDriver, Driver assistantDriver, int total, int sold,
            String price, TripStatus status,
            int hoursFromNow) {
        Trip t = new Trip();
        t.setRoute(r);
        t.setBus(b);
        t.setDriver(mainDriver);
        t.setAssistant(assistantDriver);
        t.setTotalSeats(total);
        t.setTicketsSold(sold);
        t.setPrice(new BigDecimal(price));
        t.setStatus(status);
        LocalDateTime departure = LocalDateTime.now().plusHours(hoursFromNow);
        t.setDepartureTime(departure);
        // Dùng estimatedDuration từ Route thay vì hardcode +2h
        int durationMinutes = (r.getEstimatedDuration() != null) ? r.getEstimatedDuration() : 120;
        t.setArrivalTimeExpected(departure.plusMinutes(durationMinutes));
        t.setExtraTrip(false);
        tripRepository.save(t);
    }
}