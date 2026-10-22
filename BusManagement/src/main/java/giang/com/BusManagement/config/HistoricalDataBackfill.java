package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistorySimulation.RouteProfile;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.HolidayCalendar;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bộ sinh dữ liệu chuyến xe LỊCH SỬ cho Demand Forecast — viết lại ở GĐ2 của đợt thiết
 * kế lại dự báo (QĐ-10, QĐ-11 trong docs/development/forecast_decisions.md). Bản Phase 5
 * sinh TỈ LỆ LẤP ĐẦY cho 84 ngày tính từ ngày chạy; bản này sinh NHU CẦU từ 01/01/2023,
 * rồi cắt theo số ghế mà một "nhà xe trong quá khứ" đã thật sự chạy.
 *
 * Dữ liệu này là MÔ PHỎNG và phải được nói rõ như vậy khi bảo vệ — không được trình bày
 * như số liệu thật. Phần mô phỏng (nhu cầu, bão, hủy, ý định tăng cường) nằm ở
 * HistorySimulation; lớp này xếp xe, tài xế và ghi DB.
 *
 * ====================================================================
 * CÁCH CHẠY
 * ====================================================================
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill
 *
 * Profile `backfill` KHÔNG kích hoạt `demo`, nên DataInitializer không chạy và không
 * có gì bị xóa. TUYỆT ĐỐI KHÔNG chạy kèm `demo` — profile đó xóa sạch schema.
 *
 * Tuỳ chọn: -Dbackfill.truth-csv=<đường dẫn> ghi thêm NHU CẦU THẬT của từng khung ra một
 * file CSV (QĐ-10, G2-6). DB chỉ lưu số vé đã bán — đã bị chặn bởi số ghế — nên đây là
 * cách duy nhất để chấm xem bước EM của mô hình khôi phục nhu cầu tốt tới đâu. File chỉ
 * phục vụ nghiên cứu; không ghi gì thêm vào DB.
 *
 * ====================================================================
 * CHẠY LẠI / CHẠY BỔ SUNG — THEO TỪNG NGÀY, KHÔNG NHÂN BẢN
 * ====================================================================
 * Mọi con số của một ngày tất định theo ngày đó (HistorySimulation tính từ mốc cố định
 * EPOCH, không từ ngày chạy), nên chạy bổ sung về sau nối tiếp liền mạch. Một ngày đã có
 * chuyến dạng bộ sinh thì được BỎ QUA CẢ NGÀY: xe và tài xế được xếp cho cả ngày một lúc,
 * nên sinh nửa ngày sẽ phá bất biến "mỗi xe, mỗi tài xế một chuyến/ngày".
 *
 * Dữ liệu của bộ sinh này luôn bắt đầu đúng EPOCH. Nếu DB có chuyến dạng bộ sinh mà ngày
 * EPOCH lại trống, đó là dữ liệu của bộ sinh cũ — bộ sinh DỪNG và yêu cầu chạy script xoá
 * của GĐ3 trước, thay vì trộn hai bộ dữ liệu.
 *
 * ====================================================================
 * NHÀ XE TRONG QUÁ KHỨ (planDay) — QUY TẮC NGHIỆP VỤ ĐÚNG THEO CÁCH DỰNG
 * ====================================================================
 * Ghi thẳng qua repository như DataInitializer, không qua TripService: createManualTrip()
 * kiểm theo TRẠNG THÁI HIỆN TẠI của xe (TRAVELING, ngưỡng bảo trì...), vô nghĩa với một
 * chuyến của ba năm trước. Bù lại, dữ liệu sinh ra tôn trọng các quy tắc theo cách dựng:
 * <ul>
 * <li>mỗi xe và mỗi tài xế tối đa MỘT chuyến mỗi ngày, chỉ sinh tuyến ≤ 8h — nên không
 * trùng lịch và không ai vượt 8h lái/ngày;</li>
 * <li>chuyến CÓ SẴN trong DB (Admin tự tạo, hay chuyến thật về sau) giữ xe, tài xế, phụ xe
 * và tài xế phụ của nó trong MỌI ngày nó chạy — hôm đó bộ sinh không xếp thêm chuyến cho
 * họ (GĐ3, lỗi #33). Bản GĐ2, như bản Phase 5, chỉ xét chuyến do chính nó sinh nên đã để
 * một xe chạy hai chuyến cùng lúc;</li>
 * <li>bằng lái phải CÒN HẠN vào ngày khởi hành — hỏi chính Driver.isLicenseValid(date).
 * Bộ sinh cũ thiếu điều kiện này và đã xếp 69 chuyến cho tài xế hết hạn bằng (lỗi #32);</li>
 * <li>số vé không vượt số ghế; số ghế là sức chứa của chính xe được xếp.</li>
 * </ul>
 * Mỗi khung có một chuyến thường; xe ưu tiên đúng loại xe phù hợp của tuyến, thiếu thì
 * lấy loại khác — cùng thứ tự với AI phân công (findBestAvailableBus, mục #26). Quanh
 * các kỳ nghỉ nhà xe muốn thêm chuyến (HistorySimulation.wishedExtraTrips); chuyến thêm
 * chỉ chạy được bằng xe và tài xế còn rảnh trong ngày (QĐ-10, G2-4 / M-13), ưu tiên khung
 * có nhu cầu kỳ vọng cao nhất, khởi hành sau chuyến gốc 30/40/50 phút — cùng giờ nên vẫn
 * thuộc cùng khung khi gom theo HOUR(departureTime), như chuyến của createExtraTrip().
 * Vé lấp chuyến thường trước, phần tràn sang chuyến thêm. Ngày bão: mọi chuyến của tuyến
 * bị hủy; ngoài ra 6% khung bị hủy ngẫu nhiên. Khung bị hủy không có chuyến thêm, và giữ
 * số vé đã bán trước khi hủy (cố ý, xem THESIS_ROADMAP.md).
 *
 * ====================================================================
 * ODOMETER: CỘNG CẢ HAI MỐC, GIỮ NGUYÊN kmSinceLastMaintenance
 * ====================================================================
 * Như bản Phase 5: chuyến COMPLETED cộng quãng đường tuyến vào CẢ odometer LẪN
 * lastMaintenanceOdometer, nên kmSinceLastMaintenance — và mọi quy tắc bảo dưỡng —
 * không đổi; chỉ quãng đường trọn đời tăng thật. Chỉ cộng km của chuyến do lần chạy này
 * tạo ra, nên chạy lại (tạo 0 chuyến) cũng cộng 0 km.
 *
 * Trục thời gian là departureTime; createdAt mang ngày chạy bộ sinh, đó là bình thường
 * (xem THESIS_ROADMAP.md, Developer Notes).
 */
@Component
@Profile("backfill")
@Order(100)
@RequiredArgsConstructor
public class HistoricalDataBackfill implements CommandLineRunner {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;

    /** Đường dẫn file CSV nhu cầu thật; trống = không ghi. */
    @Value("${backfill.truth-csv:}")
    private String truthCsvPath;

    /** Seed cố định của toàn bộ mô phỏng: chạy trên máy khác vẫn ra đúng bộ số đó. */
    static final long RANDOM_SEED = 20261021L;

    /**
     * Chỉ sinh tuyến mà MỘT tài xế chạy trọn được trong hạn mức ngày (giá trị 8h khai báo
     * lại theo đúng lý do đã ghi ở DriverRecommendationService). Tuyến dài hơn bắt buộc có
     * tài xế phụ và phụ xe, bộ dữ liệu sẽ phải bịa thêm nhân sự.
     */
    private static final int MAX_ROUTE_DURATION_MINUTES = 8 * 60;

    /** Số ghế xe chuẩn khi tuyến chưa có loại xe phù hợp. */
    private static final int DEFAULT_SEATS = 40;

    /** Chuyến được mở bán trước ngày chạy bao lâu (mốc nghiệp vụ, không phải audit). */
    private static final int SALE_OPENS_DAYS_BEFORE = 30;

    /** Phút khởi hành của chuyến thêm thứ 1, 2, 3 trong cùng khung. */
    private static final int[] EXTRA_MINUTES = { 30, 40, 50 };

    /**
     * Chuyến có sẵn ở trạng thái nào thì đã chiếm xe và người: mọi trạng thái trừ CANCELLED —
     * cùng danh sách mà TripService.buildAvailabilityContext nạp. Khác luật "bận" khi xếp
     * chuyến TƯƠNG LAI (isBusBusy chỉ tính ACTIVE/DEPARTED/PENDING_APPROVAL): với lịch sử,
     * chuyến đã COMPLETED cũng đã thật sự dùng xe và người hôm đó.
     */
    private static final Set<TripStatus> OCCUPYING_STATUSES = EnumSet.complementOf(EnumSet.of(TripStatus.CANCELLED));

    /** Xe và người (userId của Driver) mà các chuyến CÓ SẴN đã chiếm trong một ngày. */
    record Occupied(Set<Long> busIds, Set<Long> staffIds) {
        static final Occupied NONE = new Occupied(Set.of(), Set.of());
    }

    /**
     * Một chuyến có sẵn, không do bộ sinh tạo: khoảng [departure, end) nó chạy, xe, và mọi
     * người trên xe (tài xế, phụ xe, tài xế phụ).
     */
    record ExistingTrip(LocalDateTime departure, LocalDateTime end, Long busId, Set<Long> staffIds) {
    }

    /** Một chuyến đã lên kế hoạch, chưa là entity. */
    record PlannedTrip(Bus bus, Driver driver, int seats, int tickets, int minute, boolean extra) {
    }

    /** Một khung (tuyến × giờ) của một ngày cùng các chuyến của nó. */
    record PlannedSlot(Route route, int hourIndex, double mean, int demand, boolean closed, boolean cancelled,
            List<PlannedTrip> trips) {

        int seats() {
            return trips.stream().mapToInt(PlannedTrip::seats).sum();
        }

        int sold() {
            return trips.stream().mapToInt(PlannedTrip::tickets).sum();
        }
    }

    @Override
    public void run(String... args) {
        generate(LocalDate.now().minusDays(1));
    }

    /**
     * Sinh mọi ngày còn thiếu trong [EPOCH, lastDay].
     *
     * @return số chuyến đã tạo
     */
    int generate(LocalDate lastDay) {
        System.out.println("========================================");
        System.out.println("[Backfill] Sinh dữ liệu chuyến LỊCH SỬ (dữ liệu MÔ PHỎNG) — bộ sinh GĐ2");

        LocalDate coveredEnd = LocalDate.of(HolidayCalendar.LAST_COVERED_YEAR, 12, 31);
        if (lastDay.isAfter(coveredEnd)) {
            System.out.printf("[Backfill] Lịch ngày lễ chỉ phủ tới %s — chỉ sinh tới đó. Bổ sung bảng âm lịch "
                    + "trong HolidayCalendar để sinh tiếp.%n", coveredEnd);
            lastDay = coveredEnd;
        }
        if (lastDay.isBefore(HistorySimulation.EPOCH)) {
            System.out.println("[Backfill] DỪNG — chưa tới ngày bắt đầu " + HistorySimulation.EPOCH);
            return 0;
        }

        List<Route> routes = eligibleRoutes();
        // findAllWithBusType(): CommandLineRunner chạy NGOÀI session Hibernate, nên
        // bus.getBusType() lazy sẽ ném LazyInitializationException khi lấy số ghế.
        List<Bus> buses = busRepository.findAllWithBusType().stream()
                .sorted(Comparator.comparing(Bus::getId))
                .toList();
        List<Driver> drivers = driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .sorted(Comparator.comparing(Driver::getUserId))
                .toList();
        int slotsPerDay = routes.size() * HistorySimulation.DEPARTURE_HOURS.size();
        if (routes.isEmpty() || buses.size() < slotsPerDay || drivers.isEmpty()) {
            System.out.printf("[Backfill] DỪNG — thiếu dữ liệu nền (tuyến hợp lệ=%d, xe=%d cần ≥ %d, tài xế=%d).%n",
                    routes.size(), buses.size(), slotsPerDay, drivers.size());
            return 0;
        }

        Set<LocalDate> existingDays = existingGeneratorDays(routes, lastDay);
        if (!existingDays.isEmpty() && !existingDays.contains(HistorySimulation.EPOCH)) {
            System.out.printf("[Backfill] DỪNG — DB đã có chuyến dạng bộ sinh (%d ngày, sớm nhất %s) nhưng ngày %s "
                    + "trống: đó là dữ liệu của bộ sinh CŨ. Chạy hai script của GĐ3 trong "
                    + "docs/development/forecast_research/ (gd3_check_old_history.sql rồi gd3_delete_old_history.sql, "
                    + "sau khi backup) trước, đừng trộn hai bộ dữ liệu.%n",
                    existingDays.size(), Collections.min(existingDays), HistorySimulation.EPOCH);
            return 0;
        }

        Map<Long, RouteProfile> profiles = new HashMap<>();
        for (Route route : routes) {
            profiles.put(route.getId(), profileOf(route));
        }
        HistorySimulation simulation = new HistorySimulation(RANDOM_SEED, List.copyOf(profiles.values()), lastDay);
        System.out.printf("[Backfill] Cửa sổ: %s → %s | %d tuyến × %d khung | %d xe, %d tài xế đang hoạt động%n",
                HistorySimulation.EPOCH, lastDay, routes.size(), HistorySimulation.DEPARTURE_HOURS.size(),
                buses.size(), drivers.size());

        Map<LocalDate, Occupied> occupied = loadOccupied(routes, existingDays, lastDay);
        int occupiedDays = 0;
        for (LocalDate day : occupied.keySet()) {
            if (!day.isBefore(HistorySimulation.EPOCH) && !day.isAfter(lastDay) && !existingDays.contains(day)) {
                occupiedDays++;
            }
        }
        System.out.printf("[Backfill] Chuyến có sẵn (không do bộ sinh tạo) giữ xe hoặc người trong %d ngày cần sinh "
                + "— hôm đó không xếp thêm chuyến cho họ.%n", occupiedDays);

        Map<Long, Double> kmByBusId = new HashMap<>();
        List<String> truthRows = new ArrayList<>();
        int created = 0;
        int extras = 0;
        int cancelled = 0;
        int skippedDays = 0;
        int unservedSlots = 0;
        int soldOutSlots = 0;
        int openSlots = 0;
        for (LocalDate day = HistorySimulation.EPOCH; !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (existingDays.contains(day)) {
                skippedDays++;
                continue;
            }
            List<PlannedSlot> plan = planDay(day, routes, profiles, buses, drivers,
                    occupied.getOrDefault(day, Occupied.NONE), simulation);
            List<Trip> regular = new ArrayList<>();
            for (PlannedSlot slot : plan) {
                if (!slot.trips().isEmpty()) {
                    regular.add(toTrip(slot, slot.trips().get(0), day, null));
                }
            }
            // Chuyến thường lưu trước để chuyến thêm trỏ được về chuyến gốc của nó.
            List<Trip> savedRegular = tripRepository.saveAll(regular);

            List<Trip> extraTrips = new ArrayList<>();
            int index = 0;
            for (PlannedSlot slot : plan) {
                truthRows.add(truthRow(day, slot, profiles.get(slot.route().getId())));
                if (slot.trips().isEmpty()) {
                    unservedSlots++;
                    continue;
                }
                Trip parent = savedRegular.get(index++);
                for (PlannedTrip extra : slot.trips().subList(1, slot.trips().size())) {
                    extraTrips.add(toTrip(slot, extra, day, parent));
                }
                if (slot.cancelled()) {
                    cancelled += slot.trips().size();
                } else {
                    openSlots++;
                    if (slot.demand() >= slot.seats()) {
                        soldOutSlots++;
                    }
                }
            }
            tripRepository.saveAll(extraTrips);
            created += savedRegular.size() + extraTrips.size();
            extras += extraTrips.size();
            accumulateOdometerKm(savedRegular, kmByBusId);
            accumulateOdometerKm(extraTrips, kmByBusId);
        }

        int busesUpdated = applyOdometer(kmByBusId);
        writeTruthCsv(truthRows);

        System.out.printf("[Backfill] XONG — tạo %d chuyến (%d chuyến tăng cường, %d chuyến bị hủy); bỏ qua %d ngày "
                + "đã có sẵn; %d khung thiếu xe hoặc tài xế.%n", created, extras, cancelled, skippedDays, unservedSlots);
        if (openSlots > 0) {
            System.out.printf("[Backfill] Khung bán hết vé (nhu cầu ≥ số ghế): %d/%d = %.1f%%.%n",
                    soldOutSlots, openSlots, 100.0 * soldOutSlots / openSlots);
        }
        System.out.printf("[Backfill] Odometer: cộng %.0f km cho %d xe (kèm lastMaintenanceOdometer, "
                + "nên kmSinceLastMaintenance không đổi).%n",
                kmByBusId.values().stream().mapToDouble(Double::doubleValue).sum(), busesUpdated);
        System.out.println("[Backfill] LƯU Ý: createdAt của các dòng này là ngày chạy bộ sinh, không phải ngày "
                + "khởi hành — trục thời gian của dataset là departureTime.");
        System.out.println("========================================");
        return created;
    }

    // =========================================================================
    // NHÀ XE TRONG QUÁ KHỨ — hàm thuần, không đụng DB
    // =========================================================================

    /**
     * Kế hoạch chạy xe của một ngày: mỗi khung một chuyến thường, rồi chuyến tăng cường
     * trong giới hạn xe và tài xế còn rảnh. Thứ tự xếp các khung được xáo theo ngày (tất
     * định) để không khung nào luôn là khung nhận xe "lệch loại".
     *
     * Xe và người trong occupied đã có chuyến khác hôm đó (lỗi #33), nên được coi như đã
     * dùng ngay từ đầu — cho cả chuyến thường lẫn chuyến thêm. Ngày không có chuyến có sẵn
     * nhận Occupied.NONE và cho đúng kế hoạch như trước khi có tham số này.
     */
    static List<PlannedSlot> planDay(LocalDate day, List<Route> routes, Map<Long, RouteProfile> profiles,
            List<Bus> buses, List<Driver> drivers, Occupied occupied, HistorySimulation simulation) {

        int dayIndex = (int) ChronoUnit.DAYS.between(HistorySimulation.EPOCH, day);
        List<Driver> licensed = drivers.stream().filter(d -> d.isLicenseValid(day)).toList();
        List<Bus> busOrder = rotate(buses, dayIndex * 7);
        List<Driver> driverOrder = rotate(licensed, dayIndex * 11);
        Set<Long> busesUsed = new HashSet<>(occupied.busIds());
        Set<Long> driversUsed = new HashSet<>(occupied.staffIds());

        record Draft(Route route, int hourIndex, double mean, int demand, boolean closed, boolean cancelled,
                List<Bus> buses, List<Driver> drivers) {
        }
        List<Draft> drafts = new ArrayList<>();
        for (Route route : routes) {
            RouteProfile profile = profiles.get(route.getId());
            boolean closed = simulation.isClosed(route.getId(), day);
            for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                boolean cancelled = closed || simulation.isRandomlyCancelled(route.getId(), day, h);
                drafts.add(new Draft(route, h, simulation.mean(profile, day, h), simulation.demand(profile, day, h),
                        closed, cancelled, new ArrayList<>(), new ArrayList<>()));
            }
        }

        // Chuyến thường: xe đúng loại của tuyến trước, thiếu thì loại khác.
        List<Draft> assignmentOrder = new ArrayList<>(drafts);
        Collections.shuffle(assignmentOrder, simulation.slotOrder(day));
        for (Draft draft : assignmentOrder) {
            Integer preferredType = draft.route().getSuitableBusType() != null
                    ? draft.route().getSuitableBusType().getId() : null;
            Bus bus = busOrder.stream()
                    .filter(b -> !busesUsed.contains(b.getId()) && preferredType != null
                            && b.getBusType() != null && preferredType.equals(b.getBusType().getId()))
                    .findFirst()
                    .orElse(busOrder.stream().filter(b -> !busesUsed.contains(b.getId())).findFirst().orElse(null));
            Driver driver = driverOrder.stream().filter(d -> !driversUsed.contains(d.getUserId())).findFirst()
                    .orElse(null);
            if (bus == null || driver == null) {
                continue; // khung không có chuyến — được đếm và báo ở generate()
            }
            busesUsed.add(bus.getId());
            driversUsed.add(driver.getUserId());
            draft.buses().add(bus);
            draft.drivers().add(driver);
        }

        // Chuyến tăng cường: khung muốn thêm, chưa bị hủy, nhu cầu kỳ vọng cao nhất trước;
        // mỗi vòng thêm tối đa một chuyến cho mỗi khung; xe lớn nhất còn rảnh.
        List<Draft> wanting = drafts.stream()
                .filter(d -> !d.cancelled() && !d.buses().isEmpty()
                        && simulation.wishedExtraTrips(d.route().getId(), day) > 0)
                .sorted(Comparator.comparingDouble(Draft::mean).reversed()
                        .thenComparing(d -> d.route().getId())
                        .thenComparingInt(Draft::hourIndex))
                .toList();
        boolean resourcesLeft = true;
        for (int round = 1; round <= EXTRA_MINUTES.length && resourcesLeft; round++) {
            for (Draft draft : wanting) {
                if (simulation.wishedExtraTrips(draft.route().getId(), day) < round) {
                    continue;
                }
                // Stream.max giữ phần tử ĐẦU TIÊN khi bằng nhau, nên giữa các xe cùng cỡ
                // thứ tự xoay vòng của ngày vẫn được tôn trọng.
                Bus bus = busOrder.stream()
                        .filter(b -> !busesUsed.contains(b.getId()))
                        .max(Comparator.comparingInt(HistoricalDataBackfill::capacityOf))
                        .orElse(null);
                Driver driver = driverOrder.stream().filter(d -> !driversUsed.contains(d.getUserId())).findFirst()
                        .orElse(null);
                if (bus == null || driver == null) {
                    resourcesLeft = false;
                    break;
                }
                busesUsed.add(bus.getId());
                driversUsed.add(driver.getUserId());
                draft.buses().add(bus);
                draft.drivers().add(driver);
            }
        }

        // Vé: lấp chuyến thường trước, phần tràn sang chuyến thêm theo thứ tự.
        List<PlannedSlot> plan = new ArrayList<>();
        for (Draft draft : drafts) {
            int remaining = draft.demand();
            List<PlannedTrip> trips = new ArrayList<>();
            for (int i = 0; i < draft.buses().size(); i++) {
                int seats = capacityOf(draft.buses().get(i));
                int tickets = Math.min(remaining, seats);
                remaining -= tickets;
                trips.add(new PlannedTrip(draft.buses().get(i), draft.drivers().get(i), seats, tickets,
                        i == 0 ? 0 : EXTRA_MINUTES[i - 1], i > 0));
            }
            plan.add(new PlannedSlot(draft.route(), draft.hourIndex(), draft.mean(), draft.demand(), draft.closed(),
                    draft.cancelled(), trips));
        }
        return plan;
    }

    /**
     * Mỗi chuyến có sẵn chiếm xe và người của nó trong MỌI ngày lịch mà khoảng
     * [departure, end) chạm tới: chuyến qua đêm chiếm cả hai ngày, còn chuyến đến đúng 00:00
     * thì không chạm sang ngày sau. Giữ cả ngày chứ không chỉ giờ trùng — đúng luật "mỗi xe,
     * mỗi người một chuyến/ngày" mà bộ sinh dựng cho chính nó.
     */
    static Map<LocalDate, Occupied> occupiedByDay(Collection<ExistingTrip> trips) {
        Map<LocalDate, Set<Long>> busIds = new HashMap<>();
        Map<LocalDate, Set<Long>> staffIds = new HashMap<>();
        for (ExistingTrip trip : trips) {
            LocalDate first = trip.departure().toLocalDate();
            LocalDate last = trip.end().isAfter(trip.departure()) ? trip.end().minusNanos(1).toLocalDate() : first;
            for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                if (trip.busId() != null) {
                    busIds.computeIfAbsent(day, d -> new HashSet<>()).add(trip.busId());
                }
                staffIds.computeIfAbsent(day, d -> new HashSet<>()).addAll(trip.staffIds());
            }
        }
        Map<LocalDate, Occupied> occupied = new HashMap<>();
        Set<LocalDate> days = new HashSet<>(busIds.keySet());
        days.addAll(staffIds.keySet());
        for (LocalDate day : days) {
            occupied.put(day, new Occupied(busIds.getOrDefault(day, Set.of()), staffIds.getOrDefault(day, Set.of())));
        }
        return occupied;
    }

    /** Danh sách xoay vòng bắt đầu từ vị trí offset — để tải trải đều qua các ngày. */
    private static <T> List<T> rotate(List<T> items, int offset) {
        if (items.isEmpty()) {
            return items;
        }
        int start = Math.floorMod(offset, items.size());
        List<T> rotated = new ArrayList<>(items.subList(start, items.size()));
        rotated.addAll(items.subList(0, start));
        return rotated;
    }

    static int capacityOf(Bus bus) {
        return bus.getBusType() != null && bus.getBusType().getCapacity() > 0
                ? bus.getBusType().getCapacity()
                : DEFAULT_SEATS;
    }

    static RouteProfile profileOf(Route route) {
        int seats = route.getSuitableBusType() != null && route.getSuitableBusType().getCapacity() > 0
                ? route.getSuitableBusType().getCapacity()
                : DEFAULT_SEATS;
        return new RouteProfile(route.getId(), route.getHolidayFlow(), seats);
    }

    // =========================================================================
    // DB
    // =========================================================================

    /** Tuyến đủ điều kiện: có thời lượng, và một tài xế chạy trọn được trong hạn mức ngày. */
    private List<Route> eligibleRoutes() {
        // findAllWithStations(): suitableBusType và lộ trình được dùng ngoài session.
        return routeRepository.findAllWithStations().stream()
                .filter(r -> r.getEstimatedDuration() != null && r.getEstimatedDuration() > 0)
                .filter(r -> r.getEstimatedDuration() <= MAX_ROUTE_DURATION_MINUTES)
                .sorted(Comparator.comparing(Route::getId))
                .toList();
    }

    /**
     * Những ngày đã có chuyến DẠNG BỘ SINH (tuyến đủ điều kiện, giờ 06/12/18, phút 0 hoặc
     * phút của chuyến thêm). Nạp một lần thay vì hỏi từng khung.
     */
    private Set<LocalDate> existingGeneratorDays(List<Route> routes, LocalDate lastDay) {
        Set<LocalDate> days = new HashSet<>();
        List<Long> routeIds = routes.stream().map(Route::getId).toList();
        for (LocalDateTime departure : tripRepository.findDepartureTimesByRouteIdsBetween(routeIds,
                HistorySimulation.EPOCH.atStartOfDay(), lastDay.plusDays(1).atStartOfDay())) {
            if (hasGeneratorTime(departure)) {
                days.add(departure.toLocalDate());
            }
        }
        return days;
    }

    /** Giờ khởi hành dạng bộ sinh: 06/12/18 giờ, phút 0 (chuyến thường) hoặc phút của chuyến thêm. */
    private static boolean hasGeneratorTime(LocalDateTime departure) {
        if (!HistorySimulation.DEPARTURE_HOURS.contains(departure.getHour())) {
            return false;
        }
        int minute = departure.getMinute();
        if (minute == 0) {
            return true;
        }
        for (int extraMinute : EXTRA_MINUTES) {
            if (minute == extraMinute) {
                return true;
            }
        }
        return false;
    }

    /**
     * Xe và người mà các chuyến CÓ SẴN đã giữ, theo ngày, trên đoạn còn phải sinh — từ ngày
     * đầu tiên chưa có dữ liệu bộ sinh tới lastDay (lỗi #33). Một truy vấn scalar cho cả
     * đoạn. Chuyến dạng bộ sinh bị bỏ qua: chúng thuộc những ngày đã được bỏ qua cả ngày,
     * và bỏ qua chúng giữ cho một lần chạy bổ sung ra đúng như một lần chạy liền.
     * Package-private để test gọi thẳng được.
     */
    Map<LocalDate, Occupied> loadOccupied(List<Route> routes, Set<LocalDate> existingDays, LocalDate lastDay) {
        LocalDate firstMissing = HistorySimulation.EPOCH;
        while (!firstMissing.isAfter(lastDay) && existingDays.contains(firstMissing)) {
            firstMissing = firstMissing.plusDays(1);
        }
        if (firstMissing.isAfter(lastDay)) {
            return Map.of();
        }
        Set<Long> generatorRouteIds = new HashSet<>();
        routes.forEach(r -> generatorRouteIds.add(r.getId()));

        // Một chuyến có n tài xế phụ trả về n dòng (LEFT JOIN coDrivers): gom lại theo id.
        Map<Long, ExistingTrip> trips = new HashMap<>();
        for (Object[] row : tripRepository.findResourceUseBetween(OCCUPYING_STATUSES,
                firstMissing.atStartOfDay(), lastDay.plusDays(1).atStartOfDay())) {
            Long tripId = (Long) row[0];
            LocalDateTime departure = (LocalDateTime) row[1];
            LocalDateTime arrival = (LocalDateTime) row[2];
            Long routeId = (Long) row[3];
            Integer routeMinutes = (Integer) row[4];
            if (departure == null || (generatorRouteIds.contains(routeId) && hasGeneratorTime(departure))) {
                continue;
            }
            LocalDateTime end = arrival != null ? arrival
                    : routeMinutes != null ? departure.plusMinutes(routeMinutes) : departure;
            ExistingTrip trip = trips.computeIfAbsent(tripId,
                    id -> new ExistingTrip(departure, end, (Long) row[5], new HashSet<>()));
            for (int column = 6; column <= 8; column++) {
                if (row[column] != null) {
                    trip.staffIds().add((Long) row[column]);
                }
            }
        }
        return occupiedByDay(trips.values());
    }

    private Trip toTrip(PlannedSlot slot, PlannedTrip planned, LocalDate day, Trip parent) {
        Route route = slot.route();
        LocalDateTime departure = day.atTime(HistorySimulation.DEPARTURE_HOURS.get(slot.hourIndex()),
                planned.minute());
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setBus(planned.bus());
        trip.setDriver(planned.driver());
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));
        trip.setTotalSeats(planned.seats());
        trip.setTicketsSold(planned.tickets());
        trip.setPrice(priceFor(route));
        trip.setStatus(slot.cancelled() ? TripStatus.CANCELLED : TripStatus.COMPLETED);
        trip.setSaleOpenedAt(departure.minusDays(SALE_OPENS_DAYS_BEFORE));
        trip.setExtraTrip(planned.extra());
        trip.setOriginalTrip(parent);
        return trip;
    }

    /**
     * Gom quãng đường của các chuyến ĐÃ HOÀN THÀNH theo xe — đúng như FSM, chuyến CANCELLED
     * không chạy nên không cộng km.
     */
    private void accumulateOdometerKm(List<Trip> trips, Map<Long, Double> kmByBusId) {
        for (Trip trip : trips) {
            if (trip.getStatus() != TripStatus.COMPLETED || trip.getBus() == null) {
                continue;
            }
            Double km = trip.getRoute() != null ? trip.getRoute().getDistanceKm() : null;
            if (km != null && km > 0) {
                kmByBusId.merge(trip.getBus().getId(), km, Double::sum);
            }
        }
    }

    /**
     * Cộng km đã gom vào odometer VÀ lastMaintenanceOdometer của từng xe — hiệu số
     * kmSinceLastMaintenance giữ nguyên. Xe thiếu một trong hai mốc thì bỏ qua: cộng một
     * vế sẽ bịa ra một khoảng km kể từ lần bảo dưỡng cuối.
     *
     * @return số xe thực sự được cập nhật
     */
    private int applyOdometer(Map<Long, Double> kmByBusId) {
        List<Bus> updated = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : kmByBusId.entrySet()) {
            Bus bus = busRepository.findById(entry.getKey()).orElse(null);
            if (bus == null || bus.getOdometer() == null || bus.getLastMaintenanceOdometer() == null) {
                System.out.printf("[Backfill] Bỏ qua odometer của xe #%s (thiếu mốc odometer).%n", entry.getKey());
                continue;
            }
            bus.setOdometer(bus.getOdometer() + entry.getValue());
            bus.setLastMaintenanceOdometer(bus.getLastMaintenanceOdometer() + entry.getValue());
            updated.add(bus);
        }
        busRepository.saveAll(updated);
        return updated.size();
    }

    private String truthRow(LocalDate day, PlannedSlot slot, RouteProfile profile) {
        return String.join(",",
                day.toString(),
                String.valueOf(slot.route().getId()),
                String.valueOf(HistorySimulation.DEPARTURE_HOURS.get(slot.hourIndex())),
                String.valueOf(HistorySimulation.Flow.of(profile.holidayFlow())),
                String.valueOf(profile.standardSeats()),
                String.format(java.util.Locale.ROOT, "%.4f", slot.mean()),
                String.valueOf(slot.demand()),
                String.valueOf(slot.trips().size()),
                String.valueOf(slot.seats()),
                String.valueOf(slot.sold()),
                slot.closed() ? "1" : "0",
                slot.cancelled() ? "1" : "0");
    }

    private void writeTruthCsv(List<String> rows) {
        if (truthCsvPath == null || truthCsvPath.isBlank()) {
            return;
        }
        Path path = Path.of(truthCsvPath);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("date,route_id,hour,flow,standard_seats,mean,demand,trips,seats,sold,closed,cancelled");
            writer.newLine();
            for (String row : rows) {
                writer.write(row);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được file nhu cầu thật " + path, e);
        }
        System.out.printf("[Backfill] Đã ghi nhu cầu thật của %d khung vào %s%n", rows.size(), path);
    }

    /** Giá vé suy ra từ quãng đường, làm tròn tới 10.000đ cho giống dữ liệu thật (như Phase 5). */
    private static BigDecimal priceFor(Route route) {
        double km = route.getDistanceKm() != null ? route.getDistanceKm() : 100.0;
        long price = Math.round(km * 1000 / 10_000.0) * 10_000L;
        return BigDecimal.valueOf(Math.max(price, 50_000L));
    }
}
