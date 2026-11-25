package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistorySimulation.RouteProfile;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.RouteStationRepository;
import giang.com.BusManagement.repository.StationRepository;
import giang.com.BusManagement.repository.TripRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT8a (CM-23 cách V4) — vận hành mô phỏng đóng các chuyến đã chạy xong bằng ĐÚNG mô hình nhu cầu
 * của bộ sinh lịch sử, qua FSM; chuyến chờ duyệt quá giờ bị hủy; ngày bảo trì đã qua được ghi nhận đúng thứ tự
 * giữa các chuyến của xe. Chủ dự án duyệt 2026-11-25.
 *
 * <p>Bối cảnh: một cặp "Bến nhà SO" ↔ "Bến xa SO", 100 km, 2 giờ, xe 22 chỗ; chỉ cặp này nằm trong danh sách
 * tuyến của lịch thường lệ (truyền thẳng danh sách tuyến). Số khách kỳ vọng của mỗi khung tính lại bằng HistorySimulation
 * với cùng hồ sơ tuyến và cùng hạt giống — không đoán số.
 */
@SpringBootTest
@Transactional
class SimulatedOperationsTest {

    @Autowired private SimulatedOperations simulatedOperations;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private EntityManager em;

    private BusType type;
    private Station home;
    private Route out;
    private Route back;
    private Route outside;
    private List<Long> routeIds;

    @BeforeEach
    void setUp() {
        type = new BusType();
        type.setTypeName("SO 22 " + System.nanoTime());
        type.setCapacity(22);
        type = busTypeRepository.save(type);
        home = station("Bến nhà SO");
        Station far = station("Bến xa SO");
        out = route(home, far, HolidayFlow.LEAVING_HUB);
        back = route(far, home, HolidayFlow.TOWARD_HUB);
        outside = route(home, station("Bến ngoài SO"), HolidayFlow.LEAVING_HUB);
        routeIds = List.of(out.getId(), back.getId());
    }

    /**
     * Khung 06:00 của một ngày vừa qua có chuyến chính (22 ghế) và một chuyến tăng cường 06:30 (40 ghế), cả hai đã tới nơi:
     * vé = số khách mô phỏng của khung, lấp chuyến chính trước rồi tràn sang chuyến thêm, mỗi chuyến tối đa số
     * ghế của nó (hai chuyến khác số ghế, nên với mọi nhu cầu từ 1 tới 61 thứ tự lấp đều lộ ra); cả hai đi
     * ACTIVE → DEPARTED → COMPLETED, xe về READY và odometer cộng 100 km.
     */
    @Test
    void aFinishedSlot_isClosedWithTheSimulatedDemand_mainTripFirst_throughTheStateMachine() {
        LocalDate day = LocalDate.now().minusDays(1);
        for (int ago = 1; ago <= 10; ago++) { // ngày gần nhất mà nhu cầu mô phỏng làm thứ tự lấp lộ ra
            int d = demandOf(out, LocalDate.now().minusDays(ago), 0);
            if (d > 0 && d < 62) {
                day = LocalDate.now().minusDays(ago);
                break;
            }
        }
        Bus busA = bus("SO-A");
        Bus busB = bus("SO-B");
        Trip main = tripRepository.save(trip(out, day.atTime(6, 0), TripStatus.ACTIVE, busA, false));
        Trip bigExtra = trip(out, day.atTime(6, 30), TripStatus.ACTIVE, busB, true);
        bigExtra.setTotalSeats(40);
        Trip extra = tripRepository.save(bigExtra);
        flushAndClear();

        SimulatedOperations.Result result = simulatedOperations.closeFinishedTrips(LocalDateTime.now(), routeIds);
        flushAndClear();

        int demand = demandOf(out, day, 0);
        assertNull(result.skipped());
        assertEquals(2, result.closedTrips());
        assertEquals(1, result.closedSlots());
        Trip closedMain = tripRepository.findById(main.getId()).orElseThrow();
        Trip closedExtra = tripRepository.findById(extra.getId()).orElseThrow();
        assertEquals(TripStatus.COMPLETED, closedMain.getStatus());
        assertEquals(TripStatus.COMPLETED, closedExtra.getStatus());
        assertEquals(Math.min(demand, 22), closedMain.getTicketsSold(), "chuyến chính lấp trước, demand=" + demand);
        assertEquals(Math.min(Math.max(demand - 22, 0), 40), closedExtra.getTicketsSold(), "phần tràn sang chuyến thêm");
        assertTrue(demand > 0 && demand < 62, "tiền đề để thứ tự lấp lộ ra (10 ngày gần nhất): demand=" + demand);
        assertEquals(closedMain.getTicketsSold() + closedExtra.getTicketsSold(), result.tickets());
        Bus after = busRepository.findById(busA.getId()).orElseThrow();
        assertEquals(BusStatus.READY, after.getStatus());
        assertEquals(1100.0, after.getOdometer(), 1e-9, "odometer cộng quãng đường tuyến qua FSM");
    }

    /**
     * Không đụng tới: khung còn chuyến chưa tới nơi (để lần sau), tuyến ngoài lịch thường lệ, giờ ngoài khung.
     * Chuyến chờ duyệt đã qua giờ khởi hành thì bị hủy.
     */
    @Test
    void unfinishedSlots_otherRoutes_andOffSlotHours_areLeftAlone_whilePastPendingTripsAreCancelled() {
        LocalDateTime now = LocalDateTime.now();
        Bus bus = bus("SO-C");
        // Khung 18:00 hôm qua mà chuyến chưa tới nơi (giờ đến kéo dài tới sau now) — không phụ thuộc giờ chạy test.
        Trip late = trip(out, now.toLocalDate().minusDays(1).atTime(18, 0), TripStatus.ACTIVE, bus, false);
        late.setArrivalTimeExpected(now.plusHours(1));
        Trip running = tripRepository.save(late);
        Trip otherRoute = tripRepository.save(trip(outside, now.minusDays(1).withHour(6).withMinute(0), TripStatus.ACTIVE,
                bus, false));
        Trip offHour = tripRepository.save(trip(out, now.minusDays(1).withHour(9).withMinute(15), TripStatus.ACTIVE,
                bus, false));
        Trip pending = tripRepository.save(trip(back, now.minusDays(1).withHour(12).withMinute(0),
                TripStatus.PENDING_APPROVAL, null, false));
        flushAndClear();

        SimulatedOperations.Result result = simulatedOperations.closeFinishedTrips(now, routeIds);
        flushAndClear();

        assertEquals(0, result.closedTrips());
        assertEquals(1, result.cancelledPending());
        assertEquals(TripStatus.ACTIVE, tripRepository.findById(otherRoute.getId()).orElseThrow().getStatus());
        assertEquals(TripStatus.ACTIVE, tripRepository.findById(offHour.getId()).orElseThrow().getStatus());
        assertEquals(TripStatus.CANCELLED, tripRepository.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(TripStatus.ACTIVE, tripRepository.findById(running.getId()).orElseThrow().getStatus());
        assertEquals(1, result.waitingSlots(), "khung có chuyến chưa tới nơi chờ lần sau");
    }

    /**
     * Ngày bảo trì dự kiến hôm kia: chuyến trước ngày đó vào km trước lần bảo trì, chuyến sau ngày đó vào km
     * sau — lần bảo trì được ghi nhận đúng chỗ giữa hai chuyến, và ngày dự kiến được bỏ.
     */
    @Test
    void aPastPlannedMaintenanceDay_isRecordedBetweenTheBusTripsInTimeOrder() {
        LocalDate serviceDay = LocalDate.now().minusDays(2);
        Bus bus = bus("SO-D");
        bus.setPlannedMaintenanceDate(serviceDay);
        busRepository.save(bus);
        tripRepository.save(trip(out, serviceDay.minusDays(1).atTime(6, 0), TripStatus.ACTIVE, bus, false));
        tripRepository.save(trip(back, serviceDay.minusDays(1).atTime(12, 0), TripStatus.ACTIVE, bus, false));
        tripRepository.save(trip(out, serviceDay.plusDays(1).atTime(6, 0), TripStatus.ACTIVE, bus, false));
        flushAndClear();

        SimulatedOperations.Result result = simulatedOperations.closeFinishedTrips(LocalDateTime.now(), routeIds);
        flushAndClear();

        Bus after = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(3, result.closedTrips());
        assertEquals(1, result.serviced().size(), result.serviced().toString());
        assertTrue(result.serviced().get(0).startsWith("SO-D"));
        assertNull(after.getPlannedMaintenanceDate(), "ngày dự kiến đã dùng");
        assertEquals(1300.0, after.getOdometer(), 1e-9);
        assertEquals(1200.0, after.getLastMaintenanceOdometer(), 1e-9, "bảo trì sau hai chuyến trước ngày đó");
        assertEquals(100.0, after.getKmSinceLastMaintenance(), 1e-9, "chỉ chuyến sau ngày bảo trì tính vào km mới");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Map<Long, RouteProfile> profiles;
    private HistorySimulation simulation;

    /** Nhu cầu mô phỏng của một khung — cùng hồ sơ tuyến, cùng hạt giống như bước vận hành; dựng một lần mỗi test. */
    private int demandOf(Route route, LocalDate date, int hourIndex) {
        if (simulation == null) {
            List<Route> routes = routeRepository.findAllWithStations().stream()
                    .filter(r -> routeIds.contains(r.getId())).toList();
            profiles = HistoricalDataBackfill.profilesOf(routes);
            simulation = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, List.copyOf(profiles.values()),
                    LocalDate.now());
        }
        return simulation.demand(profiles.get(route.getId()), date, hourIndex);
    }

    private Trip trip(Route route, LocalDateTime departure, TripStatus status, Bus bus, boolean extra) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));
        t.setStatus(status);
        t.setTotalSeats(22);
        t.setPrice(new BigDecimal("100000"));
        t.setBus(bus);
        t.setExtraTrip(extra);
        return t;
    }

    private Bus bus(String plate) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand("TestBrand");
        b.setBusType(type);
        b.setOdometer(1000.0);
        b.setLastMaintenanceOdometer(1000.0);
        b.setMaintenanceThreshold(100000.0);
        b.setStatus(BusStatus.READY);
        b.setHomeStation(home);
        return busRepository.save(b);
    }

    private Station station(String name) {
        Station s = new Station();
        s.setStationName(name + " " + System.nanoTime());
        return stationRepository.save(s);
    }

    private Route route(Station from, Station to, HolidayFlow flow) {
        Route r = new Route();
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(120);
        r.setSuitableBusType(type);
        r.setHolidayFlow(flow);
        Route saved = routeRepository.save(r);
        stop(saved, from, 1);
        stop(saved, to, 2);
        return saved;
    }

    private void stop(Route route, Station station, int order) {
        RouteStation rs = new RouteStation();
        rs.setId(new RouteStationId(route.getId(), station.getId()));
        rs.setRoute(route);
        rs.setStation(station);
        rs.setStopOrder(order);
        routeStationRepository.save(rs);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
