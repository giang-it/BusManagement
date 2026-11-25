package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.RegularTimetable;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.LoopLegKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LÃI CẢ VÒNG CỦA MỘT CHUYẾN THÊM — route pairs CT7 (QĐ-22; phương án PA-B và cách đếm 2a, chủ dự
 * án duyệt 2026-11-22; xem docs/development/route_pairs_decisions.md).
 *
 * <p>Phép tính tiền DUY NHẤT mà màn Đề xuất ({@link RecommendationService}) và What-if
 * ({@link WhatIfSimulationService}) cùng dùng, để hai màn không bao giờ hiểu "chi phí" hay "lãi" theo
 * hai cách. Trước CT7, công thức chi phí một chuyến và bảng giá vé lịch sử bị chép ở cả hai màn.
 *
 * <p><b>Ba câu hỏi lớp này trả lời, không chọn xe:</b>
 * <ol>
 * <li><b>Khung đã có bao nhiêu chuyến</b> (cách 2a): mọi chuyến chưa hủy cùng tuyến, cùng ngày, cùng
 * giờ (06:30 vẫn thuộc khung 06:00, như CM-18). Khung chưa có chuyến nào — chưa tạo lịch — thì coi như
 * có một xe chuẩn, đúng giả định của màn Đề xuất trước CT7. Số chuyến thêm cần = số chuyến khung cần
 * (QĐ-18) − số chuyến đã có.</li>
 * <li><b>Chi phí một lượt</b>: nhiên liệu/km × quãng đường + lương/giờ × thời lượng × số tài xế, theo
 * tham số Admin (Phase 7 bước 3).</li>
 * <li><b>Tiền của lượt đi kèm</b> ({@link LoopLegKind}): lượt đi kèm đi cùng giờ một chuyến thường lệ
 * của chiều ngược ({@link RegularTimetable#DEPARTURE_HOURS}) và chở phần khách còn tràn của khung đó
 * SAU KHI khung đã có đủ số chuyến nó cần. Quy ước này cố ý thận trọng: lượt đi kèm không "cướp" khách
 * của các chuyến thêm mà chính khung đó cần, nên tiền của chúng không phụ thuộc thứ tự xét thẻ, và
 * What-if tính ra đúng số đó mà không cần chọn xe.</li>
 * </ol>
 *
 * <p><b>Lượt đi kèm theo kế hoạch</b> ({@link #planned}) — khi chưa chọn xe: xe dự phòng được giả định
 * đứng ở bến nhà của chúng (QĐ-23). Chuyến thêm xuất phát từ một bến nhà thì xe phải "về ngay" (RETURN);
 * chuyến thêm tới một bến nhà thì phải "đưa xe tới trước" (BRING). Màn Đề xuất dùng kế hoạch này để
 * xếp thứ tự xét thẻ và cho thẻ thiếu tài nguyên; còn thẻ chọn được xe thì đi theo xe thật. What-if
 * (mô hình đếm, không chọn xe) chỉ dùng kế hoạch.
 *
 * <p>Một đối tượng của lớp sống trong MỘT lần tính (một request, hay một cột What-if): nó giữ giá vé,
 * nhu cầu dự báo theo khung, chuyến đã có theo khung và tham số chi phí của lần tính đó. Không đọc DB.
 */
final class LoopEconomics {

    /** Thời lượng mặc định (phút) nếu tuyến thiếu estimatedDuration — cùng fallback của createExtraTrip(). */
    static final int DEFAULT_DURATION_MINUTES = 240;

    /** Hạn giờ lái/ngày để suy số tài xế của một lượt — cùng giá trị với validateStaffForTrip(). */
    static final double MAX_DAILY_DRIVING_HOURS = 8.0;

    /**
     * Thời gian chuẩn bị xe giữa hai lượt. Mirror TripService.BUS_PREP_BUFFER_HOURS (private, không mở
     * visibility chỉ cho phép tính kế hoạch — theo tiền lệ DispatchController tự khai báo lại kèm comment
     * trỏ về nguồn). Khi chọn xe thật, chính cửa sổ "bận" của TripService quyết định.
     */
    static final double PREP_BUFFER_HOURS = 1.0;

    /**
     * Tìm lượt đi kèm trong bao nhiêu ngày quanh chuyến thêm. Hai ngày đủ cho tuyến dài nhất của ba
     * cặp: xe lên Đà Lạt 06:00, tài xế đã lái 6 giờ, nên lượt về rơi vào sáng hôm sau.
     */
    static final int COMPANION_SEARCH_DAYS = 2;

    /**
     * Route pairs CT9 (CM-31): xe tăng cường được giữ ở bến xa tối đa bấy nhiêu ngày (tính phần chờ THÊM so
     * với lần về ngay sớm nhất) — bằng chân trời dự báo, vì thẻ chỉ có trong 7 ngày đó. Với tiền chờ mặc
     * định (~663.000đ/ngày) chờ chỉ đáng khi ngắn hơn 2 × chi phí một chiều: Đà Lạt ≈ 6,3 ngày, Cần Thơ
     * ≈ 3,8, Vũng Tàu ≈ 2,1. Mốc 35 ngày của QĐ-25 để dành cho cặp dài (QĐ-27).
     */
    static final int MAX_HOLD_DAYS = ForecastService.FORECAST_HORIZON_DAYS;

    /** Số chuyến và tổng số ghế đã có trong một khung. */
    record SlotTrips(int trips, int seats) {
    }

    /** Nhu cầu dự báo của một khung và số ghế xe chuẩn của tuyến. */
    record SlotDemand(double demand, int standardSeats) {
    }

    /**
     * Một lượt đi kèm và tiền của nó. route / departure là null khi không có lượt cụ thể (NONE, hay
     * ASSUMED_RETURN của tuyến không có chiều ngược). revenue null khi khung chưa có giá vé lịch sử.
     */
    record Leg(LoopLegKind kind, Route route, LocalDateTime departure, double passengers, BigDecimal revenue,
            BigDecimal cost) {
    }

    private final Map<Long, Map<Integer, BigDecimal>> prices;
    private final Map<String, SlotDemand> demandBySlot;
    private final Map<String, SlotTrips> tripsBySlot;
    private final double nbSize;
    private final double demandFactor;
    private final BigDecimal fuelCostPerKm;
    private final BigDecimal driverWagePerHour;
    private final BigDecimal waitingWagePerDay;
    private final BigDecimal busParkingPerDay;

    /**
     * @param demandFactor hệ số nhân vào nhu cầu dự báo (1.0 ở màn Đề xuất; kịch bản What-if thì khác)
     * @param waitingWagePerDay lương chờ đồng/người/ngày, busParkingPerDay phí xe nằm bến đồng/ngày (CT9, CM-8)
     */
    LoopEconomics(Map<Long, Map<Integer, BigDecimal>> prices, Map<String, SlotDemand> demandBySlot,
            Map<String, SlotTrips> tripsBySlot, double nbSize, double demandFactor, BigDecimal fuelCostPerKm,
            BigDecimal driverWagePerHour, BigDecimal waitingWagePerDay, BigDecimal busParkingPerDay) {
        this.prices = prices;
        this.demandBySlot = demandBySlot;
        this.tripsBySlot = tripsBySlot;
        this.nbSize = nbSize;
        this.demandFactor = demandFactor;
        this.fuelCostPerKm = fuelCostPerKm;
        this.driverWagePerHour = driverWagePerHour;
        this.waitingWagePerDay = waitingWagePerDay;
        this.busParkingPerDay = busParkingPerDay;
    }

    // =========================================================================
    // KHUNG
    // =========================================================================

    /** Chuyến thật đã có trong khung (chưa hủy); null khi khung chưa có chuyến nào. */
    SlotTrips existing(Long routeId, LocalDate date, int hour) {
        return tripsBySlot.get(slotKey(routeId, date, hour));
    }

    /** Chuyến đã có trong khung; khung chưa có chuyến nào (chưa tạo lịch) thì coi như có một xe chuẩn. */
    SlotTrips inSlot(Long routeId, LocalDate date, int hour, int standardSeats) {
        SlotTrips existing = existing(routeId, date, hour);
        return existing != null ? existing : new SlotTrips(1, standardSeats);
    }

    /** Số chuyến thêm khung còn cần: số chuyến khung cần (QĐ-18) trừ số chuyến đã có, không âm. */
    static int extrasStillNeeded(int tripsNeeded, SlotTrips inSlot) {
        return Math.max(0, tripsNeeded - inSlot.trips());
    }

    /** Giá vé lịch sử của khung (tuyến, giờ); null khi chưa có. */
    BigDecimal price(Long routeId, int hour) {
        return prices.getOrDefault(routeId, Map.of()).get(hour);
    }

    /** Chi phí một lượt trên tuyến này, theo tham số của lần tính. */
    BigDecimal tripCost(Route route) {
        return tripCost(route, fuelCostPerKm, driverWagePerHour);
    }

    // =========================================================================
    // LƯỢT ĐI KÈM
    // =========================================================================

    /**
     * Lượt đi kèm chạy cùng khung (legRoute, departure) bằng một xe seats chỗ: chở phần khách còn tràn
     * của khung đó sau khi khung đã có đủ số chuyến nó cần (xem javadoc của lớp). Khung ngoài dự báo
     * thì 0 khách.
     */
    Leg companion(LoopLegKind kind, Route legRoute, LocalDateTime departure, int seats) {
        double passengers = 0.0;
        SlotDemand slot = demandBySlot.get(slotKey(legRoute.getId(), departure.toLocalDate(), departure.getHour()));
        if (slot != null && seats > 0) {
            double demand = slot.demand() * demandFactor;
            SlotTrips in = inSlot(legRoute.getId(), departure.toLocalDate(), departure.getHour(), slot.standardSeats());
            int stillNeeded = extrasStillNeeded(ForecastService.tripsNeeded(demand, slot.standardSeats()), in);
            int seatsBefore = in.seats() + stillNeeded * slot.standardSeats();
            passengers = ForecastService.extraTripPassengers(demand, seatsBefore, seats, nbSize);
        }
        return new Leg(kind, legRoute, departure, passengers, revenue(price(legRoute.getId(), departure.getHour()),
                passengers), tripCost(legRoute));
    }

    /** Không xếp được lượt cụ thể: một lượt về rỗng trên tuyến này — chi phí một lượt, không tiền vé. */
    Leg assumedReturn(Route legRoute) {
        return new Leg(LoopLegKind.ASSUMED_RETURN, null, null, 0.0, BigDecimal.ZERO, tripCost(legRoute));
    }

    /** Xe kết thúc chuyến thêm ở đúng bến nhà: không có lượt đi kèm. */
    static Leg none() {
        return new Leg(LoopLegKind.NONE, null, null, 0.0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Lượt đi kèm THEO KẾ HOẠCH của chuyến thêm [departure, arrival] trên route — khi chưa chọn xe (xem
     * javadoc của lớp). reverse = tuyến chiều ngược duy nhất, hay null. notBefore: lượt đưa xe tới không
     * thể khởi hành trước lúc này.
     */
    Leg planned(Route route, Route reverse, LocalDateTime departure, LocalDateTime arrival,
            Set<Long> homeStationIds, LocalDateTime notBefore, int seats) {
        LoopLegKind kind = plannedKind(route, reverse, homeStationIds);
        if (kind == LoopLegKind.RETURN) {
            List<LocalDateTime> times = returnTimes(arrival);
            return times.isEmpty() ? assumedReturn(reverse) : companion(kind, reverse, times.get(0), seats);
        }
        if (kind == LoopLegKind.BRING) {
            List<LocalDateTime> times = bringTimes(departure, durationMinutes(reverse), notBefore);
            return times.isEmpty() ? assumedReturn(reverse) : companion(kind, reverse, times.get(0), seats);
        }
        return assumedReturn(route);
    }

    /**
     * Loại lượt đi kèm theo kế hoạch: không có tuyến chiều ngược → ASSUMED_RETURN; bến đi là bến nhà của
     * một xe nào đó → RETURN (xe dự phòng đứng ở đó); bến đến là bến nhà → BRING; không biết bến nhà nào
     * → RETURN (coi như xe dự phòng đứng ở bến đi).
     */
    static LoopLegKind plannedKind(Route route, Route reverse, Set<Long> homeStationIds) {
        if (reverse == null) {
            return LoopLegKind.ASSUMED_RETURN;
        }
        Long from = idOf(route.getDepartureStation());
        Long to = idOf(route.getDestinationStation());
        if (from != null && homeStationIds.contains(from)) {
            return LoopLegKind.RETURN;
        }
        if (to != null && homeStationIds.contains(to)) {
            return LoopLegKind.BRING;
        }
        return LoopLegKind.RETURN;
    }

    /**
     * Giờ khởi hành có thể cho lượt "về ngay", sớm trước: các giờ thường lệ từ ngày tới nơi tới hết
     * COMPANION_SEARCH_DAYS ngày sau đó, không sớm hơn arrival + thời gian chuẩn bị xe.
     */
    static List<LocalDateTime> returnTimes(LocalDateTime arrival) {
        LocalDateTime earliest = arrival.plusMinutes(Math.round(PREP_BUFFER_HOURS * 60));
        List<LocalDateTime> times = new ArrayList<>();
        for (int d = 0; d <= COMPANION_SEARCH_DAYS; d++) {
            for (int hour : RegularTimetable.DEPARTURE_HOURS) {
                LocalDateTime t = arrival.toLocalDate().plusDays(d).atTime(hour, 0);
                if (!t.isBefore(earliest)) {
                    times.add(t);
                }
            }
        }
        times.sort(Comparator.naturalOrder());
        return times;
    }

    /**
     * Giờ khởi hành có thể cho lượt "đưa xe tới trước", muộn trước: các giờ thường lệ trong
     * COMPANION_SEARCH_DAYS ngày trước, sau notBefore, mà lượt chiều ngược (reverseDurationMinutes) cộng
     * thời gian chuẩn bị xe vẫn tới kịp giờ đi của chuyến thêm.
     */
    static List<LocalDateTime> bringTimes(LocalDateTime departure, int reverseDurationMinutes, LocalDateTime notBefore) {
        LocalDateTime latest = departure.minusMinutes(reverseDurationMinutes + Math.round(PREP_BUFFER_HOURS * 60));
        List<LocalDateTime> times = new ArrayList<>();
        for (int d = 0; d <= COMPANION_SEARCH_DAYS; d++) {
            for (int hour : RegularTimetable.DEPARTURE_HOURS) {
                LocalDateTime t = departure.toLocalDate().minusDays(d).atTime(hour, 0);
                if (!t.isAfter(latest) && t.isAfter(notBefore)) {
                    times.add(t);
                }
            }
        }
        times.sort(Comparator.reverseOrder());
        return times;
    }

    // =========================================================================
    // VÒNG GIỮ XE — route pairs CT9 (QĐ-25, QĐ-26; CM-8, CM-29…CM-32)
    // =========================================================================
    //
    // Hai thẻ ngược chiều của cùng một cặp — chuyến thêm ĐI (rời bến nhà, kế hoạch "về ngay") và một chuyến
    // thêm VỀ sau đó (về bến nhà, kế hoạch "đưa xe tới trước") — có thể chạy bằng MỘT xe và MỘT kíp: xe đi
    // chuyến thêm đi, CHỜ ở bến xa, rồi chạy chuyến thêm về. Thay vì bốn lượt (hai trong đó gần như rỗng: lượt
    // về ngay và lượt đưa xe tới chỉ chở khách còn tràn), vòng giữ chạy hai lượt đều có khách, đổi lại tiền chờ.
    // Quyết bằng tiền, ở mức KẾ HOẠCH (xe chuẩn, chưa chọn xe), để màn Đề xuất và What-if ghép đúng như nhau:
    //   - tiền chờ (CM-30, W-b) = phần chờ THÊM so với lần về ngay sớm nhất (earliestReturn) × (lương chờ ×
    //     số người của kíp + phí xe nằm bến) ÷ 24 giờ;
    //   - lãi vòng giữ = tiền vé hai chuyến thêm − chi phí hai lượt − tiền chờ;
    //   - lãi tăng thêm = lãi vòng giữ − lãi của hai vòng riêng mà nó thay (chỉ phần dương: vòng lỗ thì không
    //     chạy riêng). Chỉ ghép khi lãi tăng thêm > 0, đôi tăng nhiều nhất trước (CM-32);
    //   - điều kiện: chuyến về xuất phát sau khi xe tới + 1 giờ chuẩn bị; một tài xế lái ≤ 8 giờ mỗi ngày
    //     khởi hành; chờ thêm ≤ MAX_HOLD_DAYS ngày (CM-31).
    // Lợi ích tối đa "2 × chi phí một chiều" của QĐ-25 tự có trong phép so: thứ vòng giữ tiết kiệm được chính
    // là hai lượt đi kèm của hai vòng riêng.

    /**
     * Một chuyến thêm ở mức kế hoạch (xe chuẩn), đủ cho phép ghép. id: chỉ số do nơi gọi đặt; plannedKind:
     * lượt đi kèm theo kế hoạch ({@link #planned}); tripRevenue / tripCost: của riêng chuyến thêm;
     * plannedProfit: lãi cả vòng khi chạy RIÊNG, cùng lượt đi kèm theo kế hoạch.
     */
    record PlannedExtra(int id, Route route, Route reverse, LocalDateTime departure, LocalDateTime arrival,
            LoopLegKind plannedKind, BigDecimal tripRevenue, BigDecimal tripCost, BigDecimal plannedProfit) {

        String slot() {
            return slotKey(route.getId(), departure.toLocalDate(), departure.getHour());
        }

        private boolean priced() {
            return tripRevenue != null && tripCost != null && plannedProfit != null;
        }
    }

    /**
     * Một vòng giữ xe: chuyến thêm đi (outId) rồi chuyến thêm về (inId). returnFrom: lần về ngay sớm nhất, mốc
     * của tiền chờ; waitHours / waitCost: phần chờ thêm; profit: lãi cả vòng giữ; separate: lãi của hai vòng
     * riêng mà nó thay (chỉ phần dương); gain = profit − separate.
     */
    record Hold(int outId, int inId, LocalDateTime returnFrom, double waitHours, BigDecimal waitCost,
            BigDecimal profit, BigDecimal separate, BigDecimal gain) {
    }

    /**
     * Các vòng giữ xe đáng làm trong danh sách chuyến thêm (xem đầu mục): mọi đôi (đi, về) hợp lệ có lãi tăng
     * thêm dương, xếp tăng nhiều nhất trước, rồi lấy lần lượt các đôi không trùng chuyến nào. Thứ tự hoà: chuyến
     * đi sớm trước, rồi id nhỏ trước — nơi gọi đặt id theo thứ tự chuyến thêm trong khung, nên chuyến thêm thứ
     * nhất của một khung được ghép trước chuyến thứ hai. excludedSlots: khung không được ghép (job đêm: khung
     * Admin đã từ chối).
     */
    List<Hold> pairHolds(List<PlannedExtra> extras, Set<String> excludedSlots) {
        Map<Integer, PlannedExtra> byId = new HashMap<>();
        extras.forEach(extra -> byId.put(extra.id(), extra));
        List<Hold> candidates = new ArrayList<>();
        for (PlannedExtra out : extras) {
            if (out.plannedKind() != LoopLegKind.RETURN || out.reverse() == null || !out.priced()
                    || excludedSlots.contains(out.slot())) {
                continue;
            }
            LocalDateTime returnFrom = earliestReturn(out.route(), out.reverse(), out.departure(), out.arrival());
            if (returnFrom == null) {
                continue;
            }
            LocalDateTime ready = out.arrival().plusMinutes(Math.round(PREP_BUFFER_HOURS * 60));
            for (PlannedExtra in : extras) {
                if (in.plannedKind() != LoopLegKind.BRING || !in.priced() || !in.route().getId().equals(out.reverse().getId())
                        || excludedSlots.contains(in.slot()) || in.departure().isBefore(ready)) {
                    continue;
                }
                if (in.departure().toLocalDate().equals(out.departure().toLocalDate())
                        && driverShare(out.route()) + driverShare(in.route()) > MAX_DAILY_DRIVING_HOURS) {
                    continue;
                }
                double waitHours = Math.max(0.0, Duration.between(returnFrom, in.departure()).toMinutes() / 60.0);
                if (waitHours > MAX_HOLD_DAYS * 24.0) {
                    continue;
                }
                int crew = Math.max(requiredDrivers(durationMinutes(out.route()) / 60.0),
                        requiredDrivers(durationMinutes(in.route()) / 60.0));
                BigDecimal wait = waitCost(waitHours, crew);
                BigDecimal profit = out.tripRevenue().add(in.tripRevenue()).subtract(out.tripCost())
                        .subtract(in.tripCost()).subtract(wait);
                BigDecimal separate = positive(out.plannedProfit()).add(positive(in.plannedProfit()));
                BigDecimal gain = profit.subtract(separate);
                if (gain.signum() > 0) {
                    candidates.add(new Hold(out.id(), in.id(), returnFrom, waitHours, wait, profit, separate, gain));
                }
            }
        }
        candidates.sort(Comparator.comparing(Hold::gain, Comparator.reverseOrder())
                .thenComparing(hold -> byId.get(hold.outId()).departure())
                .thenComparingInt(Hold::outId)
                .thenComparing(hold -> byId.get(hold.inId()).departure())
                .thenComparingInt(Hold::inId));
        Set<Integer> used = new HashSet<>();
        List<Hold> chosen = new ArrayList<>();
        for (Hold hold : candidates) {
            if (!used.contains(hold.outId()) && !used.contains(hold.inId())) {
                used.add(hold.outId());
                used.add(hold.inId());
                chosen.add(hold);
            }
        }
        return chosen;
    }

    /**
     * Tiền chờ của hours giờ (CM-8, CM-30): (lương chờ × số người của kíp + phí xe nằm bến) × giờ ÷ 24, làm
     * tròn tới đồng. Tham số thiếu thì bỏ thành phần đó, như tripCost.
     */
    BigDecimal waitCost(double hours, int crew) {
        BigDecimal perDay = BigDecimal.ZERO;
        if (waitingWagePerDay != null) {
            perDay = perDay.add(waitingWagePerDay.multiply(BigDecimal.valueOf(crew)));
        }
        if (busParkingPerDay != null) {
            perDay = perDay.add(busParkingPerDay);
        }
        return perDay.multiply(BigDecimal.valueOf(hours)).divide(BigDecimal.valueOf(24), 0, RoundingMode.HALF_UP);
    }

    /**
     * Lần "về ngay" sớm nhất của chuyến thêm [departure, arrival] trên route (CM-30): giờ thường lệ đầu tiên
     * của {@link #returnTimes} mà một tài xế lái được cả hai lượt (mỗi ngày khởi hành ≤ 8 giờ). Ví dụ đi Đà Lạt
     * 06:00, tới 12:00: 18:00 cùng ngày là 12 giờ lái nên không được, về ngay sớm nhất là 06:00 hôm sau.
     */
    static LocalDateTime earliestReturn(Route route, Route reverse, LocalDateTime departure, LocalDateTime arrival) {
        boolean sameDayFits = driverShare(route) + driverShare(reverse) <= MAX_DAILY_DRIVING_HOURS;
        for (LocalDateTime time : returnTimes(arrival)) {
            if (sameDayFits || !time.toLocalDate().equals(departure.toLocalDate())) {
                return time;
            }
        }
        return null;
    }

    /** Phần giờ lái của tài xế chính trên một lượt của tuyến — như TripService.driverForLoop: min(giờ ÷ số tài xế, 8). */
    private static double driverShare(Route route) {
        double hours = durationMinutes(route) / 60.0;
        return Math.min(hours / requiredDrivers(hours), MAX_DAILY_DRIVING_HOURS);
    }

    private static BigDecimal positive(BigDecimal value) {
        return value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    // =========================================================================
    // PHÉP TÍNH DÙNG CHUNG (không cần đối tượng)
    // =========================================================================

    /** Khung = (tuyến, ngày, giờ). */
    static String slotKey(Long routeId, LocalDate date, int hour) {
        return routeId + "@" + date + "@" + hour;
    }

    /**
     * Chuyến đã có theo khung, từ các dòng (routeId, departureTime, totalSeats) của
     * TripRepository.findSlotSeatsBetween (chuyến chưa hủy).
     */
    static Map<String, SlotTrips> slotTrips(List<Object[]> rows) {
        Map<String, SlotTrips> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            LocalDateTime departure = (LocalDateTime) row[1];
            int seats = row[2] != null ? ((Number) row[2]).intValue() : 0;
            result.merge(slotKey(((Number) row[0]).longValue(), departure.toLocalDate(), departure.getHour()),
                    new SlotTrips(1, seats), (a, b) -> new SlotTrips(a.trips() + b.trips(), a.seats() + b.seats()));
        }
        return result;
    }

    /** Nhu cầu dự báo theo khung, cho cả 35 ngày xem trước — lượt đi kèm có thể rơi sau ngày thứ 7. */
    static Map<String, SlotDemand> demandBySlot(DemandForecastViewDto forecast) {
        Map<String, SlotDemand> result = new HashMap<>();
        if (forecast.getSeries() == null) {
            return result;
        }
        for (ForecastSeriesDto series : forecast.getSeries()) {
            List<ForecastPointDto> points = series.getPreview() != null ? series.getPreview() : series.getPoints();
            for (ForecastPointDto point : points) {
                result.put(slotKey(series.getRouteId(), point.getDate(), series.getDepartureHour()),
                        new SlotDemand(point.getPredictedDemand(), series.getStandardSeats()));
            }
        }
        return result;
    }

    /**
     * Giá vé lịch sử theo (tuyến → giờ khởi hành → giá của chuyến COMPLETED gần nhất), từ các dòng
     * (routeId, departureTime, price) đã sắp theo thời gian GIẢM DẦN của
     * TripRepository.findPriceHistoryByStatus: putIfAbsent giữ đúng dòng mới nhất của mỗi khung.
     */
    static Map<Long, Map<Integer, BigDecimal>> latestPrices(List<Object[]> rowsNewestFirst) {
        Map<Long, Map<Integer, BigDecimal>> result = new HashMap<>();
        for (Object[] row : rowsNewestFirst) {
            if (row[0] == null || row[1] == null || row[2] == null) {
                continue;
            }
            Long routeId = ((Number) row[0]).longValue();
            int hour = ((LocalDateTime) row[1]).getHour();
            result.computeIfAbsent(routeId, k -> new HashMap<>()).putIfAbsent(hour, (BigDecimal) row[2]);
        }
        return result;
    }

    /** Thời lượng của tuyến (phút); thiếu thì DEFAULT_DURATION_MINUTES. */
    static int durationMinutes(Route route) {
        return route.getEstimatedDuration() != null && route.getEstimatedDuration() > 0
                ? route.getEstimatedDuration()
                : DEFAULT_DURATION_MINUTES;
    }

    /**
     * Số tài xế của một lượt: ⌈giờ ÷ 8⌉, ít nhất 1. Mọi tuyến của ba cặp ≤ 8 giờ nên luôn là 1; vẫn tính
     * để một tuyến dài sau này lộ ra ở cổng validateStaff chứ không làm hỏng gì.
     */
    static int requiredDrivers(double durationHours) {
        return Math.max(1, (int) Math.ceil(durationHours / MAX_DAILY_DRIVING_HOURS));
    }

    /**
     * Chi phí vận hành ước tính của một lượt: nhiên liệu/km × quãng đường + lương/giờ × thời lượng × số
     * tài xế. Bỏ qua thành phần thiếu dữ liệu thay vì đoán; thiếu cả hai thì null. Làm tròn tới đồng.
     */
    static BigDecimal tripCost(Route route, BigDecimal fuelCostPerKm, BigDecimal driverWagePerHour) {
        double durationHours = durationMinutes(route) / 60.0;
        BigDecimal cost = BigDecimal.ZERO;
        boolean any = false;
        if (route.getDistanceKm() != null && fuelCostPerKm != null) {
            cost = cost.add(fuelCostPerKm.multiply(BigDecimal.valueOf(route.getDistanceKm())));
            any = true;
        }
        if (driverWagePerHour != null) {
            cost = cost.add(driverWagePerHour.multiply(BigDecimal.valueOf(durationHours))
                    .multiply(BigDecimal.valueOf(requiredDrivers(durationHours))));
            any = true;
        }
        return any ? cost.setScale(0, RoundingMode.HALF_UP) : null;
    }

    /** Số khách × giá vé, làm tròn tới đồng; null khi chưa có giá vé. */
    static BigDecimal revenue(BigDecimal price, double passengers) {
        return price == null ? null
                : price.multiply(BigDecimal.valueOf(passengers)).setScale(0, RoundingMode.HALF_UP);
    }

    /** Cộng tiền của chuyến thêm với tiền của lượt đi kèm: phần của chuyến thêm null thì cả vòng null. */
    static BigDecimal plus(BigDecimal ofTrip, BigDecimal ofLeg) {
        if (ofTrip == null) {
            return null;
        }
        return ofLeg != null ? ofTrip.add(ofLeg) : ofTrip;
    }

    private static Long idOf(Station station) {
        return station != null ? station.getId() : null;
    }
}
