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
        BusType seats50 = createBusType("50 seats", 50);
        BusType seat60 = createBusType("60 seats", 60);

        // 2. NHÂN SỰ (Tài xế & Admin)
        createUser("admin", "Admin Hệ Thống", Role.ROLE_ADMIN);

        // Tài xế A: Cực kỳ rảnh (0h lái) -> AI sẽ ưu tiên chọn ông này
        Driver driverFresh = createDriver("driver_fresh", "Nguyễn Văn Rảnh", "B2-111", 5, 0.0);
        Driver driverFresh2 = createDriver("driver_fresh2", "Nguyễn Văn 0h", "B2-112", 4, 0.0);
        Driver driverFresh3 = createDriver("driver_fresh3", "Nguyễn Văn 1h", "B2-113", 3, 1.0);

        // Tài xế B: Sắp chạm ngưỡng (7.5h lái) -> AI sẽ KHÔNG chọn vì chuyến đi thường
        // > 0.5h
        Driver driverBusy = createDriver("driver_busy", "Trần Văn 7.5h", "D-222", 10, 7.5);
        Driver driverBusy2 = createDriver("driver_busy2", "Trần Văn 6.0", "D-221", 10, 6.0);
        Driver driverBusy3 = createDriver("driver_busy3", "Trần Văn 3h", "D-223", 10, 3.0);

        // 3. XE KHÁCH
        // Xe 1: Sẵn sàng, Odo thấp -> AI ưu tiên
        Bus busNew = createBus("29A-111.11", "Thaco1000", limousine, BusStatus.READY, 1000.0);
        Bus busNew2 = createBus("29A-111.12", "Vin100", limousine, BusStatus.READY, 100.0);
        Bus busNew3 = createBus("29A-111.13", "Seat60-100", seat60, BusStatus.READY, 100.0);

        // Xe 2: Sẵn sàng nhưng CẦN BẢO TRÌ (Odo 12000 > Threshold 5000) -> AI sẽ cân
        // nhắc/cảnh báo
        Bus busOld = createBus("29A-222.22", "Hyundai12000", giuongNam, BusStatus.READY, 12000.0);
        Bus busOld2 = createBus("29A-222.23", "Thaco4900", giuongNam, BusStatus.READY, 4900.0);

        // 4. TRẠM & TUYẾN ĐƯỜNG (Sử dụng departurePoint & destinationPoint)
        Station hn = createStation("Bến xe Mỹ Đình", "Hà Nội");
        Station hp = createStation("Bến xe Niệm Nghĩa", "Hải Phòng");
        Station bd = createStation("Bến xe MIEN DONG MOI", "BD");
        Station dn = createStation("Bến xe A", "DN");

        Route routeHnHp = new Route();
        routeHnHp.setDeparturePoint("Hà Nội");
        routeHnHp.setDestinationPoint("Hải Phòng");
        routeHnHp.setDistanceKm(120.0);
        routeHnHp.setEstimatedDuration(120); // 2 tiếng
        routeHnHp.setSuitableBusType(limousine);
        routeRepository.save(routeHnHp);

        Route routeHnBd = new Route();
        routeHnBd.setDeparturePoint("Hà Nội");
        routeHnBd.setDestinationPoint("BD");
        routeHnBd.setDistanceKm(1000.0);
        routeHnBd.setEstimatedDuration(720); // 12 tiếng
        routeHnBd.setSuitableBusType(limousine);
        routeRepository.save(routeHnBd);

        Route routeDnBd = new Route();
        routeDnBd.setDeparturePoint("DN");
        routeDnBd.setDestinationPoint("BD");
        routeDnBd.setDistanceKm(30.0);
        routeDnBd.setEstimatedDuration(50); // 50phut
        routeDnBd.setSuitableBusType(limousine);
        routeRepository.save(routeDnBd);

        Route routeHnDn = new Route();
        routeHnDn.setDeparturePoint("Hà Nội");
        routeHnDn.setDestinationPoint("DN");
        routeHnDn.setDistanceKm(1000.0);
        routeHnDn.setEstimatedDuration(720); // 12 tiếng
        routeHnDn.setSuitableBusType(seat60);
        routeRepository.save(routeHnDn);

        // 5. CÁC CHUYẾN XE ĐỂ TEST AI

        // TEST CASE 1: KÍCH HOẠT AI TĂNG CƯỜNG TỰ ĐỘNG
        // Chuyến này có 37/40 ghế đã bán (92.5%) -> Sau 10s TripService sẽ tự tạo
        // chuyến extra
        createTrip(routeHnHp, busNew, driverFresh, 40, 37, "250000", TripStatus.ACTIVE, 5);
        createTrip(routeDnBd, busNew2, driverFresh2, 50, 49, "25000", TripStatus.ACTIVE, 5);
        createTrip(routeHnBd, busOld2, driverFresh3, 50, 49, "25000", TripStatus.ACTIVE, 5);
        createTrip(routeHnDn, null, null, 50, 49, "25000", TripStatus.ACTIVE, 5);

        // TEST CASE 2: CHUYẾN CHỜ DUYỆT THỦ CÔNG
        // Tạo một chuyến trống để Admin vào phân công trong approve-form.html
        createTrip(routeHnHp, null, null, 40, 0, "250000", TripStatus.PENDING_APPROVAL, 24);

        System.out.println("✅ [Dữ liệu đã sẵn sàng]");
        System.out.println(
                "👉 Hãy chờ 10 giây để AI quét chuyến 'Hà Nội -> Hải Phòng' (92.5% vé) và đề xuất tăng cường.");
    }

    // --- CÁC HÀM TRỢ GIÚP (HELPER) ---

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
        t.setArrivalTimeExpected(LocalDateTime.now().plusHours(hoursFromNow + 2));
        t.setExtraTrip(false);
        tripRepository.save(t);
    }
}