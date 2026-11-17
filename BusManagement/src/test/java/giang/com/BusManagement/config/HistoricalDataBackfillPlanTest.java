package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistoricalDataBackfill.Dispatcher;
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
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.Station;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Nhà xe trong quá khứ" của bộ sinh dữ liệu (HistoricalDataBackfill.Dispatcher, QĐ-21 (c), (d)):
 * xếp xe và tài xế cho TỪNG ngày từ 01/01/2023 tới 20/10/2026, nối tiếp từ ngày trước, trên ba
 * cặp tuyến quanh Bến xe Miền Đông và một đội xe dựng y như DB thật (8 xe 22 chỗ, 7 xe 40 chỗ,
 * 5 xe 50 chỗ; 33 tài xế, trong đó 2 người hết hạn bằng ngày 09/07 và 17/07/2026 như dữ liệu
 * thật; mọi người ở bến nhà Miền Đông).
 *
 * JUnit thuần, không DB. Mỗi quy tắc nghiệp vụ mà bộ sinh hứa "đúng theo cách dựng" được kiểm
 * trên đủ 1.389 ngày, không phải trên vài ngày mẫu.
 */
class HistoricalDataBackfillPlanTest {

    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate EXPIRY_32 = LocalDate.of(2026, 7, 9);
    private static final LocalDate EXPIRY_33 = LocalDate.of(2026, 7, 17);
    private static final long MIEN_DONG = 3;
    private static final Set<Long> FAR_ENDS = Set.of(4L, 7L, 12L);

    private static List<Route> routes;
    private static List<Bus> buses;
    private static List<Driver> drivers;
    private static Map<Long, RouteProfile> profiles;
    private static HistorySimulation simulation;
    private static final Map<LocalDate, List<PlannedSlot>> PLANS = new LinkedHashMap<>();

    /** Một chuyến đã lên kế hoạch kèm ngày, tuyến và giờ — để kiểm theo từng xe, từng tài xế. */
    private record Run(LocalDate day, PlannedSlot slot, PlannedTrip trip) {

        LocalDateTime departure() {
            return day.atTime(HistorySimulation.DEPARTURE_HOURS.get(slot.hourIndex()), trip.minute());
        }

        LocalDateTime arrival() {
            return departure().plusMinutes(slot.route().getEstimatedDuration());
        }

        long from() {
            return slot.route().getDepartureStation().getId();
        }

        long to() {
            return slot.route().getDestinationStation().getId();
        }
    }

    @BeforeAll
    static void planEveryDay() {
        BusType seats22 = busType(1, 22);
        BusType seats40 = busType(2, 40);
        BusType seats50 = busType(3, 50);
        Station mienDong = station(MIEN_DONG);
        Station daLat = station(4);
        Station canTho = station(7);
        Station vungTau = station(12);
        buses = new ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            buses.add(bus(id, id <= 8 ? seats22 : id <= 15 ? seats40 : seats50, mienDong));
        }
        routes = List.of(
                route(2, seats40, HolidayFlow.LEAVING_HUB, 360, mienDong, daLat),
                route(5, seats50, HolidayFlow.TOWARD_HUB, 210, canTho, mienDong),
                route(10, seats22, HolidayFlow.LEAVING_HUB, 120, mienDong, vungTau),
                route(11, seats22, HolidayFlow.TOWARD_HUB, 120, vungTau, mienDong),
                route(12, seats40, HolidayFlow.TOWARD_HUB, 360, daLat, mienDong),
                route(13, seats50, HolidayFlow.LEAVING_HUB, 210, mienDong, canTho));
        drivers = new ArrayList<>();
        for (long id = 1; id <= 33; id++) {
            drivers.add(driver(id, id == 32 ? EXPIRY_32 : id == 33 ? EXPIRY_33 : LocalDate.of(2027, 12, 31),
                    mienDong));
        }
        profiles = HistoricalDataBackfill.profilesOf(routes);
        simulation = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, List.copyOf(profiles.values()),
                LAST_DAY);
        PLANS.putAll(planAll(Map.of(), LAST_DAY));
    }

    private static Map<LocalDate, List<PlannedSlot>> planAll(Map<LocalDate, Occupied> occupied, LocalDate lastDay) {
        Dispatcher dispatcher = new Dispatcher(routes, profiles, buses, drivers, simulation, occupied);
        Map<LocalDate, List<PlannedSlot>> plans = new LinkedHashMap<>();
        for (LocalDate d = HistorySimulation.EPOCH; !d.isAfter(lastDay); d = d.plusDays(1)) {
            plans.put(d, dispatcher.planDay(d));
        }
        return plans;
    }

    /** Mọi chuyến ĐÃ CHẠY (không bị hủy), theo từng xe hoặc từng tài xế, xếp theo giờ đi. */
    private static Map<Long, List<Run>> runsBy(Map<LocalDate, List<PlannedSlot>> plans, boolean byBus) {
        Map<Long, List<Run>> runs = new HashMap<>();
        plans.forEach((day, plan) -> {
            for (PlannedSlot slot : plan) {
                if (slot.cancelled()) {
                    continue;
                }
                for (PlannedTrip trip : slot.trips()) {
                    long id = byBus ? trip.bus().getId() : trip.driver().getUserId();
                    runs.computeIfAbsent(id, k -> new ArrayList<>()).add(new Run(day, slot, trip));
                }
            }
        });
        runs.values().forEach(list -> list.sort(Comparator.comparing(Run::departure)));
        return runs;
    }

    /**
     * QĐ-21 (c): chuyến sau của một xe xuất phát từ bến mà chuyến trước của nó vừa tới (chuyến
     * đầu tiên từ bến nhà), không chồng giờ, và xe nghỉ ≥ 1 giờ giữa hai chuyến.
     */
    @Test
    void everyBus_startsWhereItsLastTripEnded_andRestsAnHour() {
        Map<Long, List<Run>> byBus = runsBy(PLANS, true);
        assertEquals(20, byBus.size(), "cả 20 xe đều có chạy");
        byBus.forEach((busId, runs) -> {
            assertEquals(MIEN_DONG, runs.get(0).from(), "xe " + busId + ": chuyến đầu từ bến nhà");
            for (int i = 1; i < runs.size(); i++) {
                Run before = runs.get(i - 1);
                Run next = runs.get(i);
                assertEquals(before.to(), next.from(), "xe " + busId + " " + next.departure() + ": không ở đúng bến");
                assertFalse(before.arrival().plus(Duration.ofHours(1)).isAfter(next.departure()),
                        "xe " + busId + " " + next.departure() + ": nghỉ chưa đủ 1 giờ");
            }
        });
    }

    /**
     * QĐ-21 (c): tài xế cũng ở đúng bến, nghỉ ≥ 30 phút, và lái ≤ 8 giờ trong MỖI ngày lịch —
     * chuyến qua đêm tách tại 00:00. Vòng xe phải thật sự xảy ra: có tài xế lái hai chuyến
     * trong một ngày (điều bộ sinh cũ cấm).
     */
    @Test
    void everyDriver_isAtTheRightStation_restsHalfAnHour_andDrivesAtMostEightHoursADay() {
        boolean someoneDroveTwiceADay = false;
        for (Map.Entry<Long, List<Run>> entry : runsBy(PLANS, false).entrySet()) {
            List<Run> runs = entry.getValue();
            Map<LocalDate, Integer> minutes = new HashMap<>();
            Map<LocalDate, Integer> trips = new HashMap<>();
            assertEquals(MIEN_DONG, runs.get(0).from(), "tài xế " + entry.getKey() + ": chuyến đầu từ bến nhà");
            for (int i = 0; i < runs.size(); i++) {
                Run run = runs.get(i);
                if (i > 0) {
                    Run before = runs.get(i - 1);
                    assertEquals(before.to(), run.from(), "tài xế " + entry.getKey() + " " + run.departure());
                    assertFalse(before.arrival().plusMinutes(30).isAfter(run.departure()),
                            "tài xế " + entry.getKey() + " " + run.departure() + ": nghỉ chưa đủ 30 phút");
                }
                HistoricalDataBackfill.minutesByDay(run.departure(), run.arrival())
                        .forEach((d, m) -> minutes.merge(d, m, Integer::sum));
                trips.merge(run.day(), 1, Integer::sum);
            }
            minutes.forEach((d, m) -> assertTrue(m <= 480, "tài xế " + entry.getKey() + " lái " + m + " phút ngày " + d));
            someoneDroveTwiceADay |= trips.values().stream().anyMatch(n -> n >= 2);
        }
        assertTrue(someoneDroveTwiceADay, "có tài xế chạy đi–về trong một ngày");
    }

    @Test
    void minutesByDay_splitsAnOvernightTripAtMidnight() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertEquals(Map.of(d, 310, d.plusDays(1), 50),
                HistoricalDataBackfill.minutesByDay(d.atTime(18, 50), d.plusDays(1).atTime(0, 50)));
        assertEquals(Map.of(d, 360), HistoricalDataBackfill.minutesByDay(d.atTime(18, 0), d.plusDays(1).atStartOfDay()));
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

    /**
     * Chuyến đầu của khung là chuyến thường, chuyến sau là chuyến thêm ở phút 30/40/50; vé lấp
     * chuyến thường trước, không vượt ghế, tổng vé = min(nhu cầu, tổng số ghế).
     */
    @Test
    void everySlot_regularFirst_extrasAtTheirMinutes_andTicketsNeverExceedSeats() {
        PLANS.forEach((day, plan) -> {
            assertEquals(18, plan.size(), day.toString());
            for (PlannedSlot slot : plan) {
                if (slot.trips().isEmpty()) {
                    continue;
                }
                assertFalse(slot.trips().get(0).extra());
                Set<Integer> minutes = new HashSet<>();
                for (PlannedTrip trip : slot.trips()) {
                    assertTrue(minutes.add(trip.minute()), day + ": hai chuyến cùng phút");
                    assertTrue(trip.minute() == 0 || List.of(30, 40, 50).contains(trip.minute()));
                    assertEquals(HistoricalDataBackfill.capacityOf(trip.bus()), trip.seats(), "số ghế = sức chứa xe");
                    assertTrue(trip.tickets() >= 0 && trip.tickets() <= trip.seats(), day + ": vé vượt ghế");
                }
                assertTrue(slot.trips().size() <= 4, day + ": tối đa 3 chuyến thêm");
                assertEquals(Math.min(slot.demand(), slot.seats()), slot.sold(),
                        day + ": vé bán = min(nhu cầu, tổng số ghế)");
            }
        });
    }

    /**
     * Lịch thường lệ chạy đủ khi vòng xe đã hình thành (sau 3 ngày đầu, lúc xe còn đang tản ra
     * bến xa): khung chiều ĐI từ Miền Đông không bao giờ thiếu xe. Khung chiều VỀ chỉ thiếu xe
     * khi chuyến chiều đi đáng lẽ đưa xe lên đã bị hủy — chuyến hủy không chạy nên xe không lên
     * — vì thế số khung chiều về thiếu xe không vượt số khung chiều đi bị hủy của cặp. Bộ dự báo
     * chỉ đọc chuyến COMPLETED, nên khung thiếu xe cũng như khung bị hủy: không có quan sát.
     */
    @Test
    void regularTimetable_isServed_exceptWhereTheTripBringingTheBusWasCancelled() {
        Map<Long, Integer> unservedByRoute = new HashMap<>();
        Map<Long, Integer> cancelledByRoute = new HashMap<>();
        for (Map.Entry<LocalDate, List<PlannedSlot>> entry : PLANS.entrySet()) {
            if (entry.getKey().isBefore(HistorySimulation.EPOCH.plusDays(3))) {
                continue;
            }
            for (PlannedSlot slot : entry.getValue()) {
                long routeId = slot.route().getId();
                if (slot.trips().isEmpty()) {
                    assertTrue(FAR_ENDS.contains(slot.route().getDepartureStation().getId()),
                            entry.getKey() + ": khung chiều đi tuyến " + routeId + " thiếu xe");
                    unservedByRoute.merge(routeId, 1, Integer::sum);
                }
                cancelledByRoute.merge(routeId, slot.cancelled() ? 1 : 0, Integer::sum);
            }
        }
        for (Route route : routes) {
            if (FAR_ENDS.contains(route.getDepartureStation().getId())) {
                long outbound = HistoricalDataBackfill.partnerOf(route, routes).getId();
                int unserved = unservedByRoute.getOrDefault(route.getId(), 0);
                assertTrue(unserved <= cancelledByRoute.get(outbound),
                        "tuyến " + route.getId() + ": " + unserved + " khung thiếu xe, chiều đi chỉ hủy "
                                + cancelledByRoute.get(outbound));
            }
        }
    }

    @Test
    void regularTrips_mostlyGetTheRouteBusType() {
        int matching = 0;
        int served = 0;
        for (List<PlannedSlot> plan : PLANS.values()) {
            for (PlannedSlot slot : plan) {
                if (!slot.trips().isEmpty()) {
                    served++;
                    matching += slot.trips().get(0).seats() == slot.route().getSuitableBusType().getCapacity() ? 1 : 0;
                }
            }
        }
        assertTrue(matching >= served * 0.95, "đúng loại xe: " + matching + "/" + served);
    }

    /**
     * QĐ-21 (d), H1: xe tăng cường tới bến xa thì chuyến kế tiếp của nó là chuyến về Miền Đông.
     * Khung bị hủy không có chuyến thêm. Có chạy tăng cường quanh các kỳ nghỉ.
     */
    @Test
    void extraTrips_neverOnCancelledSlots_andEveryBusSentFarComesStraightBack() {
        int daysWithExtras = 0;
        for (Map.Entry<LocalDate, List<PlannedSlot>> entry : PLANS.entrySet()) {
            boolean any = false;
            for (PlannedSlot slot : entry.getValue()) {
                if (slot.trips().size() > 1) {
                    assertFalse(slot.cancelled(), entry.getKey() + ": khung bị hủy không có chuyến thêm");
                    any = true;
                }
            }
            daysWithExtras += any ? 1 : 0;
        }
        assertTrue(daysWithExtras > 20, "nhà xe có chạy tăng cường quanh các kỳ nghỉ: " + daysWithExtras + " ngày");

        runsBy(PLANS, true).forEach((busId, runs) -> {
            for (int i = 0; i + 1 < runs.size(); i++) {
                Run run = runs.get(i);
                if (run.trip().extra() && FAR_ENDS.contains(run.to())) {
                    Run next = runs.get(i + 1);
                    assertEquals(MIEN_DONG, next.to(), "xe " + busId + " " + run.departure() + ": không về ngay");
                    assertTrue(next.trip().extra() || next.trip().minute() != 0,
                            "xe " + busId + " " + next.departure() + ": lượt về là chuyến thêm (hoặc được đôn lên)");
                }
            }
        });
    }

    /**
     * QĐ-21 (d): chiều về cần thêm xe thì xe được gửi lên trước. Trong những ngày nhà xe muốn
     * thêm chuyến chiều về, khung chiều về có chuyến thêm chở khách thật.
     */
    @Test
    void returnWaveExtras_areFedBySendingBusesUpAhead() {
        int carrying = 0;
        for (Map.Entry<LocalDate, List<PlannedSlot>> entry : PLANS.entrySet()) {
            for (PlannedSlot slot : entry.getValue()) {
                if (FAR_ENDS.contains(slot.route().getDepartureStation().getId())
                        && simulation.wishedExtraTrips(slot.route().getId(), entry.getKey()) > 0) {
                    carrying += (int) slot.trips().stream().filter(t -> t.extra() && t.tickets() > 0).count();
                }
            }
        }
        assertTrue(carrying > 50, "chuyến thêm chiều về có chở khách: " + carrying);
    }

    /**
     * Số xe đứng ở mỗi bến xa lúc nửa đêm không trôi theo năm tháng: chuyến hủy ở bến xa không
     * để lại xe dư (xe về ngay), nên cuối mỗi năm mỗi bến xa chỉ còn vài xe của vòng thường lệ.
     */
    @Test
    void busesParkedAtFarEnds_doNotDriftOverTheYears() {
        Map<Long, List<Run>> byBus = runsBy(PLANS, true);
        for (int year = 2023; year <= 2026; year++) {
            LocalDateTime midnight = (year == 2026 ? LAST_DAY : LocalDate.of(year, 12, 31)).atTime(23, 59);
            Map<Long, Integer> parked = new HashMap<>();
            for (List<Run> runs : byBus.values()) {
                Run last = null;
                for (Run run : runs) {
                    if (!run.arrival().isAfter(midnight)) {
                        last = run;
                    }
                }
                if (last != null && FAR_ENDS.contains(last.to())) {
                    parked.merge(last.to(), 1, Integer::sum);
                }
            }
            for (long far : FAR_ENDS) {
                assertTrue(parked.getOrDefault(far, 0) <= 4, year + ": bến " + far + " có " + parked.get(far) + " xe");
            }
        }
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
     * Lỗi #33: xe và người mà chuyến có sẵn đã giữ thì không được xếp chuyến nào hôm đó, kể cả
     * chuyến tăng cường. Lấy ngày nhiều chuyến thêm nhất năm 2023, giữ chính những xe, người
     * mà kế hoạch không có chuyến có sẵn đã dùng hôm đó — nên test không thể đậu suông.
     */
    @Test
    void occupiedBusesAndStaff_areNeverUsedThatDay_notEvenForExtraTrips() {
        LocalDate day = PLANS.entrySet().stream()
                .filter(e -> e.getKey().getYear() == 2023)
                .max(Comparator.comparingInt(e -> e.getValue().stream().mapToInt(s -> Math.max(0, s.trips().size() - 1)).sum()))
                .orElseThrow().getKey();
        List<PlannedTrip> before = PLANS.get(day).stream().flatMap(s -> s.trips().stream()).toList();
        List<PlannedTrip> extrasBefore = before.stream().filter(PlannedTrip::extra).toList();
        assertTrue(extrasBefore.size() >= 2, day + " phải có chuyến thêm để test có nghĩa");

        Set<Long> heldBuses = new HashSet<>();
        Set<Long> heldStaff = new HashSet<>();
        for (PlannedTrip trip : List.of(extrasBefore.get(0), extrasBefore.get(1), before.get(0))) {
            heldBuses.add(trip.bus().getId());
            heldStaff.add(trip.driver().getUserId());
        }
        Map<LocalDate, List<PlannedSlot>> replanned = planAll(Map.of(day, new Occupied(heldBuses, heldStaff)), day);

        int trips = 0;
        for (PlannedSlot slot : replanned.get(day)) {
            for (PlannedTrip trip : slot.trips()) {
                assertFalse(heldBuses.contains(trip.bus().getId()), day + ": xe " + trip.bus().getId() + " đang bận");
                assertFalse(heldStaff.contains(trip.driver().getUserId()),
                        day + ": tài xế " + trip.driver().getUserId() + " đang bận");
                trips++;
            }
        }
        assertTrue(trips > 0, "vẫn có chuyến hôm đó");
        // Lượt hôm trước vắt sang hôm nay cũng không được dùng xe hay người đang bận.
        for (PlannedSlot slot : replanned.get(day.minusDays(1))) {
            for (PlannedTrip trip : slot.trips()) {
                LocalDateTime end = day.minusDays(1).atTime(HistorySimulation.DEPARTURE_HOURS.get(slot.hourIndex()),
                        trip.minute()).plusMinutes(slot.route().getEstimatedDuration());
                if (end.isAfter(day.atStartOfDay()) && !slot.cancelled()) {
                    assertFalse(heldBuses.contains(trip.bus().getId()), "chuyến qua đêm vào ngày bị giữ");
                }
            }
        }
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

    /** Hai chiều của một cặp nhận ra nhau qua bến đi / bến đến đổi chỗ (QĐ-21 (a)). */
    @Test
    void profiles_knowTheReverseRouteOfEachPair() {
        assertEquals(12L, profiles.get(2L).partnerRouteId());
        assertEquals(2L, profiles.get(12L).partnerRouteId());
        assertEquals(13L, profiles.get(5L).partnerRouteId());
        assertEquals(11L, profiles.get(10L).partnerRouteId());
        assertEquals(2L, profiles.get(12L).pairKey());
        assertNotNull(HistoricalDataBackfill.partnerOf(routes.get(0), routes));
    }

    // =====================================================================

    private static BusType busType(int id, int capacity) {
        BusType type = new BusType();
        type.setId(id);
        type.setTypeName("Loại " + capacity);
        type.setCapacity(capacity);
        return type;
    }

    private static Station station(long id) {
        Station station = new Station();
        station.setId(id);
        station.setStationName("Bến " + id);
        return station;
    }

    private static Bus bus(long id, BusType type, Station home) {
        Bus bus = new Bus();
        bus.setId(id);
        bus.setBusType(type);
        bus.setHomeStation(home);
        return bus;
    }

    private static Route route(long id, BusType type, HolidayFlow flow, int minutes, Station from, Station to) {
        Route route = new Route();
        route.setId(id);
        route.setSuitableBusType(type);
        route.setHolidayFlow(flow);
        route.setEstimatedDuration(minutes);
        route.setDistanceKm(100.0);
        route.setRouteStations(List.of(stop(route, from, 1), stop(route, to, 2)));
        return route;
    }

    private static RouteStation stop(Route route, Station station, int order) {
        RouteStation rs = new RouteStation();
        rs.setRoute(route);
        rs.setStation(station);
        rs.setStopOrder(order);
        return rs;
    }

    private static Driver driver(long userId, LocalDate licenceExpiry, Station home) {
        Driver driver = new Driver();
        driver.setUserId(userId);
        driver.setIsActive(true);
        driver.setLicenseExpiryDate(licenceExpiry);
        driver.setHomeStation(home);
        return driver;
    }
}
