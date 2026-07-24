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
 * Ghim quy ước equals/hashCode/toString của entity: so theo ID, không đệ quy,
 * không lazy-load.
 *
 * ====================================================================
 * VÌ SAO TEST NÀY TỒN TẠI
 * ====================================================================
 * Mọi entity trong domain đều mang Lombok @Data, vốn sinh equals/hashCode/
 * toString trên MỌI field. Với entity JPA điều đó sai theo hai cách:
 *
 *   (a) Đệ quy: domain có ba quan hệ hai chiều; nếu cả hai phía cùng nằm trong
 *       ba hàm đó thì chúng gọi vòng lẫn nhau tới StackOverflowError:
 *           User.driver          <-> Driver.user
 *           Route.routeStations  <-> RouteStation.route
 *           Station.routeStations<-> RouteStation.station
 *   (b) Lazy-load: các hàm đó chạm association LAZY → bắn query phụ khi entity
 *       còn managed, hoặc ném LazyInitializationException khi đã detached.
 *
 * Quy ước đã chốt (xem javadoc đầy đủ ở User.driver): equals/hashCode CHỈ theo
 * id — @EqualsAndHashCode(onlyExplicitlyIncluded = true) + @EqualsAndHashCode
 * .Include trên id — và mọi association được @ToString.Exclude. Một hàm chỉ đọc
 * id thì không thể đệ quy (id là Long) và không thể lazy-load; toString không
 * chạm association. Test này canh đúng quy ước đó.
 *
 * ====================================================================
 * VÌ SAO KHÔNG BỎ QUA ĐƯỢC
 * ====================================================================
 * Lỗi này từng nằm im rất lâu vì KHÔNG chỗ nào trong src/main/java gọi
 * hashCode/equals/toString lên entity — code so sánh bằng ID ở mọi nơi. Nghĩa
 * là nếu ai đó lỡ đưa association trở lại equals/hashCode/toString, hoặc thêm
 * một quan hệ mới mà quên Include-id/Exclude-toString, thì KHÔNG có test hay màn
 * hình nào hiện tại phát hiện ra — nó sẽ chỉ nổ vào lần đầu có người viết
 * Set<Trip>, .distinct() hay log thẳng một entity.
 *
 * Vì vậy test bao gồm cả các thao tác Java thường gặp (HashSet, HashMap,
 * distinct) chứ không chỉ gọi hashCode() trần: đó mới là hình dạng thật mà lỗi
 * sẽ xuất hiện, và Phase 9 (Customer Portal — tìm kiếm chuyến, lịch sử đặt vé)
 * là nơi nhiều khả năng viết chúng đầu tiên. Ngoài ra idSemantics_* ghim đúng
 * hợp đồng mới: hai entity bằng nhau khi và chỉ khi cùng id, và hashCode ổn định
 * khi field khác đổi.
 *
 * LƯU Ý cho người thêm entity mới (ví dụ Booking ở Phase 9): thêm
 * @EqualsAndHashCode.Include lên id và @ToString.Exclude lên mọi association,
 * rồi bổ sung nó vào test này.
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
    // Phần 2b — hợp đồng ID-based: entity bằng nhau ⇔ cùng id, và hashCode ổn
    // định khi field khác đổi. Đây là điều mà cách "chỉ exclude association"
    // KHÔNG bảo đảm — canh để không ai lỡ quay lại equals theo-mọi-field.
    // =====================================================================

    @Test
    @DisplayName("equals/hashCode chỉ theo id: cùng id thì bằng, khác id thì khác")
    void idSemantics_equalityFollowsIdOnly() {
        Trip a = new Trip();
        a.setId(100L);
        a.setTicketsSold(10);

        Trip b = new Trip();
        b.setId(100L);
        b.setTicketsSold(999); // field khác hẳn, nhưng cùng id

        Trip c = new Trip();
        c.setId(101L);
        c.setTicketsSold(10); // field giống a, nhưng khác id

        assertTrue(a.equals(b), "cùng id ⇒ bằng nhau bất kể field khác");
        assertTrue(a.hashCode() == b.hashCode(), "cùng id ⇒ cùng hashCode");
        assertTrue(!a.equals(c), "khác id ⇒ không bằng nhau dù field trùng");
    }

    @Test
    @DisplayName("hashCode ỔN ĐỊNH khi field mutable đổi (an toàn trong HashSet)")
    void idSemantics_hashCodeStableAcrossFieldMutation() {
        Trip trip = new Trip();
        trip.setId(100L);
        trip.setTicketsSold(0);

        Set<Trip> set = new HashSet<>();
        set.add(trip);

        // Bán thêm vé SAU khi đã bỏ vào Set — nếu hashCode phụ thuộc ticketsSold
        // (kiểu @Data theo-mọi-field) thì contains() sẽ trả false và trip "lạc".
        trip.setTicketsSold(42);
        assertTrue(set.contains(trip),
                "hashCode phải độc lập với field mutable, nếu không entity lạc khỏi HashSet");
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
