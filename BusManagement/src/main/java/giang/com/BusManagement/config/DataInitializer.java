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

        System.out.println("🤖 [AI Test Setup] Đang khởi tạo dữ liệu đồng bộ với Domain mới...");

        // 1. LOẠI XE
        BusType limousine = createBusType("Limousine", 22);
        BusType giuongNam = createBusType("Giường nằm", 40);

        // 2. NHÂN SỰ (Tài xế & Admin)
        createUser("admin", "Admin Hệ Thống", Role.ROLE_ADMIN);

        // Các tài xế cực kỳ rảnh (0h lái) -> Đủ điều kiện, cần nhiều vì mỗi xe cần 2
        // người
        Driver driverFresh1 = createDriver("driver_fresh1", "Nguyễn Văn Rảnh 1", "B2-111", 5, 0.0);
        Driver driverFresh2 = createDriver("driver_fresh2", "Nguyễn Văn Rảnh 2", "B2-112", 5, 0.0);
        Driver driverFresh3 = createDriver("driver_fresh3", "Nguyễn Văn Rảnh 3", "B2-113", 5, 0.0);
        Driver driverFresh4 = createDriver("driver_fresh4", "Nguyễn Văn Rảnh 4", "B2-114", 5, 0.0);
        Driver driverFresh5 = createDriver("driver_fresh5", "Nguyễn Văn Rảnh 5", "B2-115", 5, 0.0);
        Driver driverFresh6 = createDriver("driver_fresh6", "Nguyễn Văn Rảnh 6", "B2-116", 5, 0.0);
        Driver driverFresh7 = createDriver("driver_fresh7", "Nguyễn Văn Rảnh 7", "B2-117", 5, 0.0);
        Driver driverFresh8 = createDriver("driver_fresh8", "Nguyễn Văn Rảnh 8", "B2-118", 5, 0.0);

        // Tài xế B: Sắp chạm ngưỡng (7.5h lái) -> AI sẽ KHÔNG chọn vì chuyến đi thường
        // > 0.5h
        Driver driverBusy = createDriver("driver_busy", "Trần Văn Chăm", "D-222", 10, 7.5);

        // 3. XE KHÁCH
        // Xe: Sẵn sàng, Odo thấp -> AI ưu tiên
        Bus busNew1 = createBus("29A-111.11", "Thaco", limousine, BusStatus.READY, 1000.0);
        Bus busNew2 = createBus("29A-111.12", "Thaco", limousine, BusStatus.READY, 1000.0);
        Bus busNew3 = createBus("29A-111.13", "Thaco", giuongNam, BusStatus.READY, 1000.0);
        Bus busNew4 = createBus("29A-111.14", "Thaco", giuongNam, BusStatus.READY, 1000.0);

        // Xe CẦN BẢO TRÌ (Odo 12000 > Threshold 5000) -> AI sẽ cân nhắc/cảnh báo
        Bus busOld = createBus("29A-222.22", "Hyundai", giuongNam, BusStatus.READY, 12000.0);

        // 4. TRẠM & TUYẾN ĐƯỜNG (Sử dụng departurePoint & destinationPoint)
        Station hn = createStation("Bến xe Mỹ Đình", "Hà Nội");
        Station hp = createStation("Bến xe Niệm Nghĩa", "Hải Phòng");

        Route routeHnHp = new Route();
        routeHnHp.setDeparturePoint("Hà Nội");
        routeHnHp.setDestinationPoint("Hải Phòng");
        routeHnHp.setDistanceKm(120.0);
        routeHnHp.setEstimatedDuration(120); // 2 tiếng
        routeHnHp.setSuitableBusType(limousine);
        routeRepository.save(routeHnHp);

        // 5. CÁC CHUYẾN XE ĐỂ TEST AI

        // TEST CASE 1: KÍCH HOẠT AI TĂNG CƯỜNG TỰ ĐỘNG
        // Chuyến này có 37/40 ghế đã bán (92.5%) -> Sau 10s TripService sẽ tự tạo
        // chuyến extra
        createTrip(routeHnHp, busNew1, driverFresh1, driverFresh2, 40, 37, "250000", TripStatus.ACTIVE, 5);

        // TEST CASE 2: CHUYẾN CHỜ DUYỆT THỦ CÔNG
        // Tạo một chuyến trống để Admin vào phân công trong approve-form.html
        createTrip(routeHnHp, null, null, null, 40, 0, "250000", TripStatus.ACTIVE, 24);

        System.out.println("✅ [Dữ liệu đã sẵn sàng]");
        System.out.println(
                "👉 Hãy chờ 10 giây để AI quét chuyến 'Hà Nội -> Hải Phòng' (92.5% vé) và đề xuất tăng cường.");

        System.out.println("🚀 [AI Stress Test] Đang nạp thêm 20 thực thể thử nghiệm...");

        // --- 1. THÊM 5 ROUTE (Đa dạng quãng đường) ---
        Route rHnSg = createRoute("Hà Nội", "Sài Gòn", 1700.0, 1800, giuongNam); // Tuyến siêu dài (30h)
        Route rSgDl = createRoute("Sài Gòn", "Đà Lạt", 300.0, 360, limousine); // Tuyến trung bình (6h)
        Route rDnHue = createRoute("Đà Nẵng", "Huế", 100.0, 120, giuongNam); // Tuyến ngắn (2h)
        Route rHpHn = createRoute("Hải Phòng", "Hà Nội", 120.0, 100, giuongNam); // Cao tốc (1.5h)
        Route rCtSg = createRoute("Cần Thơ", "Sài Gòn", 180.0, 210, limousine); // Tuyến miền Tây

        // --- 2. THÊM 5 DRIVER (Với các Edge Cases) ---
        // Case 3: Tài xế vừa đủ ngưỡng (7.9h) -> AI gán bất cứ chuyến nào cũng sẽ quá
        // 8h
        Driver dEdgeHour = createDriver("driver_edge", "Lê Văn Tới Hạn", "E-999", 12, 7.9);

        // Case 4: Tài xế mới tinh, kinh nghiệm 0 -> AI ưu tiên gán chuyến ngắn/dễ
        Driver dNewbie = createDriver("driver_new", "Trần Văn Mới", "B2-000", 0, 0.0);

        // Case 5: Tài xế bị khóa (Inactive) -> AI tuyệt đối không được gán
        Driver dInactive = createDriver("driver_locked", "Nguyễn Văn Khóa", "C-888", 10, 0.0);
        dInactive.setIsActive(false);
        driverRepository.save(dInactive);

        // Case 6: Tài xế bằng lái sắp hết hạn (còn 1 ngày) -> Test cảnh báo
        Driver dExpiring = createDriver("driver_exp", "Hoàng Văn Hạn", "D-777", 8, 2.0);
        dExpiring.setLicenseExpiryDate(java.time.LocalDate.now().plusDays(1));
        driverRepository.save(dExpiring);

        // Case 7: Tài xế rảnh nhưng đã lái 6h, nếu gán tuyến rHnSg (30h) sẽ vi phạm
        // nặng
        Driver dMid = createDriver("driver_mid", "Vũ Văn Tầm Trung", "E-666", 5, 6.0);

        Driver dOver1 = createDriver("driver_over1", "Vũ Văn Tầm quá hạn1", "E-991", 5, 9.0);
        Driver dOver2 = createDriver("driver_over2", "Vũ Văn Tầm quá hạn2", "E-992", 5, 10.0);
        Driver dOver3 = createDriver("driver_over3", "Vũ Văn Tầm quá hạn3", "E-993", 5, 11.0);
        Driver dOver4 = createDriver("driver_over4", "Vũ Văn Tầm quá hạn4", "E-994", 5, 12.0);

        // --- 3. THÊM 5 BUS (Với các trạng thái khác nhau) ---
        // Case 3: Xe sát ngưỡng bảo trì (4995km) -> Đi tuyến > 5km là tạch
        Bus bEdgeOdo = createBus("51B-499.95", "Thaco-Edge", limousine, BusStatus.READY, 4995.0);

        // Case 4: Xe đang sửa chữa (Repairing) -> AI phải né
        Bus bRepair = createBus("51B-000.01", "Hỏng-Hóc", giuongNam, BusStatus.REPAIRING, 1000.0);

        // Case 5: Xe đang trên đường (Traveling) -> AI không gán cho chuyến mới trùng
        // giờ
        Bus bTraveling = createBus("51B-777.77", "Đang-Chạy", giuongNam, BusStatus.TRAVELING, 2000.0);

        // Case 6: Xe mới tinh (Odo 0)
        Bus bBrandNew = createBus("51B-000.00", "Siêu-Xe", limousine, BusStatus.READY, 0.0);

        // Case 7: Xe rảnh nhưng loại xe không phù hợp (Route yêu cầu Limousine, Xe là
        // 50 chỗ)
        Bus bWrongType = createBus("51B-555.55", "Sai-Loại", giuongNam, BusStatus.READY, 500.0);

        // --- 4. THÊM 5 TRIP (Để AI trổ tài) ---

        // Chuyến 1: Chuyến trống hoàn toàn -> AI phải tự tìm Bus/Driver phù hợp nhất
        createTrip(rSgDl, null, null, null, 22, 0, "300000", TripStatus.PENDING_APPROVAL, 4);

        // Chuyến 2: Chuyến sắp đầy (21/22 ghế) -> AI phải kích hoạt tạo chuyến phụ
        // (Extra Trip)
        createTrip(rCtSg, bBrandNew, dNewbie, driverFresh3, 22, 21, "200000", TripStatus.ACTIVE, 6);

        // Chuyến 3: Tuyến cực dài (30h) -> AI phải tìm tài xế có 0h lái và xe cực tốt
        createTrip(rHnSg, null, null, null, 40, 0, "900000", TripStatus.PENDING_APPROVAL, 24);

        // Chuyến 4: Test logic từ chối -> Gán ép tài xế dEdgeHour (7.9h) vào chuyến 2h
        // Sau đó Admin vào xem AI sẽ cảnh báo đỏ hoặc không cho lưu.
        createTrip(rDnHue, null, dEdgeHour, null, 50, 10, "150000", TripStatus.PENDING_APPROVAL, 2);

        // Chuyến 5: Chuyến ngắn cao tốc
        createTrip(rHpHn, null, null, null, 60, 0, "120000", TripStatus.PENDING_APPROVAL, 3);

    }

    // --- CÁC HÀM TRỢ GIÚP (HELPER) ---

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
        d.setLicenseExpiryDate(java.time.LocalDate.now().plusYears(1)); // Bằng lái còn hạn
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
        t.setDepartureTime(LocalDateTime.now().plusHours(hoursFromNow));
        t.setArrivalTimeExpected(LocalDateTime.now().plusHours(hoursFromNow + 2));
        t.setExtraTrip(false);
        tripRepository.save(t);
    }
}