package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistoricalDataBackfill.ExistingTrip;
import giang.com.BusManagement.config.HistoricalDataBackfill.Occupied;
import giang.com.BusManagement.config.HistoricalDataBackfill.PlannedSlot;
import giang.com.BusManagement.config.HistoricalDataBackfill.PlannedTrip;
import giang.com.BusManagement.config.HistorySimulation.RouteProfile;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Route;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Nhà xe trong quá khứ" của bộ sinh dữ liệu (HistoricalDataBackfill.planDay, QĐ-10 G2-4):
 * xếp xe và tài xế cho TỪNG ngày từ 01/01/2023 tới 20/10/2026, trên một đội xe dựng y như
 * DB thật (8 xe 22 chỗ, 7 xe 40 chỗ, 5 xe 50 chỗ; 5 tuyến với loại xe phù hợp 22/22/40/40/22;
 * 33 tài xế, trong đó 2 người hết hạn bằng ngày 09/07 và 17/07/2026 như dữ liệu thật).
 *
 * JUnit thuần, không DB. Mỗi quy tắc nghiệp vụ mà bộ sinh hứa "đúng theo cách dựng" được
 * kiểm trên đủ 1.389 ngày, không phải trên vài ngày mẫu.
 */
class HistoricalDataBackfillPlanTest {

    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate EXPIRY_32 = LocalDate.of(2026, 7, 9);
    private static final LocalDate EXPIRY_33 = LocalDate.of(2026, 7, 17);

    private static List<Route> routes;
    private static List<Bus> buses;
    private static List<Driver> drivers;
    private static Map<Long, RouteProfile> profiles;
    private static HistorySimulation simulation;
    private static final Map<LocalDate, List<PlannedSlot>> PLANS = new LinkedHashMap<>();

    @BeforeAll
    static void planEveryDay() {
        BusType seats22 = busType(1, 22);
        BusType seats40 = busType(2, 40);
        BusType seats50 = busType(3, 50);
        buses = new ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            buses.add(bus(id, id <= 8 ? seats22 : id <= 15 ? seats40 : seats50));
        }
        routes = List.of(
                route(1, seats22, HolidayFlow.LEAVING_HUB, 120),
                route(2, seats22, HolidayFlow.LEAVING_HUB, 360),
                route(3, seats40, null, 120),
                route(4, seats40, HolidayFlow.TOWARD_HUB, 90),
                route(5, seats22, HolidayFlow.TOWARD_HUB, 210));
        drivers = new ArrayList<>();
        for (long id = 1; id <= 33; id++) {
            drivers.add(driver(id, id == 32 ? EXPIRY_32 : id == 33 ? EXPIRY_33 : LocalDate.of(2027, 12, 31)));
        }
        profiles = new HashMap<>();
        for (Route route : routes) {
            profiles.put(route.getId(), HistoricalDataBackfill.profileOf(route));
        }
        simulation = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, List.copyOf(profiles.values()),
                LAST_DAY);
        for (LocalDate d = HistorySimulation.EPOCH; !d.isAfter(LAST_DAY); d = d.plusDays(1)) {
            PLANS.put(d, HistoricalDataBackfill.planDay(d, routes, profiles, buses, drivers, Occupied.NONE,
                    simulation));
        }
    }

    @Test
    void everyDay_eachBusAndEachDriverRunsAtMostOneTrip() {
        PLANS.forEach((day, plan) -> {
            Set<Long> busIds = new HashSet<>();
            Set<Long> driverIds = new HashSet<>();
            for (PlannedSlot slot : plan) {
                for (PlannedTrip trip : slot.trips()) {
                    assertTrue(busIds.add(trip.bus().getId()), day + ": xe " + trip.bus().getId() + " chạy hai chuyến");
                    assertTrue(driverIds.add(trip.driver().getUserId()),
                            day + ": tài xế " + trip.driver().getUserId() + " chạy hai chuyến");
                }
            }
        });
    }

    /** Lỗi #32: bộ sinh cũ xếp 69 chuyến cho hai tài xế đã hết hạn bằng. */
    @Test
    void driversWithAnExpiredLicence_neverDriveFromTheirExpiryDate() {
        boolean drove32Before = false;
        boolean drove33Before = false;
        for (Map.Entry<LocalDate, List<PlannedSlot>> entry : PLANS.entrySet()) {
            for (PlannedSlot slot : entry.getValue()) {
                for (PlannedTrip trip : slot.trips()) {
                    assertTrue(trip.driver().isLicenseValid(entry.getKey()),
                            entry.getKey() + ": tài xế " + trip.driver().getUserId() + " hết hạn bằng");
                    drove32Before |= trip.driver().getUserId() == 32;
                    drove33Before |= trip.driver().getUserId() == 33;
                }
            }
        }
        assertTrue(drove32Before && drove33Before, "hai tài xế vẫn chạy bình thường trước ngày hết hạn");
    }

    @Test
    void everySlot_hasItsRegularTrip_andTicketsNeverExceedSeats() {
        PLANS.forEach((day, plan) -> {
            assertEquals(15, plan.size(), day.toString());
            for (PlannedSlot slot : plan) {
                assertFalse(slot.trips().isEmpty(), day + ": khung không có chuyến");
                assertEquals(0, slot.trips().get(0).minute(), "chuyến thường khởi hành giờ chẵn");
                assertFalse(slot.trips().get(0).extra());
                for (PlannedTrip trip : slot.trips()) {
                    assertEquals(HistoricalDataBackfill.capacityOf(trip.bus()), trip.seats(), "số ghế = sức chứa xe");
                    assertTrue(trip.tickets() >= 0 && trip.tickets() <= trip.seats(), day + ": vé vượt ghế");
                }
                assertEquals(Math.min(slot.demand(), slot.seats()), slot.sold(),
                        day + ": vé bán = min(nhu cầu, tổng số ghế)");
            }
        });
    }

    @Test
    void regularTrips_preferTheRouteBusType() {
        PLANS.forEach((day, plan) -> {
            int matching22 = 0;
            for (PlannedSlot slot : plan) {
                int preferred = slot.route().getSuitableBusType().getCapacity();
                int got = slot.trips().get(0).seats();
                if (preferred == 40) {
                    assertEquals(40, got, day + ": tuyến 40 chỗ, có đủ 7 xe 40 chỗ cho 6 khung");
                } else if (got == 22) {
                    matching22++;
                }
            }
            assertTrue(matching22 >= 8, day + ": 8 xe 22 chỗ phải phục vụ 8/9 khung 22 chỗ, được " + matching22);
        });
    }

    @Test
    void extraTrips_comeOnlyFromWishes_onOpenSlots_withinTheSpareFleet() {
        int daysWithExtras = 0;
        for (Map.Entry<LocalDate, List<PlannedSlot>> entry : PLANS.entrySet()) {
            LocalDate day = entry.getKey();
            int extras = 0;
            for (PlannedSlot slot : entry.getValue()) {
                List<PlannedTrip> added = slot.trips().subList(1, slot.trips().size());
                if (!added.isEmpty()) {
                    assertFalse(slot.cancelled(), day + ": khung bị hủy không có chuyến thêm");
                    assertTrue(added.size() <= simulation.wishedExtraTrips(slot.route().getId(), day),
                            day + ": chuyến thêm vượt ý định của nhà xe");
                    for (int i = 0; i < added.size(); i++) {
                        assertTrue(added.get(i).extra());
                        assertEquals(30 + 10 * i, added.get(i).minute());
                    }
                }
                extras += added.size();
            }
            assertTrue(extras <= 5, day + ": " + extras + " chuyến thêm, chỉ có 20 − 15 = 5 xe rảnh");
            daysWithExtras += extras > 0 ? 1 : 0;
        }
        assertTrue(daysWithExtras > 20, "nhà xe có chạy tăng cường quanh các kỳ nghỉ: " + daysWithExtras + " ngày");
    }

    @Test
    void stormClosure_cancelsEverySlotOfTheRouteThatDay() {
        int closedDays = 0;
        for (Map.Entry<LocalDate, List<PlannedSlot>> entry : PLANS.entrySet()) {
            for (Route route : routes) {
                List<PlannedSlot> slots = entry.getValue().stream()
                        .filter(s -> s.route().getId().equals(route.getId())).toList();
                boolean closed = slots.get(0).closed();
                for (PlannedSlot slot : slots) {
                    assertEquals(closed, slot.closed(), "đóng tuyến là cả ngày");
                    if (closed) {
                        assertTrue(slot.cancelled());
                    }
                }
                closedDays += closed ? 1 : 0;
            }
        }
        assertTrue(closedDays > 0, "có ngày đóng tuyến vì bão");
    }

    /**
     * Lỗi #33: xe và người mà chuyến có sẵn đã giữ thì không được xếp thêm chuyến nào hôm đó,
     * kể cả chuyến tăng cường (vốn chọn xe LỚN NHẤT còn rảnh). Lấy đúng ngày nhiều chuyến
     * thêm nhất, và giữ chính những xe, người mà kế hoạch không có chuyến có sẵn đã dùng —
     * nên test không thể đậu suông.
     */
    @Test
    void occupiedBusesAndStaff_areNeverUsedThatDay_notEvenForExtraTrips() {
        LocalDate day = PLANS.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().stream()
                        .mapToInt(s -> s.trips().size() - 1).sum()))
                .orElseThrow().getKey();
        List<PlannedTrip> before = PLANS.get(day).stream().flatMap(s -> s.trips().stream()).toList();
        List<PlannedTrip> extrasBefore = before.stream().filter(PlannedTrip::extra).toList();
        assertTrue(extrasBefore.size() >= 2, day + " phải có chuyến thêm để test có nghĩa");

        // Giữ: xe của hai chuyến thêm và của một chuyến thường; tài xế của ba chuyến đó.
        Set<Long> heldBuses = new HashSet<>();
        Set<Long> heldStaff = new HashSet<>();
        for (PlannedTrip trip : List.of(extrasBefore.get(0), extrasBefore.get(1), before.get(0))) {
            heldBuses.add(trip.bus().getId());
            heldStaff.add(trip.driver().getUserId());
        }

        List<PlannedSlot> plan = HistoricalDataBackfill.planDay(day, routes, profiles, buses, drivers,
                new Occupied(heldBuses, heldStaff), simulation);

        int extras = 0;
        for (PlannedSlot slot : plan) {
            assertFalse(slot.trips().isEmpty(), day + ": vẫn đủ xe cho mọi chuyến thường");
            for (PlannedTrip trip : slot.trips()) {
                assertFalse(heldBuses.contains(trip.bus().getId()), day + ": xe " + trip.bus().getId() + " đang bận");
                assertFalse(heldStaff.contains(trip.driver().getUserId()),
                        day + ": tài xế " + trip.driver().getUserId() + " đang bận");
                extras += trip.extra() ? 1 : 0;
            }
        }
        assertTrue(extras <= 20 - heldBuses.size() - 15, "chuyến thêm chỉ trong số xe CÒN rảnh: " + extras);
    }

    /** Chuyến có sẵn giữ xe và người trong MỌI ngày nó chạm tới — không hơn, không kém. */
    @Test
    void occupiedByDay_holdsEveryCalendarDayATripTouches() {
        LocalDate d16 = LocalDate.of(2026, 7, 16);
        Map<LocalDate, Occupied> occupied = HistoricalDataBackfill.occupiedByDay(List.of(
                // qua đêm: giữ cả hai ngày
                new ExistingTrip(d16.atTime(21, 53), d16.plusDays(1).atTime(3, 53), 2L, Set.of(7L, 8L)),
                // tới đúng 00:00: không chạm sang ngày sau
                new ExistingTrip(d16.plusDays(3).atTime(18, 0), d16.plusDays(4).atStartOfDay(), 5L, Set.of(9L)),
                // chạy hai ngày, năm người trên xe
                new ExistingTrip(d16.plusDays(16).atTime(11, 29), d16.plusDays(17).atTime(17, 29), 7L,
                        Set.of(2L, 3L, 5L, 6L, 7L)),
                // chưa gán xe, không có giờ đến: chỉ giữ người, chỉ ngày khởi hành
                new ExistingTrip(d16.plusDays(20).atTime(10, 0), d16.plusDays(20).atTime(10, 0), null, Set.of(4L))));

        assertEquals(Set.of(2L), occupied.get(d16).busIds());
        assertEquals(Set.of(7L, 8L), occupied.get(d16.plusDays(1)).staffIds());
        assertEquals(Set.of(5L), occupied.get(d16.plusDays(3)).busIds());
        assertFalse(occupied.containsKey(d16.plusDays(4)), "tới đúng 00:00 không giữ ngày sau");
        assertEquals(Set.of(2L, 3L, 5L, 6L, 7L), occupied.get(d16.plusDays(17)).staffIds());
        assertEquals(Set.of(), occupied.get(d16.plusDays(20)).busIds());
        assertEquals(Set.of(4L), occupied.get(d16.plusDays(20)).staffIds());
        assertEquals(6, occupied.size());
    }

    // =====================================================================

    private static BusType busType(int id, int capacity) {
        BusType type = new BusType();
        type.setId(id);
        type.setTypeName("Loại " + capacity);
        type.setCapacity(capacity);
        return type;
    }

    private static Bus bus(long id, BusType type) {
        Bus bus = new Bus();
        bus.setId(id);
        bus.setBusType(type);
        return bus;
    }

    private static Route route(long id, BusType type, HolidayFlow flow, int minutes) {
        Route route = new Route();
        route.setId(id);
        route.setSuitableBusType(type);
        route.setHolidayFlow(flow);
        route.setEstimatedDuration(minutes);
        route.setDistanceKm(100.0);
        return route;
    }

    private static Driver driver(long userId, LocalDate licenceExpiry) {
        Driver driver = new Driver();
        driver.setUserId(userId);
        driver.setIsActive(true);
        driver.setLicenseExpiryDate(licenceExpiry);
        return driver;
    }
}
