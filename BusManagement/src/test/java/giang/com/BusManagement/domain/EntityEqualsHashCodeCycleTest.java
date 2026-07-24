package giang.com.BusManagement.domain;

import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ghim bất biến: equals/hashCode/toString của entity KHÔNG được đệ quy vô tận.
 *
 * ====================================================================
 * VÌ SAO TEST NÀY TỒN TẠI
 * ====================================================================
 * Mọi entity trong domain đều mang Lombok @Data, tức equals/hashCode/toString
 * được sinh trên MỌI field. Domain có ba quan hệ hai chiều, và nếu cả hai phía
 * cùng nằm trong ba hàm đó thì chúng gọi vòng lẫn nhau tới StackOverflowError:
 *
 *   User.driver          <-> Driver.user
 *   Route.routeStations  <-> RouteStation.route
 *   Station.routeStations<-> RouteStation.station
 *
 * Cách xử lý đã chốt: cắt ở PHÍA NGHỊCH ĐẢO (mappedBy) bằng
 * @EqualsAndHashCode.Exclude + @ToString.Exclude. Cắt một phía là đủ để đứt
 * vòng. Test này canh đúng quy ước đó.
 *
 * ====================================================================
 * VÌ SAO KHÔNG BỎ QUA ĐƯỢC
 * ====================================================================
 * Lỗi này từng nằm im rất lâu vì KHÔNG chỗ nào trong src/main/java gọi
 * hashCode/equals/toString lên entity — code so sánh bằng ID ở mọi nơi. Nghĩa
 * là nếu ai đó lỡ gỡ annotation, hoặc thêm một quan hệ hai chiều mới mà quên
 * exclude, thì KHÔNG có test hay màn hình nào hiện tại phát hiện ra — nó sẽ chỉ
 * nổ vào lần đầu có người viết Set<Trip>, .distinct() hay log thẳng một entity.
 *
 * Vì vậy test bao gồm cả các thao tác Java thường gặp (HashSet, HashMap,
 * distinct) chứ không chỉ gọi hashCode() trần: đó mới là hình dạng thật mà lỗi
 * sẽ xuất hiện, và Phase 9 (Customer Portal — tìm kiếm chuyến, lịch sử đặt vé)
 * là nơi nhiều khả năng viết chúng đầu tiên.
 *
 * LƯU Ý cho người thêm entity mới (ví dụ Booking ở Phase 9): nếu entity mới có
 * quan hệ hai chiều với entity sẵn có, hãy exclude phía mappedBy và bổ sung nó
 * vào test này.
 */
@SpringBootTest
class EntityEqualsHashCodeCycleTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private EntityManager em;

    // =====================================================================
    // Phần 1 — dựng vòng THỦ CÔNG trong bộ nhớ.
    // Đây là phép thử nghiêm ngặt nhất: hai phía được nối vòng tường minh, không
    // phụ thuộc Hibernate nạp gì. Nếu ba hàm vẫn chạy được ở đây thì vòng đã đứt
    // thật, chứ không phải "may mà chưa ai chạm tới".
    // =====================================================================

    @Test
    @DisplayName("User <-> Driver: vòng thủ công không làm hashCode/toString đệ quy")
    void userDriverCycle_doesNotRecurse() {
        User user = new User();
        user.setId(1L);
        user.setUsername("u1");
        user.setRole(Role.ROLE_DRIVER);

        Driver driver = new Driver();
        driver.setUserId(1L);
        driver.setLicenseNumber("L1");
        driver.setIsActive(true);
        driver.setLicenseExpiryDate(LocalDate.now().plusYears(1));

        // Nối vòng tường minh cả hai chiều
        driver.setUser(user);
        user.setDriver(driver);

        assertNotNull(Integer.valueOf(user.hashCode()));
        assertNotNull(Integer.valueOf(driver.hashCode()));
        assertNotNull(user.toString());
        assertNotNull(driver.toString());
        assertTrue(user.equals(user));
        assertTrue(driver.equals(driver));
    }

    @Test
    @DisplayName("Route/Station <-> RouteStation: vòng thủ công không làm hashCode/toString đệ quy")
    void routeStationCycles_doNotRecurse() {
        Station station = new Station();
        station.setId(10L);
        station.setStationName("Bến A");

        Route route = new Route();
        route.setId(20L);
        route.setDistanceKm(100.0);
        route.setEstimatedDuration(120);

        RouteStation link = new RouteStation();
        link.setId(new RouteStationId(route.getId(), station.getId()));
        link.setRoute(route);
        link.setStation(station);
        link.setStopOrder(1);

        // Nối vòng tường minh cả hai chiều, cho cả Route lẫn Station
        route.setRouteStations(new ArrayList<>(List.of(link)));
        station.setRouteStations(new ArrayList<>(List.of(link)));

        assertNotNull(Integer.valueOf(route.hashCode()));
        assertNotNull(Integer.valueOf(station.hashCode()));
        assertNotNull(Integer.valueOf(link.hashCode()));
        assertNotNull(route.toString());
        assertNotNull(station.toString());
        assertNotNull(link.toString());
    }

    @Test
    @DisplayName("Trip chạm được cả ba vòng cùng lúc mà vẫn an toàn")
    void trip_reachingAllCycles_doesNotRecurse() {
        Trip trip = buildFullyWiredTrip();

        assertNotNull(Integer.valueOf(trip.hashCode()));
        assertNotNull(trip.toString());
        assertTrue(trip.equals(trip));
    }

    // =====================================================================
    // Phần 2 — chính các thao tác Java sẽ làm lộ lỗi trong Phase 9.
    // Gọi hashCode() trần là điều kiện cần; đây mới là hình dạng thật.
    // =====================================================================

    @Test
    @DisplayName("HashSet / HashMap / distinct trên entity không nổ (các khuôn Phase 9 sẽ dùng)")
    void collectionOperationsOnEntities_doNotRecurse() {
        Trip trip = buildFullyWiredTrip();
        Driver driver = trip.getDriver();
        Route route = trip.getRoute();

        Set<Trip> tripSet = new HashSet<>();
        tripSet.add(trip);
        assertTrue(tripSet.contains(trip));

        Set<Driver> driverSet = new HashSet<>();
        driverSet.add(driver);
        assertTrue(driverSet.contains(driver));

        Map<Route, Integer> byRoute = new HashMap<>();
        byRoute.put(route, 1);
        assertTrue(byRoute.containsKey(route));

        // Lọc trùng kết quả tìm kiếm — dòng code nhiều khả năng nhất ở Phase 9
        long distinctCount = List.of(trip, trip).stream().distinct().count();
        assertTrue(distinctCount == 1L);
    }

    // =====================================================================
    // Phần 3 — vòng do CHÍNH Hibernate dựng, sau một findById bình thường.
    // Đây là kịch bản đã tái hiện được lỗi trước khi sửa, giữ lại để chứng minh
    // bản sửa xử lý đúng đường đi thực tế chứ không chỉ đường dựng tay.
    // =====================================================================

    @Test
    @Transactional
    @DisplayName("Sau findById, Hibernate tự nối vòng — hashCode vẫn an toàn")
    void cycleBuiltByHibernate_doesNotRecurse() {
        User user = new User();
        user.setUsername("cycle_probe_" + System.nanoTime());
        user.setPassword("x");
        user.setFullName("Cycle Probe");
        user.setRole(Role.ROLE_DRIVER);
        user.setStatus(true);
        userRepository.save(user);

        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNumber("PROBE");
        driver.setExperienceYears(1);
        driver.setTotalDrivingHours24h(0.0);
        driver.setIsActive(true);
        driver.setLicenseExpiryDate(LocalDate.now().plusYears(2));
        driverRepository.save(driver);

        em.flush();
        em.clear();

        Driver reloaded = driverRepository.findById(user.getId()).orElseThrow();

        // Điều kiện KHÔNG rỗng: vòng phải thực sự tồn tại, nếu không test này
        // xanh một cách vô nghĩa (nó sẽ chỉ đang kiểm tra một tham chiếu null).
        assertNotNull(reloaded.getUser());
        assertSame(reloaded, reloaded.getUser().getDriver(),
                "Hibernate phải nối ngược User.driver về chính Driver này — nếu không, "
                        + "test không còn kiểm chứng vòng nào cả");

        assertNotNull(Integer.valueOf(reloaded.hashCode()));
        assertNotNull(Integer.valueOf(reloaded.getUser().hashCode()));
        assertNotNull(reloaded.toString());
        assertNotNull(reloaded.getUser().toString());
    }

    /** Một Trip nối đầy đủ tới cả ba vòng, dùng chung cho nhiều test. */
    private Trip buildFullyWiredTrip() {
        User user = new User();
        user.setId(1L);
        user.setUsername("u1");

        Driver driver = new Driver();
        driver.setUserId(1L);
        driver.setUser(user);
        user.setDriver(driver);

        Station station = new Station();
        station.setId(10L);
        station.setStationName("Bến A");

        Route route = new Route();
        route.setId(20L);

        RouteStation link = new RouteStation();
        link.setId(new RouteStationId(route.getId(), station.getId()));
        link.setRoute(route);
        link.setStation(station);
        link.setStopOrder(1);
        route.setRouteStations(new ArrayList<>(List.of(link)));
        station.setRouteStations(new ArrayList<>(List.of(link)));

        Trip trip = new Trip();
        trip.setId(100L);
        trip.setRoute(route);
        trip.setDriver(driver);
        trip.setAssistant(driver);
        trip.setCoDrivers(new ArrayList<>(List.of(driver)));
        return trip;
    }
}
