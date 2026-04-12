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

        tripRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.deleteAll();
        routeStationRepository.deleteAll();
        stationRepository.deleteAll();
        routeRepository.deleteAll();
        userRepository.deleteAll();
        busTypeRepository.deleteAll();

        if (userRepository.count() > 0)
            return; // Tránh tạo trùng dữ liệu khi restart

        // // 1. Khởi tạo BusType
        // BusType busTypeGiuongNam = new BusType();
        // busTypeGiuongNam.setTypeName("Giường nằm");
        // busTypeGiuongNam.setCapacity(40);
        // busTypeRepository.save(busTypeGiuongNam);

        // BusType busTypeGheNgoi = new BusType();
        // busTypeGheNgoi.setTypeName("Ghế ngồi");
        // busTypeGheNgoi.setCapacity(28);
        // busTypeRepository.save(busTypeGheNgoi);

        // // 2. Khởi tạo Users (Admin & Driver)
        // User admin = new User();
        // admin.setUsername("admin");
        // admin.setPassword("123456"); // Trong thực tế nên dùng BCrypt
        // admin.setFullName("Quản trị viên");
        // admin.setRole(Role.ROLE_ADMIN);
        // userRepository.save(admin);

        // User userDriver1 = new User();
        // userDriver1.setUsername("driver1");
        // userDriver1.setFullName("Nguyễn Văn Tài");
        // userDriver1.setRole(Role.ROLE_DRIVER);
        // userRepository.save(userDriver1);

        // User userDriver2 = new User();
        // userDriver2.setUsername("driver2");
        // userDriver2.setFullName("Lê Văn Lái");
        // userDriver2.setRole(Role.ROLE_DRIVER);
        // userRepository.save(userDriver2);

        // User userDriver3 = new User();
        // userDriver3.setUsername("driver3");
        // userDriver3.setFullName("Nguyễn Văn Dự Phòng");
        // userDriver3.setRole(Role.ROLE_DRIVER);
        // userRepository.save(userDriver3);

        // // 3. Khởi tạo Drivers
        // Driver driver1 = new Driver();
        // driver1.setUser(userDriver1);
        // driver1.setLicenseNumber("B2-123456");
        // driver1.setExperienceYears(5);
        // driver1.setTotalDrivingHours24h(2.0); // Đã lái 2h, vẫn rảnh
        // driverRepository.save(driver1);

        // Driver driver2 = new Driver();
        // driver2.setUser(userDriver2);
        // driver2.setLicenseNumber("D-789012");
        // driver2.setExperienceYears(10);
        // driver2.setTotalDrivingHours24h(0.0);
        // driverRepository.save(driver2);

        // Driver driver3 = new Driver();
        // driver3.setUser(userDriver3);
        // driver3.setLicenseNumber("E-112233");
        // driver3.setExperienceYears(3);
        // driver3.setTotalDrivingHours24h(0.0); // Người này cực kỳ rảnh
        // driverRepository.save(driver3);

        // // 4. Khởi tạo Buses
        // Bus bus1 = new Bus();
        // bus1.setLicensePlate("29A-123.45");
        // bus1.setBrand("Thaco");
        // bus1.setBusType(busTypeGiuongNam);
        // bus1.setStatus(BusStatus.READY);
        // bus1.setTotalKm(1200.0);
        // busRepository.save(bus1);

        // Bus bus2 = new Bus();
        // bus2.setLicensePlate("30B-678.90");
        // bus2.setBrand("Mercedes");
        // bus2.setBusType(busTypeGiuongNam);
        // bus2.setStatus(BusStatus.READY);
        // bus2.setTotalKm(500.0);
        // busRepository.save(bus2);

        // // 5. Khởi tạo Stations
        // Station hanoi = new Station();
        // hanoi.setStationName("Bến xe Mỹ Đình");
        // hanoi.setAddress("Hà Nội");
        // stationRepository.save(hanoi);

        // Station haiphong = new Station();
        // haiphong.setStationName("Bến xe Niệm Nghĩa");
        // haiphong.setAddress("Hải Phòng");
        // stationRepository.save(haiphong);

        // Station a = new Station();
        // a.setStationName("Bến xe a");
        // a.setAddress("a address");
        // stationRepository.save(a);

        // Station b = new Station();
        // b.setStationName("Bến xe b");
        // b.setAddress("b address");
        // stationRepository.save(b);

        // // 6. Khởi tạo Route
        // Route routeHnHp = new Route();
        // routeHnHp.setDeparturePoint("Hà Nội");
        // routeHnHp.setDestinationPoint("Hải Phòng");
        // routeHnHp.setDistanceKm(120.0);
        // routeHnHp.setEstimatedDuration(120); // 2 tiếng
        // routeHnHp.setSuitableBusType(busTypeGiuongNam);
        // routeRepository.save(routeHnHp);

        // Route routeAB = new Route();
        // routeAB.setDeparturePoint("A");
        // routeAB.setDestinationPoint("B");
        // routeAB.setDistanceKm(100.0);
        // routeAB.setEstimatedDuration(60); // 1 tiếng
        // routeAB.setSuitableBusType(busTypeGiuongNam);
        // routeRepository.save(routeAB);

        // // 7. Khởi tạo Chuyến xe (Trip) - CHUYẾN NÀY SẼ KÍCH HOẠT AI
        // Trip hotTrip = new Trip();
        // hotTrip.setRoute(routeHnHp);
        // hotTrip.setBus(bus1);
        // hotTrip.setDriver(driver1);
        // hotTrip.setDepartureTime(LocalDateTime.now().plusHours(12)); // Chạy sau 12h
        // nữa
        // hotTrip.setTotalSeats(40);
        // hotTrip.setTicketsSold(37); // Tỉ lệ lấp đầy: 37/40 = 92.5% (> 90%)
        // hotTrip.setPrice(new BigDecimal("250000"));
        // hotTrip.setStatus(TripStatus.ACTIVE);
        // tripRepository.save(hotTrip);

        // Trip trip2 = new Trip();
        // trip2.setRoute(routeAB);
        // trip2.setBus(bus2);
        // trip2.setDriver(driver2);
        // trip2.setDepartureTime(LocalDateTime.now().plusHours(12)); // Chạy sau 12h
        // nữa
        // trip2.setTotalSeats(50);
        // trip2.setTicketsSold(49); // Tỉ lệ lấp đầy: 37/40 = 92.5% (> 90%)
        // trip2.setPrice(new BigDecimal("2500000"));
        // trip2.setStatus(TripStatus.ACTIVE);
        // tripRepository.save(trip2);

        ///////////////////////////////////////////////////////////////////////////////////////////
        /// new data for testing
        ///

        // --- 1. BUS TYPES ---
        BusType giuongNam = createBusType("Giường nằm", 40);
        BusType gheNgoi = createBusType("Ghế ngồi", 28);
        BusType limousine = createBusType("Limousine", 9);

        // --- 2. USERS & DRIVERS ---
        // Tài xế cực kỳ rảnh (0h lái)
        Driver driverFresh = createDriver("driver_fresh", "Nguyễn Văn Mới", "A1-111", 2, 0.0);

        // Tài xế đang làm việc nhưng vẫn trong khung giờ cho phép (5h lái)
        Driver driverWorking = createDriver("driver_working", "Trần Văn Chăm", "B2-222", 8, 5.0);

        // Tài xế VI PHẠM (Đã lái 9h/24h) -> AI KHÔNG ĐƯỢC đề xuất ông này
        Driver driverExhausted = createDriver("driver_exhausted", "Võ Văn Đuối", "C3-333", 15, 9.0);

        // Tài xế dự phòng chất lượng cao
        Driver driverPro = createDriver("driver_pro", "Phạm Anh Tài", "D4-444", 12, 1.5);
        // Tài dự phòng không ca làm
        Driver driverFree = createDriver("driver_free", "Nguyễn Văn Rảnh", "A1-222", 6, 0.0);
        Driver driverFree2 = createDriver("driver_free2", "Nguyễn Văn Rảnh2", "A1-333", 6, 0.0);

        // --- 3. BUSES ---
        // Xe sẵn sàng, odo thấp
        Bus busNew = createBus("29A-888.88", "Thaco", giuongNam, BusStatus.READY, 1000.0);
        Bus busNew2 = createBus("29A-999.99", "Vinfast", giuongNam, BusStatus.READY, 100.0);

        // Xe ĐANG CHẠY (Busy) -> AI không được lấy xe này để tăng cường
        Bus busOnRoad = createBus("30F-999.99", "Hyundai", giuongNam, BusStatus.TRAVELING, 5000.0);

        // Xe QUÁ HẠN BẢO TRÌ (Giả sử ngưỡng là 10.000km) -> AI nên cân nhắc loại bỏ
        // hoặc cảnh báo
        Bus busOld = createBus("51G-777.77", "Mercedes", gheNgoi, BusStatus.READY, 15500.0);

        // Xe ĐANG SỬA CHỮA
        Bus busRepair = createBus("15A-111.22", "Samco", gheNgoi, BusStatus.REPAIRING, 8000.0);

        // --- 4. STATIONS & ROUTES ---
        Station hn = createStation("Bến xe Mỹ Đình", "Hà Nội");
        Station hp = createStation("Bến xe Niệm Nghĩa", "Hải Phòng");
        Station dn = createStation("Bến xe Đà Nẵng", "Đà Nẵng");

        Route routeHnHp = createRoute("Hà Nội", "Hải Phòng", 120.0, 120, giuongNam);
        Route routeHnDn = createRoute("Hà Nội", "Đà Nẵng", 750.0, 900, giuongNam);
        Route routeHpDn = createRoute("Hải Phòng", "Đà Nẵng", 1050.0, 900, giuongNam);

        // --- 5. TRIPS (TRƯỜNG HỢP TEST AI) ---

        // CASE 1: Chuyến cực hot (>90% vé) -> AI PHẢI ĐỀ XUẤT TĂNG CƯỜNG
        createTrip(routeHnHp, busNew, driverFresh, 40, 39, "300000", TripStatus.ACTIVE, 10);

        // CASE 2: Chuyến bình thường (50% vé) -> AI KHÔNG ĐỀ XUẤT
        createTrip(routeHnHp, busOld, driverPro, 40, 20, "250000", TripStatus.ACTIVE, 15);

        // CASE 3: Chuyến sắp chạy nhưng tài xế đã lái quá giờ (Test logic an toàn)
        // Dù khách đông nhưng tài xế driverExhausted đã lái 9h, AI nên cảnh báo thay
        // người
        // chưa làm tính năng này
        createTrip(routeHnDn, busOnRoad, driverExhausted, 40, 38, "700000", TripStatus.ACTIVE, 5);

        // CASE 4: Chuyến "Ế" (<20% vé) -> AI có thể đề xuất hủy chuyến hoặc gộp chuyến
        createTrip(routeHpDn, busNew, driverWorking, 40, 5, "250000", TripStatus.ACTIVE, 20);
        createTrip(routeHnDn, busNew, driverWorking, 40, 5, "250000", TripStatus.ACTIVE, 15);

        System.out.println(">> Data prepared!");
        System.out.println(
                ">> data consist of: 1 driver over 8h (duoi), 1 bus over repair (mecsedes), 2 hot trips (hn-ph 10h, hn-dn 5h) và 2 chuyến ế (hn-dn 15h, hp-dn 20h) .");

        // Sửa lại trong DataInitializer.java
        System.out.println(">> DATA INITIALIZED");
        System.out.println(">> AI se som de xuat chuyen tang cuong trong vong 10 giay toi...");
    }

    // --- HELPER METHODS ĐỂ CODE GỌN HƠN ---

    private BusType createBusType(String name, int cap) {
        BusType bt = new BusType();
        bt.setTypeName(name);
        bt.setCapacity(cap);
        return busTypeRepository.save(bt);
    }

    private Driver createDriver(String user, String name, String license, int exp, double hours) {
        User u = new User();
        u.setUsername(user);
        u.setPassword("password");
        u.setFullName(name);
        u.setRole(Role.ROLE_DRIVER);
        userRepository.save(u);

        Driver d = new Driver();
        d.setUser(u);
        d.setLicenseNumber(license);
        d.setExperienceYears(exp);
        d.setTotalDrivingHours24h(hours);
        return driverRepository.save(d);
    }

    private Bus createBus(String plate, String brand, BusType type, BusStatus status, double km) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand(brand);
        b.setBusType(type);
        b.setStatus(status);
        b.setTotalKm(km);
        return busRepository.save(b);
    }

    private Station createStation(String name, String addr) {
        Station s = new Station();
        s.setStationName(name);
        s.setAddress(addr);
        return stationRepository.save(s);
    }

    private Route createRoute(String start, String end, double dist, int duration, BusType type) {
        Route r = new Route();
        r.setDeparturePoint(start);
        r.setDestinationPoint(end);
        r.setDistanceKm(dist);
        r.setEstimatedDuration(duration);
        r.setSuitableBusType(type);
        return routeRepository.save(r);
    }

    private void createTrip(Route r, Bus b, Driver d, int total, int sold, String price, TripStatus status,
            int hoursFromNow) {
        Trip t = new Trip();
        t.setRoute(r);
        t.setBus(b);
        t.setDriver(d);
        t.setTotalSeats(total);
        t.setTicketsSold(sold);
        t.setPrice(new BigDecimal(price));
        t.setStatus(status);
        t.setDepartureTime(LocalDateTime.now().plusHours(hoursFromNow));
        tripRepository.save(t);
    }
}