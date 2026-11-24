package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.RegularTimetable;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.dto.MaintenancePlanDto;
import giang.com.BusManagement.dto.RegularTimetableRunDto;
import giang.com.BusManagement.dto.RegularTimetableViewDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LỊCH THƯỜNG LỆ — route pairs CT6 (QĐ-20, QĐ-22; CM-4, CM-6, CM-14, CM-17…CM-22 trong
 * docs/development/route_pairs_decisions.md).
 *
 * <p>Admin bấm "Tạo lịch" cho N ngày tới; service tạo chuyến chính cho mọi khung (tuyến × ngày ×
 * giờ 06/12/18) của các cặp tuyến có lịch. Mỗi khung:
 * <ol>
 * <li>đã có chuyến chính (không phải tăng cường, ở mọi trạng thái) thì bỏ qua — bấm lại không tạo
 * trùng, chuyến Admin tự tạo hay đã hủy được tôn trọng (CM-18);</li>
 * <li>đã qua giờ, hay chưa có giá vé lịch sử (CM-20), thì bỏ qua;</li>
 * <li>xe đúng loại đang ở bến nhà mà chỉ luật km bảo trì chặn (sắp chạm mốc 90% theo lịch đã xếp) thì được
 * đặt một ngày bảo trì ở bến nhà ({@link TripService#planDueMaintenance}, CT7d, CM-25 M1): ngày đó xe không
 * chạy, từ hôm sau km tính lại từ 0;</li>
 * <li>còn lại thì máy chọn xe và kíp bằng ĐÚNG bộ máy của job 10 giây
 * ({@link TripService#assignRegularTrip}) — xe và người phải đứng ở bến xuất phát (CT5b) — qua
 * cổng kiểm tra, và lưu ACTIVE qua lối form Tạo ({@link TripService#createManualTrip}, CM-14);</li>
 * <li>không ai đứng đúng bến, hay cổng từ chối, thì vẫn tạo chuyến nhưng ở PENDING_APPROVAL, chưa
 * có xe — đúng như job 10 giây khi không phân công được — để Admin chọn tay ở màn Duyệt (CM-17).</li>
 * </ol>
 *
 * <p><b>Thứ tự xếp (CM-19):</b> mọi khung của mọi tuyến xếp chung một trục giờ, sớm trước. Chuyến
 * vừa xếp được giữ chỗ trong context ({@link TripService#reserveRegularTrip}), nên chuyến sau thấy
 * xe đó đã đi đâu: vòng đi–về tự hình thành. Xếp xong tuyến này rồi mới sang tuyến kia thì không
 * chuyến về nào đưa xe trở lại, và bến nhà hết xe sau hai ngày.
 *
 * <p><b>Cặp tuyến (CM-6):</b> chỉ tuyến có đúng một tuyến chiều ngược, và tuyến đó cũng có lịch.
 * Lịch là theo cặp (QĐ-22): thiếu chiều về thì xe đi rồi không có chuyến nào đưa về.
 *
 * <p>Toàn bộ một lần bấm là MỘT transaction: lỗi giữa chừng thì không chuyến nào được lưu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegularTimetableService {

    /** Mặc định tạo trước bằng tầm dự báo, 7 ngày (CM-21). */
    public static final int DEFAULT_DAYS = ForecastService.FORECAST_HORIZON_DAYS;

    /**
     * Trần một lần bấm: 14 ngày. Giữ một lần chạy trong khoảng nửa phút, và giới hạn số ngày mà
     * phép cộng km bảo trì (CM-21) phải nhìn trước. Bấm lại mỗi ngày thì lịch tự gối đầu.
     */
    public static final int MAX_DAYS = 14;

    private final TripService tripService;
    private final RecommendationService recommendationService;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final BusRepository busRepository;

    /** Tuyến có lịch: cùng khoá cấu hình với bộ sinh lịch sử (CM-4). Package-private để test gán thẳng. */
    @Value(RegularTimetable.ROUTE_IDS_PROPERTY)
    List<Long> routeIds;

    // =========================================================================
    // TRANG
    // =========================================================================

    @Transactional(readOnly = true)
    public RegularTimetableViewDto overview() {
        Plan plan = plan();
        Map<Long, Map<Integer, BigDecimal>> prices = recommendationService.loadLatestPrices();
        List<Integer> hours = RegularTimetable.DEPARTURE_HOURS;

        List<RegularTimetableViewDto.PairRow> pairs = new ArrayList<>();
        Set<Long> shown = new HashSet<>();
        for (Route route : plan.routes()) {
            Route reverse = plan.reverseOf().get(route.getId());
            if (!shown.add(route.getId()) || !shown.add(reverse.getId())) {
                continue;
            }
            pairs.add(new RegularTimetableViewDto.PairRow(
                    route.getId(), label(route), reverse.getId(), label(reverse),
                    route.getDistanceKm(), route.getEstimatedDuration(),
                    route.getSuitableBusType() != null ? route.getSuitableBusType().getTypeName() : null,
                    route.getSuitableBusType() != null ? route.getSuitableBusType().getCapacity() : null,
                    pricesOf(prices, route, hours), pricesOf(prices, reverse, hours)));
        }

        // Mỗi ngày tới đã có bao nhiêu khung có chuyến chính — đúng phép đếm mà generate() dùng để bỏ qua.
        LocalDate today = LocalDate.now();
        Map<LocalDate, Set<String>> takenByDay = new HashMap<>();
        for (Object[] row : mainTripDepartures(plan, today.atStartOfDay(), today.plusDays(MAX_DAYS).atStartOfDay())) {
            LocalDateTime departure = (LocalDateTime) row[1];
            takenByDay.computeIfAbsent(departure.toLocalDate(), d -> new HashSet<>())
                    .add(slotKey((Long) row[0], departure));
        }
        int slotsPerDay = plan.routes().size() * hours.size();
        // Ngày bảo trì dự kiến đang ghi trên xe (CT7d) — cả ngày đã qua mà chưa ghi nhận, để Admin thấy.
        List<MaintenancePlanDto> maintenance = busRepository.findAll().stream()
                .filter(bus -> bus.getPlannedMaintenanceDate() != null)
                .sorted(Comparator.comparing(Bus::getPlannedMaintenanceDate).thenComparing(Bus::getLicensePlate))
                .map(bus -> maintenanceRow(bus, bus.getKmSinceLastMaintenance(), !bus.hasPlannedMaintenance(today)))
                .toList();
        List<RegularTimetableViewDto.DayRow> coverage = new ArrayList<>();
        for (int i = 0; i < MAX_DAYS; i++) {
            LocalDate day = today.plusDays(i);
            coverage.add(new RegularTimetableViewDto.DayRow(day,
                    takenByDay.getOrDefault(day, Set.of()).size(), slotsPerDay,
                    maintenance.stream().filter(m -> m.getDate().equals(day)).map(MaintenancePlanDto::getBusPlate)
                            .toList()));
        }
        return new RegularTimetableViewDto(hours, today, DEFAULT_DAYS, MAX_DAYS, slotsPerDay, pairs,
                plan.problems(), coverage, maintenance);
    }

    // =========================================================================
    // TẠO LỊCH
    // =========================================================================

    /**
     * Tạo chuyến chính cho mọi khung của các ngày [from, from + days). Xem javadoc của lớp.
     *
     * @throws IllegalArgumentException khi đầu vào sai (from ở quá khứ, days ngoài 1…MAX_DAYS)
     */
    @Transactional
    public RegularTimetableRunDto generate(LocalDate from, int days) {
        if (from == null) {
            throw new IllegalArgumentException("Hãy chọn ngày bắt đầu.");
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Số ngày phải từ 1 đến " + MAX_DAYS + ".");
        }
        if (from.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày bắt đầu " + from.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " đã qua — lịch chỉ tạo cho hôm nay trở đi.");
        }
        long startedAt = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        Plan plan = plan();

        LocalDateTime spanStart = from.atStartOfDay();
        LocalDateTime spanEnd = from.plusDays(days).atStartOfDay();

        // Mọi khung, xếp chung một trục giờ (CM-19); cùng giờ thì theo id tuyến cho ổn định.
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = from.plusDays(i);
            for (Integer hour : RegularTimetable.DEPARTURE_HOURS) {
                for (Route route : plan.routes()) {
                    slots.add(new Slot(route, day.atTime(hour, 0)));
                }
            }
        }
        slots.sort(Comparator.comparing(Slot::departure).thenComparing(slot -> slot.route().getId()));

        Set<String> taken = new HashSet<>();
        for (Object[] row : mainTripDepartures(plan, spanStart, spanEnd)) {
            taken.add(slotKey((Long) row[0], (LocalDateTime) row[1]));
        }
        Map<Long, Map<Integer, BigDecimal>> prices = recommendationService.loadLatestPrices();
        // Dư một ngày sau cùng: chuyến 18:00 ngày cuối tới nơi lúc 24:00, cộng cửa sổ bận 1 giờ.
        AvailabilityContext ctx = tripService.buildTimetableContext(spanStart, spanEnd.plusDays(1));

        int created = 0;
        int skippedExisting = 0;
        int skippedPast = 0;
        int skippedNoPrice = 0;
        List<RegularTimetableRunDto.PendingRow> pendingRows = new ArrayList<>();
        List<MaintenancePlanDto> maintenancePlanned = new ArrayList<>();
        LocalDate lastDay = from.plusDays(days - 1L);
        for (Slot slot : slots) {
            Route route = slot.route();
            LocalDateTime departure = slot.departure();
            if (!departure.isAfter(now)) {
                skippedPast++;
                continue;
            }
            if (taken.contains(slotKey(route.getId(), departure))) {
                skippedExisting++;
                continue;
            }
            BigDecimal price = prices.getOrDefault(route.getId(), Map.of()).get(departure.getHour());
            if (price == null) {
                skippedNoPrice++;
                continue;
            }

            Trip trip = newTrip(route, departure, price);
            // CT7d (CM-25): xe đúng loại ở bến nhà mà chỉ luật km bảo trì chặn thì được đặt ngày bảo trì trước.
            for (TripService.PlannedMaintenance planned : tripService.planDueMaintenance(trip, lastDay, ctx)) {
                maintenancePlanned.add(maintenanceRow(planned.bus(), planned.kmBefore(), false));
            }
            String reason = assignAndActivate(trip, ctx);
            if (reason == null) {
                created++;
                continue;
            }
            // Không xếp được: vẫn giữ khung bằng một chuyến chờ duyệt chưa có xe (CM-17) — như job
            // 10 giây khi không phân công được. Số ghế tạm = xe chuẩn của tuyến; lúc Admin duyệt,
            // approveTrip() đặt lại theo sức chứa xe được chọn (#26).
            Trip pending = newTrip(route, departure, price);
            pending.setTotalSeats(standardSeats(route));
            tripRepository.save(pending);
            pendingRows.add(new RegularTimetableRunDto.PendingRow(pending.getId(), label(route), departure, reason));
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        log.info("📅 [Lịch thường lệ] {} → {}: {} chuyến mở bán, {} chờ phân công, {} ngày bảo trì, bỏ qua {} đã có / {} đã qua / {} chưa có giá ({} ms)",
                from, lastDay, created, pendingRows.size(), maintenancePlanned.size(), skippedExisting, skippedPast,
                skippedNoPrice, elapsed);
        return new RegularTimetableRunDto(from, lastDay, created, pendingRows.size(),
                skippedExisting, skippedPast, skippedNoPrice, elapsed, pendingRows, plan.problems(), maintenancePlanned);
    }

    /** Một dòng ngày bảo trì dự kiến của xe cho trang (CT7d). */
    private static MaintenancePlanDto maintenanceRow(Bus bus, double km, boolean overdue) {
        String type = bus.getBusType() != null
                ? bus.getBusType().getTypeName() + " " + bus.getBusType().getCapacity() + " chỗ" : null;
        return new MaintenancePlanDto(bus.getId(), bus.getLicensePlate(), type, bus.getPlannedMaintenanceDate(), km,
                bus.getMaintenanceThreshold(), overdue);
    }

    /**
     * Máy chọn xe và kíp (đúng bộ máy của job 10 giây), qua cổng kiểm tra rồi lưu ACTIVE. Trả null
     * khi đã lưu; ngược lại là lý do không xếp được — khi đó trip KHÔNG được lưu.
     *
     * Cổng chạy bản dry-run trước, vì lối lưu (createManualTrip) ném exception khi từ chối, mà một
     * exception đi qua proxy giao dịch sẽ đánh dấu cả lần bấm phải rollback. Qua dry-run thì
     * createManualTrip kiểm lại đúng các luật đó trên cùng dữ liệu, trong cùng transaction.
     */
    private String assignAndActivate(Trip trip, AvailabilityContext ctx) {
        AutoAssignResult assigned = tripService.assignRegularTrip(trip, ctx);
        if (!assigned.isSuccess()) {
            return assigned.getFailureReason();
        }
        trip.setBus(assigned.getBus());
        trip.setDriver(assigned.getDriver());
        trip.setAssistant(assigned.getAssistant());
        trip.getCoDrivers().addAll(assigned.getCoDrivers());
        Integer capacity = TripService.seatCapacityOf(assigned.getBus());
        trip.setTotalSeats(capacity != null ? capacity : standardSeats(trip.getRoute()));

        ValidationResult busGate = tripService.validateBusForTripDryRun(trip.getBus(), trip, null);
        if (!busGate.isValid()) {
            return "Cổng kiểm tra xe từ chối: " + busGate.getFailureReason();
        }
        ValidationResult staffGate = tripService.validateStaffForTripDryRun(trip, null);
        if (!staffGate.isValid()) {
            return "Cổng kiểm tra nhân sự từ chối: " + staffGate.getFailureReason();
        }
        tripService.createManualTrip(trip);
        tripService.reserveRegularTrip(trip, ctx);
        return null;
    }

    // =========================================================================
    // CẶP TUYẾN
    // =========================================================================

    /** Tuyến xếp được lịch (theo thứ tự cấu hình), tuyến chiều ngược của từng tuyến, và lý do của các tuyến bị loại. */
    private record Plan(List<Route> routes, Map<Long, Route> reverseOf, List<String> problems) {
    }

    private record Slot(Route route, LocalDateTime departure) {
    }

    private Plan plan() {
        List<Route> all = routeRepository.findAllWithStations();
        Map<Long, Route> byId = all.stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<Long> configured = routeIds != null ? routeIds : List.of();

        // Lượt 1: mỗi tuyến tự nó — có trong DB, có thời lượng, đúng một tuyến chiều ngược, và tuyến đó có lịch.
        Map<Long, Route> candidates = new LinkedHashMap<>();
        Map<Long, Route> reverseOf = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (Long id : configured) {
            Route route = byId.get(id);
            if (route == null) {
                problems.add("Tuyến #" + id + " không có trong dữ liệu.");
                continue;
            }
            String name = "Tuyến #" + id + " (" + label(route) + ")";
            if (route.getEstimatedDuration() == null || route.getEstimatedDuration() <= 0) {
                problems.add(name + " chưa có thời gian chạy dự kiến.");
                continue;
            }
            List<Route> reverse = RouteService.reverseRoutesOf(route, all);
            if (reverse.isEmpty()) {
                problems.add(name + " không có tuyến chiều ngược.");
                continue;
            }
            if (reverse.size() > 1) {
                problems.add(name + " có " + reverse.size() + " tuyến chiều ngược ("
                        + reverse.stream().map(r -> "#" + r.getId()).collect(Collectors.joining(", "))
                        + ") — không biết ghép với tuyến nào.");
                continue;
            }
            if (!configured.contains(reverse.get(0).getId())) {
                problems.add(name + ": tuyến chiều ngược #" + reverse.get(0).getId()
                        + " không có lịch, nên xe đi rồi không có chuyến nào đưa về.");
                continue;
            }
            candidates.put(route.getId(), route);
            reverseOf.put(route.getId(), reverse.get(0));
        }
        // Lượt 2: lịch là theo CẶP — giữ tuyến khi chiều ngược của nó cũng qua lượt 1. Tuyến 10 có hai
        // tuyến chiều ngược thì bị loại, và tuyến 11 (ngược duy nhất của nó là 10) cũng không chạy một mình.
        List<Route> routes = new ArrayList<>();
        for (Route route : candidates.values()) {
            Route reverse = reverseOf.get(route.getId());
            if (candidates.containsKey(reverse.getId())) {
                routes.add(route);
            } else {
                problems.add("Tuyến #" + route.getId() + " (" + label(route) + "): tuyến chiều ngược #"
                        + reverse.getId() + " không xếp được lịch (xem lý do của nó), nên không chạy một chiều.");
            }
        }
        return new Plan(routes, reverseOf, problems);
    }

    // =========================================================================
    // TIỆN ÍCH
    // =========================================================================

    private List<Object[]> mainTripDepartures(Plan plan, LocalDateTime from, LocalDateTime to) {
        List<Long> ids = plan.routes().stream().map(Route::getId).toList();
        return ids.isEmpty() ? List.of() : tripRepository.findMainTripDeparturesBetween(ids, from, to);
    }

    /** Khung = (tuyến, ngày, giờ): chuyến chính lúc 06:15 vẫn thuộc khung 06:00 (CM-18). */
    private static String slotKey(Long routeId, LocalDateTime departure) {
        return routeId + "@" + departure.toLocalDate() + "@" + departure.getHour();
    }

    private static Trip newTrip(Route route, LocalDateTime departure, BigDecimal price) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));
        trip.setPrice(price);
        return trip;
    }

    /** Số ghế của xe chuẩn (loại xe gợi ý) của tuyến; 0 khi tuyến không quy định loại. */
    private static int standardSeats(Route route) {
        return route.getSuitableBusType() != null ? route.getSuitableBusType().getCapacity() : 0;
    }

    private static List<BigDecimal> pricesOf(Map<Long, Map<Integer, BigDecimal>> prices, Route route,
            List<Integer> hours) {
        Map<Integer, BigDecimal> byHour = prices.getOrDefault(route.getId(), Map.of());
        List<BigDecimal> result = new ArrayList<>();
        for (Integer hour : hours) {
            result.add(byHour.get(hour));
        }
        return result;
    }

    private static String label(Route route) {
        return route.getDeparturePointDisplay() + " → " + route.getDestinationPointDisplay();
    }
}
