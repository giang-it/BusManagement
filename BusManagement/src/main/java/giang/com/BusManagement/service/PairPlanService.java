package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.RegularTimetable;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastPairGroupDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.LoopLegKind;
import giang.com.BusManagement.dto.PairPlanViewDto;
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationStatus;
import giang.com.BusManagement.dto.RecommendationViewDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * KẾ HOẠCH CẶP TUYẾN — route pairs CT7 (QĐ-22 mức M2; CM-9: trang mới trong nhóm phân tích, chủ dự án
 * duyệt 2026-11-22).
 *
 * <p>Trả lời câu hỏi của thầy hướng dẫn: <b>"nhìn vào là thấy chiều nào được tăng cường, xe lấy từ
 * đâu, đi rồi về thế nào, cả vòng lãi bao nhiêu"</b>. Mỗi cặp một bảng cho một ngày: hai chiều cạnh nhau
 * ở mỗi giờ chạy thường lệ, mỗi xe một hàng, chuyến thêm của màn Đề xuất có dấu ★ cùng lượt đi kèm.
 *
 * <p><b>Không tính gì mới.</b> Trang chỉ SẮP XẾP LẠI ba thứ đã có:
 * <ul>
 *   <li>chuyến đã có trong hệ thống (thường lệ, tăng cường, tạo tay) — đọc DB;</li>
 *   <li>kết quả của màn Đề xuất ({@link RecommendationService#buildRecommendations}) — các thẻ, xe và
 *       lượt đi kèm đã giữ chỗ — gọi đúng một lần, nên hai màn luôn cùng một kế hoạch;</li>
 *   <li>dự báo của từng khung ({@link ForecastService#buildForecast}, có cache).</li>
 * </ul>
 * Chỉ đọc, không tạo chuyến. Khác "Bảng điều hành" (/admin/dispatch), là màn chuyển trạng thái chuyến.
 *
 * <p>Cặp tuyến: các tuyến có lịch thường lệ ({@link RegularTimetable}, cùng khoá cấu hình với bộ sinh
 * lịch sử), ghép bằng đảo bến như CM-6. Chiều "đi" là chiều rời bến nhà của đội xe (QĐ-23).
 */
@Service
@RequiredArgsConstructor
public class PairPlanService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_DAY = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final RecommendationService recommendationService;
    private final ForecastService forecastService;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final BusRepository busRepository;

    /** Tuyến có lịch: cùng khoá cấu hình với lịch thường lệ và bộ sinh lịch sử (CM-4). Package-private để test gán. */
    @Value(RegularTimetable.ROUTE_IDS_PROPERTY)
    List<Long> routeIds;

    /** Một cặp: out rời bến nhà, back về bến nhà. */
    record Pair(Route out, Route back) {
        boolean has(Long routeId) {
            return Objects.equals(out.getId(), routeId) || Objects.equals(back.getId(), routeId);
        }

        Route other(Long routeId) {
            return Objects.equals(out.getId(), routeId) ? back : out;
        }
    }

    /** Một lượt trong ngày, trước khi xếp vào bảng. plate null = chuyến chưa có xe. */
    private record Leg(String plate, String kind, Route route, LocalDateTime departure, String status,
            Double passengers, String note) {
    }

    // =========================================================================
    // TRANG KẾ HOẠCH
    // =========================================================================

    @Transactional(readOnly = true)
    public PairPlanViewDto build(LocalDate requested) {
        DemandForecastViewDto forecast = forecastService.buildForecast();
        RecommendationViewDto recommendation = recommendationService.buildRecommendations();
        List<Route> all = routeRepository.findAllWithStations();
        Map<Long, Route> byId = all.stream().collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        List<String> problems = new ArrayList<>();
        List<Pair> pairs = pairs(all, homeStations(), routeIds != null ? routeIds : List.of(), problems);

        PairPlanViewDto view = new PairPlanViewDto();
        view.setHistoryEnd(forecast.getHistoryEnd());
        view.setHistoryStaleDays(forecast.getHistoryStaleDays());
        view.setForecastFrom(forecast.getForecastFrom());
        view.setHorizonDays(forecast.getHorizonDays());
        view.setHours(RegularTimetable.DEPARTURE_HOURS);
        view.setProblems(problems);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < forecast.getHorizonDays(); i++) {
            days.add(forecast.getForecastFrom().plusDays(i));
        }
        view.setDays(days);
        LocalDate selected = requested != null && days.contains(requested) ? requested : forecast.getForecastFrom();
        view.setSelectedDate(selected);

        List<RecommendationCardDto> cards = recommendation.getCards();
        for (Pair pair : pairs) {
            view.getOverview().add(week(pair, days, cards));
        }

        // Mọi lượt của ngày đang xem, mọi cặp: cần cho "tối nay xe ngủ ở đâu" của từng xe.
        List<Long> pairRouteIds = pairs.stream().flatMap(p -> java.util.stream.Stream.of(p.out().getId(),
                p.back().getId())).toList();
        List<Trip> trips = pairRouteIds.isEmpty() ? List.of()
                : tripRepository.findPlanTrips(pairRouteIds, RecommendationService.SLOT_STATUSES,
                        selected.atStartOfDay(), selected.plusDays(1).atStartOfDay());
        List<Leg> legs = legsOf(trips, cards, byId, selected);
        Map<String, Leg> lastOfBus = new HashMap<>();
        for (Leg leg : legs) {
            if (leg.plate() != null) {
                lastOfBus.merge(leg.plate(), leg, (a, b) -> b.departure().isAfter(a.departure()) ? b : a);
            }
        }

        Map<String, ForecastPointDto> points = new HashMap<>();
        Map<Long, Integer> standardSeats = new HashMap<>();
        for (ForecastSeriesDto series : forecast.getSeries()) {
            standardSeats.put(series.getRouteId(), series.getStandardSeats());
            for (ForecastPointDto point : series.getPreview()) {
                points.put(LoopEconomics.slotKey(series.getRouteId(), point.getDate(), series.getDepartureHour()), point);
            }
        }

        for (Pair pair : pairs) {
            view.getPairs().add(day(pair, selected, trips, legs, lastOfBus, cards, points, standardSeats, byId));
        }
        return view;
    }

    /** Tổng quan một cặp: chuyến thêm ★ và chuyến thêm chưa xếp được của từng ngày. */
    private PairPlanViewDto.PairWeek week(Pair pair, List<LocalDate> days, List<RecommendationCardDto> cards) {
        PairPlanViewDto.PairWeek week = new PairPlanViewDto.PairWeek();
        week.setPairLabel(pairLabel(pair));
        for (LocalDate date : days) {
            int extras = 0;
            int unserved = 0;
            BigDecimal profit = null;
            for (RecommendationCardDto card : cards) {
                if (!pair.has(card.getRouteId()) || !date.equals(card.getDate())) {
                    continue;
                }
                if (card.getStatus() == RecommendationStatus.RECOMMENDED) {
                    extras++;
                    profit = add(profit, card.getEstimatedProfit());
                } else {
                    unserved++;
                }
            }
            week.getDays().add(new PairPlanViewDto.DayCount(date, extras, unserved, profit));
        }
        return week;
    }

    /** Bảng của một cặp cho một ngày. */
    private PairPlanViewDto.PairDay day(Pair pair, LocalDate date, List<Trip> trips, List<Leg> legs,
            Map<String, Leg> lastOfBus, List<RecommendationCardDto> cards, Map<String, ForecastPointDto> points,
            Map<Long, Integer> standardSeats, Map<Long, Route> byId) {
        PairPlanViewDto.PairDay day = new PairPlanViewDto.PairDay();
        Route out = pair.out();
        day.setPairLabel(pairLabel(pair));
        day.setOutLabel(label(out));
        day.setBackLabel(label(pair.back()));
        day.setDistanceKm(out.getDistanceKm());
        day.setDurationMinutes(out.getEstimatedDuration());
        if (out.getSuitableBusType() != null) {
            day.setBusTypeLabel(out.getSuitableBusType().getTypeName() + " " + out.getSuitableBusType().getCapacity()
                    + " chỗ");
        }
        day.setScheduled(trips.stream().anyMatch(t -> pair.has(t.getRoute().getId())));

        // ── Hai chiều ở mỗi giờ ──
        for (Integer hour : RegularTimetable.DEPARTURE_HOURS) {
            day.getSlots().add(new PairPlanViewDto.SlotPair(hour,
                    slot(out, date, hour, trips, cards, points, standardSeats),
                    slot(pair.back(), date, hour, trips, cards, points, standardSeats)));
        }

        // ── Mỗi xe một hàng ──
        Map<String, List<Leg>> byPlate = new LinkedHashMap<>();
        List<List<Leg>> withoutBus = new ArrayList<>();
        for (Leg leg : legs) {
            if (!pair.has(leg.route().getId())) {
                continue;
            }
            if (leg.plate() == null) {
                withoutBus.add(List.of(leg));
            } else {
                byPlate.computeIfAbsent(leg.plate(), p -> new ArrayList<>()).add(leg);
            }
        }
        List<PairPlanViewDto.BusRow> regular = new ArrayList<>();
        List<PairPlanViewDto.BusRow> extra = new ArrayList<>();
        for (Map.Entry<String, List<Leg>> entry : byPlate.entrySet()) {
            PairPlanViewDto.BusRow row = row(entry.getKey(), entry.getValue(), pair, lastOfBus.get(entry.getKey()));
            (row.isExtra() ? extra : regular).add(row);
        }
        day.getRows().addAll(regular);
        day.getRows().addAll(extra);
        for (List<Leg> single : withoutBus) {
            day.getRows().add(row(null, single, pair, null));
        }

        // ── Chuyến thêm chưa xếp được, và dòng tóm tắt ──
        BigDecimal profit = null;
        double companionKm = 0.0;
        int extras = 0;
        for (RecommendationCardDto card : cards) {
            if (!pair.has(card.getRouteId()) || !date.equals(card.getDate())) {
                continue;
            }
            if (card.getStatus() != RecommendationStatus.RECOMMENDED) {
                day.getUnserved().add(card.getSlotLabel() + " " + card.getRouteLabel() + " (chuyến thêm "
                        + card.getExtraIndex() + "/" + card.getExtraCount() + "): " + card.getNote());
                continue;
            }
            extras++;
            profit = add(profit, card.getEstimatedProfit());
            companionKm += companionKm(card, pair, byId);
        }
        day.setExtras(extras);
        day.setLoopProfit(profit);
        day.setCompanionKm(companionKm);
        day.setBusesUsed(byPlate.size());
        return day;
    }

    /** Một khung: dự báo, chuyến đã có, chuyến thêm ★. */
    private PairPlanViewDto.SlotInfo slot(Route route, LocalDate date, int hour, List<Trip> trips,
            List<RecommendationCardDto> cards, Map<String, ForecastPointDto> points, Map<Long, Integer> standardSeats) {
        PairPlanViewDto.SlotInfo info = new PairPlanViewDto.SlotInfo();
        ForecastPointDto point = points.get(LoopEconomics.slotKey(route.getId(), date, hour));
        info.setStandardSeats(standardSeats.getOrDefault(route.getId(),
                route.getSuitableBusType() != null ? route.getSuitableBusType().getCapacity() : 0));
        if (point != null) {
            info.setDemand(point.getPredictedDemand());
            info.setHot(point.isNeedsReinforcement());
            info.setTripsNeeded(point.getTripsNeeded());
        }
        for (Trip trip : trips) {
            if (route.getId().equals(trip.getRoute().getId()) && trip.getDepartureTime().getHour() == hour) {
                info.setTrips(info.getTrips() + 1);
                info.setSeats(info.getSeats() + trip.getTotalSeats());
            }
        }
        info.setExtras((int) cards.stream()
                .filter(c -> c.getStatus() == RecommendationStatus.RECOMMENDED && route.getId().equals(c.getRouteId())
                        && date.equals(c.getDate()) && c.getDepartureHour() == hour)
                .count());
        return info;
    }

    /** Hàng của một xe: các lượt xếp vào cột giờ, lượt cuối trong ngày cho biết tối nay xe ngủ ở đâu. */
    private PairPlanViewDto.BusRow row(String plate, List<Leg> legs, Pair pair, Leg lastOfDay) {
        PairPlanViewDto.BusRow row = new PairPlanViewDto.BusRow();
        row.setBusPlate(plate);
        Map<Integer, PairPlanViewDto.HourCell> cells = new LinkedHashMap<>();
        for (Integer hour : RegularTimetable.DEPARTURE_HOURS) {
            PairPlanViewDto.HourCell cell = new PairPlanViewDto.HourCell();
            cell.setHour(hour);
            cells.put(hour, cell);
        }
        for (Leg leg : legs.stream().sorted(Comparator.comparing(Leg::departure)).toList()) {
            boolean starred = !"REGULAR".equals(leg.kind()) && !"EXTRA".equals(leg.kind());
            row.setExtra(row.isExtra() || starred);
            cells.get(column(leg.departure().getHour())).getLegs().add(new PairPlanViewDto.LegCell(leg.kind(),
                    leg.route().getId().equals(pair.out().getId()), label(leg.route()), leg.departure(), leg.status(),
                    leg.passengers()));
            if (leg.note() != null) {
                row.getNotes().add(leg.note());
            }
        }
        row.setCells(new ArrayList<>(cells.values()));
        Leg last = lastOfDay != null ? lastOfDay : legs.get(legs.size() - 1);
        row.setSleepsAt(last.route().getDestinationPointDisplay());
        return row;
    }

    /**
     * Mọi lượt của một ngày: chuyến đã có (theo trạng thái), chuyến thêm ★ đủ điều kiện của màn Đề xuất, và
     * lượt đi kèm đã giữ chỗ của chúng (có thể rơi vào ngày khác ngày của chuyến thêm).
     */
    private List<Leg> legsOf(List<Trip> trips, List<RecommendationCardDto> cards, Map<Long, Route> byId,
            LocalDate date) {
        List<Leg> legs = new ArrayList<>();
        for (Trip trip : trips) {
            legs.add(new Leg(trip.getBus() != null ? trip.getBus().getLicensePlate() : null,
                    trip.isExtraTrip() ? "EXTRA" : "REGULAR", byId.getOrDefault(trip.getRoute().getId(), trip.getRoute()),
                    trip.getDepartureTime(), trip.getStatus().name(), null, null));
        }
        for (RecommendationCardDto card : cards) {
            if (card.getStatus() != RecommendationStatus.RECOMMENDED) {
                continue;
            }
            Route route = byId.get(card.getRouteId());
            if (route != null && date.equals(card.getDate())) {
                legs.add(new Leg(card.getBusPlate(), "STAR", route, card.getDate().atTime(card.getDepartureHour(), 0),
                        null, card.getExpectedPassengers(), starNote(card)));
            }
            RecommendationCardDto.CompanionLeg companion = card.getCompanion();
            if (companion != null && companion.isReserved() && companion.getDeparture() != null
                    && date.equals(companion.getDeparture().toLocalDate()) && byId.containsKey(companion.getRouteId())) {
                legs.add(new Leg(companion.getBusPlate(), companion.getKind().name(), byId.get(companion.getRouteId()),
                        companion.getDeparture(), null, companion.getPassengers(),
                        (companion.getKind() == LoopLegKind.RETURN ? "↩ Lượt về" : "↪ Lượt đưa xe tới") + " "
                                + TIME.format(companion.getDeparture()) + " của chuyến thêm ★ " + card.getRouteLabel()
                                + " " + card.getSlotLabel() + " " + card.getDate().format(DateTimeFormatter.ofPattern("dd/MM"))));
            }
        }
        return legs;
    }

    private static String starNote(RecommendationCardDto card) {
        StringBuilder note = new StringBuilder("★ " + card.getSlotLabel() + " " + card.getRouteLabel()
                + ": chuyến thêm " + card.getExtraIndex() + "/" + card.getExtraCount() + " — dự báo "
                + Math.round(card.getPredictedDemand()) + " khách, khung đã có " + card.getExistingSeats() + " ghế"
                + (card.isExistingAssumed() ? " (giả định, chưa có lịch)" : ""));
        RecommendationCardDto.CompanionLeg leg = card.getCompanion();
        if (leg != null && leg.getKind() == LoopLegKind.RETURN && leg.getDeparture() != null) {
            note.append("; về ngay ").append(TIME_DAY.format(leg.getDeparture()));
        } else if (leg != null && leg.getKind() == LoopLegKind.BRING && leg.getDeparture() != null) {
            note.append("; xe được đưa tới lúc ").append(TIME_DAY.format(leg.getDeparture()));
        } else if (leg != null && leg.getKind() == LoopLegKind.NONE) {
            note.append("; xe về đúng bến nhà, không cần lượt đi kèm");
        } else {
            note.append("; chiều về tính như một lượt rỗng");
        }
        if (card.getEstimatedProfit() != null) {
            note.append("; lãi cả vòng ").append(String.format("%,d", card.getEstimatedProfit().longValue())
                    .replace(',', '.')).append("đ");
        }
        return note.toString();
    }

    /** Km của lượt đi kèm một chuyến thêm: về ngay / đưa xe tới / về rỗng chạy trên tuyến chiều ngược. */
    private static double companionKm(RecommendationCardDto card, Pair pair, Map<Long, Route> byId) {
        RecommendationCardDto.CompanionLeg leg = card.getCompanion();
        if (leg == null || leg.getKind() == LoopLegKind.NONE) {
            return 0.0;
        }
        Route route = leg.getRouteId() != null ? byId.get(leg.getRouteId()) : pair.other(card.getRouteId());
        return route != null && route.getDistanceKm() != null ? route.getDistanceKm() : 0.0;
    }

    /** Cột giờ của một lượt: giờ thường lệ lớn nhất không muộn hơn giờ đi (06:30 → cột 06:00). */
    private static int column(int hour) {
        int column = RegularTimetable.DEPARTURE_HOURS.get(0);
        for (Integer h : RegularTimetable.DEPARTURE_HOURS) {
            if (h <= hour) {
                column = h;
            }
        }
        return column;
    }

    // =========================================================================
    // MÀN DỰ BÁO GOM THEO CẶP (phần M1)
    // =========================================================================

    /**
     * Bảng 7 ngày của màn Dự báo, gom theo cặp: hai chiều liền nhau, chiều rời bến nhà trước, mỗi chiều theo
     * giờ; cặp có mức đỉnh cao nhất lên trước (giữ tinh thần "khẩn nhất lên đầu" của thứ tự cũ). Mọi tuyến
     * có đúng một tuyến chiều ngược đều được ghép, không chỉ tuyến có lịch. Không đổi số nào của dự báo.
     */
    @Transactional(readOnly = true)
    public List<ForecastPairGroupDto> forecastByPair(List<ForecastSeriesDto> series) {
        List<Route> all = routeRepository.findAllWithStations();
        List<Pair> pairs = pairs(all, homeStations(), all.stream().map(Route::getId).toList(), null);
        Map<Long, List<ForecastSeriesDto>> byRoute = series.stream()
                .collect(Collectors.groupingBy(ForecastSeriesDto::getRouteId, LinkedHashMap::new, Collectors.toList()));

        List<ForecastPairGroupDto> groups = new ArrayList<>();
        Set<Long> grouped = new HashSet<>();
        for (Pair pair : pairs) {
            List<ForecastSeriesDto> members = new ArrayList<>();
            for (Route route : List.of(pair.out(), pair.back())) {
                byRoute.getOrDefault(route.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(ForecastSeriesDto::getDepartureHour))
                        .forEach(members::add);
                grouped.add(route.getId());
            }
            if (!members.isEmpty()) {
                groups.add(new ForecastPairGroupDto(pairLabel(pair), true, members));
            }
        }
        groups.sort(Comparator.comparingDouble((ForecastPairGroupDto g) -> g.getSeries().stream()
                .mapToDouble(ForecastSeriesDto::getPeakDemandRatio).max().orElse(0.0)).reversed());
        List<ForecastSeriesDto> rest = series.stream().filter(s -> !grouped.contains(s.getRouteId())).toList();
        if (!rest.isEmpty()) {
            groups.add(new ForecastPairGroupDto("Tuyến chưa có đúng một tuyến chiều ngược", false, rest));
        }
        return groups;
    }

    // =========================================================================
    // CẶP TUYẾN
    // =========================================================================

    /**
     * Ghép các tuyến trong candidateIds thành cặp bằng đảo bến (CM-6: đúng một tuyến chiều ngược, và tuyến
     * đó cũng trong danh sách). Chiều "đi" là chiều rời một bến nhà của đội xe; không biết thì tuyến id nhỏ
     * trước. problems != null thì ghi lý do của tuyến không ghép được.
     */
    static List<Pair> pairs(List<Route> all, Set<Long> homeStations, Collection<Long> candidateIds,
            List<String> problems) {
        Map<Long, Route> byId = all.stream().collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        List<Pair> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long id : candidateIds) {
            Route route = byId.get(id);
            if (route == null) {
                note(problems, "Tuyến #" + id + " không có trong dữ liệu.");
                continue;
            }
            if (used.contains(id)) {
                continue;
            }
            List<Route> reverse = RouteService.reverseRoutesOf(route, all);
            if (reverse.size() != 1 || !candidateIds.contains(reverse.get(0).getId())) {
                note(problems, "Tuyến #" + id + " (" + label(route) + ") không có đúng một tuyến chiều ngược"
                        + " trong danh sách có lịch — không có cặp.");
                continue;
            }
            Route other = reverse.get(0);
            if (used.contains(other.getId())) {
                continue;
            }
            boolean routeLeavesHome = leavesHome(route, homeStations);
            boolean otherLeavesHome = leavesHome(other, homeStations);
            Route out = routeLeavesHome != otherLeavesHome ? (routeLeavesHome ? route : other)
                    : (route.getId() < other.getId() ? route : other);
            result.add(new Pair(out, out == route ? other : route));
            used.add(route.getId());
            used.add(other.getId());
        }
        return result;
    }

    private static boolean leavesHome(Route route, Set<Long> homeStations) {
        Station from = route.getDepartureStation();
        return from != null && homeStations.contains(from.getId());
    }

    private static void note(List<String> problems, String problem) {
        if (problems != null) {
            problems.add(problem);
        }
    }

    private Set<Long> homeStations() {
        return busRepository.findAll().stream()
                .map(Bus::getHomeStation)
                .filter(Objects::nonNull)
                .map(Station::getId)
                .collect(Collectors.toSet());
    }

    private static String pairLabel(Pair pair) {
        return pair.out().getDeparturePointDisplay() + " ↔ " + pair.out().getDestinationPointDisplay();
    }

    private static String label(Route route) {
        return route.getDeparturePointDisplay() + " → " + route.getDestinationPointDisplay();
    }

    private static BigDecimal add(BigDecimal sum, BigDecimal value) {
        if (value == null) {
            return sum;
        }
        return sum == null ? value : sum.add(value);
    }
}
