package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistorySimulation.RouteProfile;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.HolidayCalendar;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.service.BusService;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * VẬN HÀNH MÔ PHỎNG (route pairs CT8a, CM-23 cách V4) — bước 1 của job đêm. App không bán vé và không ai bấm
 * "Xuất phát / Hoàn thành", nên chuyến thường lệ đã chạy xong vẫn nằm ở trạng thái mở bán: dự báo (chỉ học
 * chuyến COMPLETED) không có thêm quan sát nào, Bảng điều hành đầy chuyến "trễ giờ", và km của xe không bao giờ
 * vào odometer. Bước này "vận hành" thay nhà xe, như bộ sinh lịch sử đã làm cho quá khứ:
 *
 * <ul>
 * <li>Mỗi KHUNG (tuyến × ngày × giờ khung) của các tuyến lịch thường lệ mà mọi chuyến đang bán hay đang chạy đều
 * đã tới nơi: số khách = {@link HistorySimulation#demand} của khung — CÙNG mô hình, CÙNG hạt giống
 * ({@link HistoricalDataBackfill#RANDOM_SEED}) đã sinh lịch sử, tất định theo ngày. Vé lấp chuyến chính trước,
 * phần tràn sang chuyến tăng cường theo giờ đi, mỗi chuyến tối đa số ghế của nó (khung bán hết thì dự báo hiểu
 * là nhu cầu ≥ số ghế, như với lịch sử); không bao giờ thấp hơn số vé đã gõ tay; khách của chuyến đã COMPLETED
 * trong khung được trừ trước.</li>
 * <li>Rồi đúng FSM: ACTIVE → DEPARTED → COMPLETED qua {@link TripService#updateTripStatus} — xe về READY,
 * odometer cộng quãng đường tuyến.</li>
 * <li>Ngày bảo trì dự kiến (CT7d) đã qua: xe đã đứng ở bến nhà cả ngày đó như lịch xếp, nên lần bảo trì được ghi
 * nhận ({@link BusService#recordPlannedService}) đúng thứ tự thời gian giữa các chuyến của xe.</li>
 * <li>Chuyến chờ duyệt đã qua giờ khởi hành (không ai duyệt) → CANCELLED, qua FSM.</li>
 * </ul>
 *
 * Không mô phỏng bão hay hủy chuyến đã lên lịch (giữ nguyên chuỗi vị trí của xe và người). Chuyến ngoài các tuyến
 * lịch thường lệ, hay khởi hành ngoài giờ khung, không bị đụng tới. Số khách là MÔ PHỎNG và phải được nói rõ như
 * vậy khi bảo vệ, như dữ liệu lịch sử. Bật / tắt bằng {@code dispatch.nightly.simulate-operations}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatedOperations {

    private static final List<TripStatus> RUNNING = List.of(TripStatus.ACTIVE, TripStatus.DEPARTED);

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;
    private final TripService tripService;
    private final BusService busService;

    /** Các tuyến của lịch thường lệ — cùng khoá cấu hình với bộ sinh và lịch thật (CM-4). */
    @Value(giang.com.BusManagement.domain.RegularTimetable.ROUTE_IDS_PROPERTY)
    private List<Long> routeIds;

    /**
     * @param closedTrips     số chuyến vừa chuyển COMPLETED
     * @param closedSlots     số khung đã đóng
     * @param tickets         tổng số vé mô phỏng của các chuyến vừa đóng
     * @param cancelledPending số chuyến chờ duyệt đã qua giờ, vừa hủy
     * @param serviced        các lần bảo trì vừa ghi nhận ("biển số dd/MM")
     * @param waitingSlots    khung đã khởi hành nhưng còn chuyến chưa tới nơi — để lần sau
     * @param skipped         lý do không làm gì, hay null
     */
    public record Result(int closedTrips, int closedSlots, int tickets, int cancelledPending, List<String> serviced,
            int waitingSlots, String skipped) {
        static Result skipped(String reason) {
            return new Result(0, 0, 0, 0, List.of(), 0, reason);
        }
    }

    /** Một việc theo thời gian: đóng một khung, hay ghi nhận một ngày bảo trì đã qua. */
    private record Step(LocalDateTime at, List<Trip> slot, Bus serviced) {
    }

    @Transactional
    public Result closeFinishedTrips(LocalDateTime now) {
        return closeFinishedTrips(now, routeIds);
    }

    /** Như trên, cho một danh sách tuyến cho trước (test truyền tuyến của chính nó). */
    @Transactional
    Result closeFinishedTrips(LocalDateTime now, List<Long> routeIdsToRun) {
        LocalDate today = now.toLocalDate();
        if (!HolidayCalendar.covers(today)) {
            return Result.skipped("ngày " + today + " nằm ngoài lịch ngày lễ của mô phỏng");
        }
        List<Route> routes = routeRepository.findAllWithStations().stream()
                .filter(route -> routeIdsToRun != null && routeIdsToRun.contains(route.getId()))
                .toList();
        if (routes.isEmpty()) {
            return Result.skipped("không có tuyến nào của lịch thường lệ");
        }
        List<Long> ids = routes.stream().map(Route::getId).toList();
        LocalDateTime epoch = HistorySimulation.EPOCH.atStartOfDay();

        int cancelledPending = 0;
        for (Trip pending : tripRepository.findPlanTrips(ids, List.of(TripStatus.PENDING_APPROVAL), epoch, now)) {
            tripService.updateTripStatus(pending.getId(), TripStatus.CANCELLED);
            cancelledPending++;
        }

        // Khung → các chuyến đang bán / đang chạy đã khởi hành; chỉ giờ khung mới có nhu cầu mô phỏng.
        Map<String, List<Trip>> slots = new TreeMap<>();
        for (Trip trip : tripRepository.findPlanTrips(ids, RUNNING, epoch, now)) {
            if (HistorySimulation.DEPARTURE_HOURS.contains(trip.getDepartureTime().getHour())) {
                slots.computeIfAbsent(slotKey(trip), key -> new ArrayList<>()).add(trip);
            }
        }
        List<Step> steps = new ArrayList<>();
        int waiting = 0;
        for (List<Trip> slot : slots.values()) {
            if (slot.stream().allMatch(trip -> !arrival(trip).isAfter(now))) {
                steps.add(new Step(slotStart(slot.get(0)), slot, null));
            } else {
                waiting++;
            }
        }
        for (Bus bus : busRepository.findAll()) {
            if (bus.getPlannedMaintenanceDate() != null && bus.getPlannedMaintenanceDate().isBefore(today)) {
                steps.add(new Step(bus.getPlannedMaintenanceDate().atStartOfDay(), null, bus));
            }
        }
        steps.sort(Comparator.comparing(Step::at));

        Map<Long, RouteProfile> profiles = HistoricalDataBackfill.profilesOf(routes);
        HistorySimulation simulation = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED,
                List.copyOf(profiles.values()), today);
        int closedTrips = 0;
        int closedSlots = 0;
        int tickets = 0;
        List<String> serviced = new ArrayList<>();
        for (Step step : steps) {
            if (step.serviced() != null) {
                serviced.add(step.serviced().getLicensePlate() + " "
                        + String.format("%02d/%02d", step.at().getDayOfMonth(), step.at().getMonthValue()));
                busService.recordPlannedService(step.serviced());
                continue;
            }
            tickets += close(step.slot(), profiles, simulation);
            closedTrips += step.slot().size();
            closedSlots++;
        }

        log.info("🌙 [Job đêm] Vận hành mô phỏng: đóng {} chuyến ({} khung, {} vé), hủy {} chuyến chờ duyệt đã qua giờ, "
                + "bảo trì {}, {} khung chờ chuyến tới nơi", closedTrips, closedSlots, tickets, cancelledPending,
                serviced, waiting);
        return new Result(closedTrips, closedSlots, tickets, cancelledPending, serviced, waiting, null);
    }

    /** Đóng một khung: chia vé mô phỏng rồi cho từng chuyến đi hết FSM. Trả tổng vé của các chuyến vừa đóng. */
    private int close(List<Trip> slot, Map<Long, RouteProfile> profiles, HistorySimulation simulation) {
        Trip first = slot.get(0);
        long routeId = first.getRoute().getId();
        LocalDateTime start = slotStart(first);
        int remaining = simulation.demand(profiles.get(routeId), start.toLocalDate(),
                HistorySimulation.DEPARTURE_HOURS.indexOf(start.getHour()));
        for (Trip done : tripRepository.findPlanTrips(List.of(routeId), List.of(TripStatus.COMPLETED), start,
                start.plusHours(1))) {
            remaining -= done.getTicketsSold();
        }

        int tickets = 0;
        List<Trip> ordered = slot.stream()
                .sorted(Comparator.comparing(Trip::isExtraTrip).thenComparing(Trip::getDepartureTime)
                        .thenComparing(Trip::getId))
                .toList();
        for (Trip trip : ordered) {
            int sold = Math.max(trip.getTicketsSold(), Math.min(Math.max(remaining, 0), trip.getTotalSeats()));
            remaining -= sold;
            tickets += sold;
            trip.setTicketsSold(sold);
            if (trip.getStatus() == TripStatus.ACTIVE) {
                tripService.updateTripStatus(trip.getId(), TripStatus.DEPARTED);
            }
            tripService.updateTripStatus(trip.getId(), TripStatus.COMPLETED);
        }
        return tickets;
    }

    private static LocalDateTime arrival(Trip trip) {
        if (trip.getArrivalTimeExpected() != null) {
            return trip.getArrivalTimeExpected();
        }
        Integer minutes = trip.getRoute().getEstimatedDuration();
        return trip.getDepartureTime().plusMinutes(minutes != null ? minutes : 0);
    }

    private static LocalDateTime slotStart(Trip trip) {
        return trip.getDepartureTime().toLocalDate().atTime(trip.getDepartureTime().getHour(), 0);
    }

    /** Khoá sắp được theo thời gian: "yyyy-MM-ddTHH|routeId". */
    private static String slotKey(Trip trip) {
        return slotStart(trip) + "|" + String.format("%06d", trip.getRoute().getId());
    }
}
