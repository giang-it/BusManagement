package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistorySimulation.RouteProfile;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.HolidayCalendar;
import giang.com.BusManagement.domain.LocationRule;
import giang.com.BusManagement.domain.LocationRule.Leg;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Station;
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
import java.time.Duration;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Bộ sinh dữ liệu chuyến xe LỊCH SỬ cho Demand Forecast. Viết lại ở GĐ2 của đợt thiết kế
 * lại dự báo (QĐ-10, QĐ-11 trong docs/development/forecast_decisions.md), rồi ở CT3 của
 * đợt cặp tuyến (QĐ-21 trong docs/development/route_pairs_decisions.md): chỉ sinh các
 * CẶP tuyến đã chọn, và xe, tài xế chạy NỐI TIẾP theo vòng đi–về thay vì "mỗi người một
 * chuyến mỗi ngày". Bộ sinh sinh NHU CẦU từ 01/01/2023, rồi cắt theo số ghế mà một "nhà
 * xe trong quá khứ" đã thật sự chạy.
 *
 * Dữ liệu này là MÔ PHỎNG và phải được nói rõ như vậy khi bảo vệ — không được trình bày
 * như số liệu thật. Phần mô phỏng (nhu cầu, bão, hủy, ý định tăng cường) nằm ở
 * HistorySimulation; lớp này xếp xe, tài xế (Dispatcher) và ghi DB.
 *
 * ====================================================================
 * CÁCH CHẠY
 * ====================================================================
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill
 *
 * Profile `backfill` KHÔNG kích hoạt `demo`, nên DataInitializer không chạy và không
 * có gì bị xóa. TUYỆT ĐỐI KHÔNG chạy kèm `demo` — profile đó xóa sạch schema.
 *
 * Tuỳ chọn:
 * <ul>
 * <li>-Dbackfill.route-ids=2,12,5,13,10,11 — các tuyến được sinh (QĐ-20: ba cặp quanh
 * Bến xe Miền Đông, đây cũng là giá trị mặc định). Mỗi tuyến phải dài ≤ 8 giờ, có bến
 * đi / bến đến, và có CHIỀU NGƯỢC nằm cùng danh sách — xe đi thì phải có đường về.</li>
 * <li>-Dbackfill.truth-csv=&lt;đường dẫn&gt; — ghi thêm NHU CẦU THẬT của từng khung ra một
 * file CSV (QĐ-10, G2-6). DB chỉ lưu số vé đã bán — đã bị chặn bởi số ghế — nên đây là
 * cách duy nhất để chấm xem bước EM của mô hình khôi phục nhu cầu tốt tới đâu.</li>
 * </ul>
 *
 * ====================================================================
 * CHẠY LẠI / CHẠY BỔ SUNG — CHỈ NỐI TIẾP, KHÔNG NHÂN BẢN
 * ====================================================================
 * Mọi con số nhu cầu của một ngày tất định theo ngày đó (HistorySimulation tính từ mốc
 * cố định EPOCH, không từ ngày chạy). Một ngày đã có chuyến dạng bộ sinh thì được BỎ QUA
 * CẢ NGÀY, và những ngày đã sinh phải LIỀN MỘT MẠCH từ EPOCH: xe chạy vòng nên ngày sau
 * bắt đầu từ chỗ ngày trước kết thúc. Lần chạy bổ sung đọc lại vị trí, giờ rảnh và giờ
 * lái của từng xe, từng tài xế từ chính các chuyến đã ghi (Dispatcher.resume). Chỗ duy
 * nhất có thể lệch so với một lần chạy liền là các lượt tăng cường vắt qua ranh giới: xe
 * tăng cường đang ở bến xa sẽ về ở chuyến về sớm nhất (QĐ-21 (d)).
 *
 * Dữ liệu của bộ sinh này luôn bắt đầu đúng EPOCH. Nếu DB có chuyến dạng bộ sinh mà ngày
 * EPOCH lại trống, đó là dữ liệu của bộ sinh cũ — bộ sinh DỪNG và yêu cầu chạy script xoá
 * trước, thay vì trộn hai bộ dữ liệu.
 *
 * ====================================================================
 * NHÀ XE TRONG QUÁ KHỨ (Dispatcher) — QUY TẮC NGHIỆP VỤ ĐÚNG THEO CÁCH DỰNG
 * ====================================================================
 * Ghi thẳng qua repository như DataInitializer, không qua TripService: createManualTrip()
 * kiểm theo TRẠNG THÁI HIỆN TẠI của xe (TRAVELING, ngưỡng bảo trì...), vô nghĩa với một
 * chuyến của ba năm trước. Bù lại, dữ liệu sinh ra tôn trọng các quy tắc theo cách dựng
 * (QĐ-21 (c), luật vị trí QĐ-23 ở domain/LocationRule):
 * <ul>
 * <li>lượt sau của một xe hay một tài xế xuất phát từ bến mà lượt trước của họ vừa tới;
 * chưa chạy lượt nào thì ở BẾN NHÀ (CT5a);</li>
 * <li>xe nghỉ ≥ 1 giờ, tài xế nghỉ ≥ 30 phút giữa hai lượt; tài xế lái ≤ 8 giờ trong MỖI
 * ngày lịch — chuyến qua đêm được tách tại 00:00, mỗi phần tính vào ngày của nó;</li>
 * <li>chuyến CÓ SẴN trong DB (Admin tự tạo, hay chuyến thật về sau) giữ xe, tài xế, phụ xe
 * và tài xế phụ của nó trong MỌI ngày nó chạy (GĐ3, lỗi #33). Chúng nằm ngoài vòng xe của
 * bộ sinh nên không đổi vị trí của ai;</li>
 * <li>bằng lái phải CÒN HẠN vào ngày khởi hành — hỏi chính Driver.isLicenseValid(date)
 * (lỗi #32);</li>
 * <li>số vé không vượt số ghế; số ghế là sức chứa của chính xe được xếp.</li>
 * </ul>
 * Lịch thường lệ: mỗi tuyến 06/12/18 giờ, mỗi khung một chuyến thường. Các khung được xếp
 * theo GIỜ khởi hành; cùng một giờ thì xáo theo ngày (tất định). Chuyến thường lấy xe đang
 * ở bến xuất phát, ưu tiên đúng loại xe của tuyến, xe chờ lâu nhất đi trước; tài xế cũng
 * vậy. Khung không có xe hay tài xế hợp lệ thì bỏ trống và được đếm.
 *
 * Chuyến tăng cường (HistorySimulation.wishedExtraTrips, QĐ-10 G2-4 / M-13) khởi hành sau
 * chuyến thường 30/40/50 phút — cùng giờ nên vẫn thuộc cùng khung khi gom theo
 * HOUR(departureTime), như chuyến của createExtraTrip(). Xe "rảnh" cho chuyến thêm là xe
 * đang đứng ở bến nhà của nó, chưa có lượt nào; xe lớn nhất trước. Xe thuộc lịch thường lệ
 * đang ở bến xa không bị lấy đi. Theo QĐ-21 (d), cách H1:
 * <ul>
 * <li>xe tăng cường tới bến xa thì VỀ NGAY, đi cùng chuyến thường lệ chiều về sớm nhất mà
 * cả xe (nghỉ ≥ 1 giờ) lẫn tài xế (≤ 8 giờ/ngày) đều kịp, chở khách tràn của khung đó —
 * thường 0 vé;</li>
 * <li>chiều về cần thêm xe ở bến xa thì xe được GỬI LÊN TRƯỚC, đi cùng chuyến thường lệ
 * chiều đi muộn nhất còn kịp (cũng chở khách tràn của khung đó), rồi chờ ở bến xa tới khung
 * cần nó;</li>
 * <li>chuyến thường ở bến xa bị hủy thì xe và tài xế đứng yên, rồi cũng về ngay như trên.
 * Thiếu luật này thì mỗi lần hủy lại để dư một xe ở bến xa, và đội xe trôi dần ra bến xa.</li>
 * </ul>
 * Lượt chạy rỗng được ghi là chuyến tăng cường COMPLETED có 0 vé: nhu cầu của khung không
 * đổi, còn khung có thêm ghế trống nên không bị coi là bán hết, đúng sự thật. Vé lấp chuyến
 * thường trước, phần tràn sang chuyến thêm. Ngày bão: mọi chuyến của tuyến bị hủy; ngoài
 * ra 6% khung bị hủy ngẫu nhiên. Khung bị hủy không có chuyến thêm, và giữ số vé đã bán
 * trước khi hủy (cố ý, xem THESIS_ROADMAP.md).
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

    /**
     * Các tuyến được sinh (QĐ-20): mặc định ba cặp quanh Bến xe Miền Đông trên DB thật —
     * Đà Lạt 2/12, Cần Thơ 13/5, Vũng Tàu 10/11 — cùng khoá với lịch thật (CT6). Package-private để test gán thẳng.
     */
    @Value(giang.com.BusManagement.domain.RegularTimetable.ROUTE_IDS_PROPERTY)
    List<Long> routeIds;

    /** Seed cố định của toàn bộ mô phỏng: chạy trên máy khác vẫn ra đúng bộ số đó. */
    static final long RANDOM_SEED = 20261021L;

    /**
     * Chỉ sinh tuyến mà MỘT tài xế chạy trọn được trong hạn mức ngày (giá trị 8h khai báo
     * lại theo đúng lý do đã ghi ở DriverRecommendationService). Tuyến dài hơn bắt buộc có
     * tài xế phụ và phụ xe, bộ dữ liệu sẽ phải bịa thêm nhân sự (QĐ-20: cặp dài để sau).
     */
    private static final int MAX_ROUTE_DURATION_MINUTES = 8 * 60;

    /** Tài xế lái tối đa chừng này phút trong một ngày lịch (QĐ-21 (c)). */
    static final int MAX_DRIVING_MINUTES_PER_DAY = 8 * 60;

    /** Nghỉ tối thiểu giữa hai lượt (QĐ-21 (c)). */
    static final Duration BUS_REST = Duration.ofHours(1);
    static final Duration DRIVER_REST = Duration.ofMinutes(30);

    /** Số ghế xe chuẩn khi tuyến chưa có loại xe phù hợp. */
    private static final int DEFAULT_SEATS = 40;

    /** Chuyến được mở bán trước ngày chạy bao lâu (mốc nghiệp vụ, không phải audit). */
    private static final int SALE_OPENS_DAYS_BEFORE = 30;

    /** Phút khởi hành của chuyến thêm thứ 1, 2, 3 trong cùng khung — tối đa 3 chuyến thêm. */
    static final int[] EXTRA_MINUTES = { 30, 40, 50 };

    /** Lượt về của xe tăng cường được tìm trong chừng này ngày kể từ khung nó rời đi. */
    private static final int RETURN_SEARCH_DAYS = 3;

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

    /**
     * Một chuyến đã lên kế hoạch, chưa là entity. extra = chuyến tăng cường; chuyến đầu của
     * khung là chuyến thường (extra = false), thường khởi hành phút 0.
     */
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

    /** Một khung (tuyến × ngày × giờ) — khoá để đặt trước chuyến tăng cường. */
    record SlotKey(long routeId, LocalDate day, int hourIndex) {
    }

    /** Một chuyến đã gán xe và tài xế vào một khung, khởi hành ở phút minute của giờ khung. */
    record Booking(Bus bus, Driver driver, int minute) {
    }

    /** Một chuyến dạng bộ sinh đã ghi trong DB — để lần chạy bổ sung nối tiếp đúng vị trí. */
    record GeneratedLeg(long routeId, LocalDateTime departure, LocalDateTime arrival, Long busId, Long driverId,
            boolean extra, boolean cancelled) {
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
        System.out.println("[Backfill] Sinh dữ liệu chuyến LỊCH SỬ (dữ liệu MÔ PHỎNG) — bộ sinh cặp tuyến, xe chạy vòng");

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

        List<Route> routes = selectedRoutes();
        if (routes == null) {
            return 0;
        }
        // findAllWithBusType(): CommandLineRunner chạy NGOÀI session Hibernate, nên
        // bus.getBusType() lazy sẽ ném LazyInitializationException khi lấy số ghế.
        List<Bus> buses = busRepository.findAllWithBusType().stream()
                .sorted(Comparator.comparing(Bus::getId))
                .toList();
        List<Driver> drivers = driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .sorted(Comparator.comparing(Driver::getUserId))
                .toList();
        if (buses.isEmpty() || drivers.isEmpty()) {
            System.out.printf("[Backfill] DỪNG — thiếu dữ liệu nền (xe=%d, tài xế đang hoạt động=%d).%n",
                    buses.size(), drivers.size());
            return 0;
        }
        List<String> homeless = new ArrayList<>();
        buses.stream().filter(b -> homeOf(b) == null).forEach(b -> homeless.add("xe #" + b.getId()));
        drivers.stream().filter(d -> homeOf(d) == null).forEach(d -> homeless.add("tài xế #" + d.getUserId()));
        if (!homeless.isEmpty()) {
            System.out.println("[Backfill] DỪNG — chưa có bến nhà (CT5a), không biết họ bắt đầu từ đâu: " + homeless);
            return 0;
        }

        Set<LocalDate> existingDays = existingGeneratorDays(routes, lastDay);
        if (!existingDays.isEmpty() && !existingDays.contains(HistorySimulation.EPOCH)) {
            System.out.printf("[Backfill] DỪNG — DB đã có chuyến dạng bộ sinh (%d ngày, sớm nhất %s) nhưng ngày %s "
                    + "trống: đó là dữ liệu của bộ sinh CŨ. Xoá nó bằng script trong "
                    + "docs/development/route_pairs_research/ (sau khi backup) trước, đừng trộn hai bộ dữ liệu.%n",
                    existingDays.size(), Collections.min(existingDays), HistorySimulation.EPOCH);
            return 0;
        }
        LocalDate firstMissing = HistorySimulation.EPOCH;
        while (existingDays.contains(firstMissing)) {
            firstMissing = firstMissing.plusDays(1);
        }
        LocalDate gapEnd = firstMissing;
        if (existingDays.stream().anyMatch(d -> d.isAfter(gapEnd))) {
            System.out.printf("[Backfill] DỪNG — các ngày đã sinh không liền một mạch từ %s (trống ngày %s mà vẫn có "
                    + "ngày sau đó): xe chạy vòng nên không sinh chen vào giữa được.%n",
                    HistorySimulation.EPOCH, firstMissing);
            return 0;
        }

        Map<Long, RouteProfile> profiles = profilesOf(routes);
        HistorySimulation simulation = new HistorySimulation(RANDOM_SEED, List.copyOf(profiles.values()), lastDay);
        System.out.printf("[Backfill] Cửa sổ: %s → %s | tuyến %s (%d khung/ngày) | %d xe, %d tài xế đang hoạt động%n",
                HistorySimulation.EPOCH, lastDay, routes.stream().map(Route::getId).toList(),
                routes.size() * HistorySimulation.DEPARTURE_HOURS.size(), buses.size(), drivers.size());

        Map<LocalDate, Occupied> occupied = loadOccupied(routes, existingDays, lastDay);
        int occupiedDays = 0;
        for (LocalDate day : occupied.keySet()) {
            if (!day.isBefore(firstMissing) && !day.isAfter(lastDay)) {
                occupiedDays++;
            }
        }
        System.out.printf("[Backfill] Chuyến có sẵn (không do bộ sinh tạo) giữ xe hoặc người trong %d ngày cần sinh "
                + "— hôm đó không xếp chuyến nào cho họ.%n", occupiedDays);

        Dispatcher dispatcher = new Dispatcher(routes, profiles, buses, drivers, simulation, occupied);
        if (firstMissing.isAfter(HistorySimulation.EPOCH) && !firstMissing.isAfter(lastDay)) {
            int rebooked = dispatcher.resume(loadGeneratorLegs(routes, firstMissing), firstMissing);
            System.out.printf("[Backfill] Chạy bổ sung từ %s: vị trí, giờ rảnh và giờ lái đọc từ các chuyến đã sinh; "
                    + "%d xe ở bến xa được xếp lượt về ngay.%n", firstMissing, rebooked);
        }

        Map<Long, Double> kmByBusId = new HashMap<>();
        List<String> truthRows = new ArrayList<>();
        int created = 0;
        int extras = 0;
        int emptyExtras = 0;
        int cancelled = 0;
        int unservedSlots = 0;
        int soldOutSlots = 0;
        int openSlots = 0;
        for (LocalDate day = firstMissing; !day.isAfter(lastDay); day = day.plusDays(1)) {
            List<PlannedSlot> plan = dispatcher.planDay(day);
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
                    emptyExtras += !slot.cancelled() && extra.tickets() == 0 ? 1 : 0;
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

        System.out.printf("[Backfill] XONG — tạo %d chuyến (%d chuyến tăng cường, trong đó %d lượt 0 vé; %d chuyến bị "
                + "hủy); bỏ qua %d ngày đã có sẵn; %d khung thiếu xe hoặc tài xế.%n",
                created, extras, emptyExtras, cancelled, existingDays.size(), unservedSlots);
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
    // NHÀ XE TRONG QUÁ KHỨ — thuần, không đụng DB
    // =========================================================================

    /**
     * "Nhà xe trong quá khứ" (QĐ-21 (c), (d)): xếp xe và tài xế cho từng ngày, NỐI TIẾP từ
     * ngày trước — mỗi xe, mỗi tài xế mang theo lượt gần nhất của mình (ở đâu, tới lúc nào),
     * số phút đã lái trong từng ngày lịch, và các chuyến tăng cường đã đặt trước cho những
     * khung chưa tới. Mọi phép "đang ở đâu, chạy tiếp được chưa" gọi LocationRule (CT5a).
     *
     * Các ngày phải được xếp theo đúng thứ tự, mỗi ngày một lần (planDay). Lớp này không
     * đụng DB; generate() ghi kết quả.
     */
    static final class Dispatcher {

        private final List<Route> routes;
        private final Map<Long, RouteProfile> profiles;
        private final List<Bus> buses;
        private final List<Driver> drivers;
        private final HistorySimulation simulation;
        private final Map<LocalDate, Occupied> occupied;

        private final Map<Long, Long> fromOf = new HashMap<>();
        private final Map<Long, Long> toOf = new HashMap<>();
        private final Map<Long, Route> partner = new HashMap<>();
        private final Map<Long, Long> busHome = new HashMap<>();
        private final Map<Long, Long> driverHome = new HashMap<>();
        /** Các bến có xe "ở nhà" — nơi lấy xe rảnh cho chuyến tăng cường. */
        private final Set<Long> busHomes = new HashSet<>();

        /** Lượt gần nhất ĐÃ CAM KẾT của mỗi xe / tài xế — có thể là lượt đặt trước cho ngày sau. */
        private final Map<Long, Leg> busLast = new HashMap<>();
        private final Map<Long, Leg> driverLast = new HashMap<>();
        /** Số phút mỗi tài xế đã lái (kể cả đã cam kết) trong từng ngày lịch. */
        private final Map<Long, Map<LocalDate, Integer>> driverMinutes = new HashMap<>();
        /** Chuyến tăng cường đặt trước cho khung của những ngày CHƯA xếp. */
        private final Map<SlotKey, List<Booking>> booked = new HashMap<>();
        /** Các khung của ngày đang xếp — chuyến đặt vào khung hôm nay ghi thẳng vào đây. */
        private Map<SlotKey, Draft> today = Map.of();

        /** Một khung của ngày đang xếp. */
        private static final class Draft {
            final Route route;
            final LocalDate day;
            final int hourIndex;
            final double mean;
            final int demand;
            final boolean closed;
            final boolean cancelled;
            /** Chuyến thường (phút 0), hoặc chuyến đặt trước được đôn lên khi khung không có xe khác. */
            Booking regular;
            final List<Booking> extras = new ArrayList<>();

            Draft(Route route, LocalDate day, int hourIndex, double mean, int demand, boolean closed,
                    boolean cancelled) {
                this.route = route;
                this.day = day;
                this.hourIndex = hourIndex;
                this.mean = mean;
                this.demand = demand;
                this.closed = closed;
                this.cancelled = cancelled;
            }

            LocalDateTime at(int minute) {
                return day.atTime(HistorySimulation.DEPARTURE_HOURS.get(hourIndex), minute);
            }
        }

        /** Một lượt về tìm được: khung, phút khởi hành, và lượt chạy. */
        private record Candidate(SlotKey key, int minute, Leg leg) {
        }

        Dispatcher(List<Route> routes, Map<Long, RouteProfile> profiles, List<Bus> buses, List<Driver> drivers,
                HistorySimulation simulation, Map<LocalDate, Occupied> occupied) {
            this.routes = routes.stream().sorted(Comparator.comparing(Route::getId)).toList();
            this.profiles = profiles;
            this.buses = buses;
            this.drivers = drivers;
            this.simulation = simulation;
            this.occupied = occupied;
            for (Route route : this.routes) {
                fromOf.put(route.getId(), fromStation(route));
                toOf.put(route.getId(), toStation(route));
            }
            for (Route route : this.routes) {
                Route reverse = partnerOf(route, this.routes);
                if (reverse != null) {
                    partner.put(route.getId(), reverse);
                }
            }
            for (Bus bus : buses) {
                busHome.put(bus.getId(), homeOf(bus));
                busHomes.add(homeOf(bus));
            }
            for (Driver driver : drivers) {
                driverHome.put(driver.getUserId(), homeOf(driver));
            }
        }

        /**
         * Kế hoạch của một ngày. Theo từng giờ khởi hành: (1) chuyến thường của mọi khung giờ
         * đó; (2) xe gửi lên trước cho chiều về; (3) chuyến tăng cường nhà xe muốn, khung có
         * nhu cầu kỳ vọng cao nhất trước, mỗi vòng thêm tối đa một chuyến cho mỗi khung.
         */
        List<PlannedSlot> planDay(LocalDate day) {
            int dayIndex = (int) ChronoUnit.DAYS.between(HistorySimulation.EPOCH, day);
            List<Bus> busOrder = rotate(buses, dayIndex * 7);
            List<Driver> driverOrder = rotate(drivers, dayIndex * 11);

            Map<SlotKey, Draft> drafts = new LinkedHashMap<>();
            for (Route route : routes) {
                RouteProfile profile = profiles.get(route.getId());
                boolean closed = simulation.isClosed(route.getId(), day);
                for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                    SlotKey key = new SlotKey(route.getId(), day, h);
                    Draft draft = new Draft(route, day, h, simulation.mean(profile, day, h),
                            simulation.demand(profile, day, h), closed,
                            closed || simulation.isRandomlyCancelled(route.getId(), day, h));
                    List<Booking> pre = booked.remove(key);
                    if (pre != null) {
                        draft.extras.addAll(pre);
                    }
                    drafts.put(key, draft);
                }
            }
            today = drafts;

            Random order = simulation.slotOrder(day);
            for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                List<Draft> atHour = new ArrayList<>();
                for (Draft draft : drafts.values()) {
                    if (draft.hourIndex == h) {
                        atHour.add(draft);
                    }
                }
                Collections.shuffle(atHour, order);
                Set<Long> heldBuses = new HashSet<>();
                Set<Long> heldDrivers = new HashSet<>();
                for (Draft draft : atHour) {
                    assignRegular(draft, busOrder, driverOrder, heldBuses, heldDrivers);
                }
                for (Draft draft : atHour) {
                    sendOuts(draft, busOrder, driverOrder, heldBuses, heldDrivers);
                }
                wishedExtras(atHour, busOrder, driverOrder, heldBuses, heldDrivers);
            }
            today = Map.of();

            List<PlannedSlot> plan = new ArrayList<>();
            for (Draft draft : drafts.values()) {
                plan.add(toPlannedSlot(draft));
            }
            return plan;
        }

        /**
         * Nối tiếp một lần chạy trước (chạy bổ sung): nạp lượt gần nhất của từng xe, tài xế
         * và số phút đã lái của các chuyến vắt qua ngày firstDay. Xe đang ở bến xa mà lượt cuối
         * là chuyến tăng cường, hay vừa có chuyến bị hủy ở đó, được xếp lượt về ngay (H1) — lần
         * chạy liền cũng làm đúng vậy, chỉ là lượt về ấy rơi vào phần chưa ghi.
         *
         * @return số xe được xếp lượt về
         */
        int resume(Collection<GeneratedLeg> legs, LocalDate firstDay) {
            Map<Long, GeneratedLeg> lastLegOfBus = new HashMap<>();
            Map<Long, GeneratedLeg> lastRecordOfBus = new HashMap<>();
            Comparator<GeneratedLeg> byArrival = Comparator.comparing(GeneratedLeg::arrival)
                    .thenComparing(GeneratedLeg::departure);
            for (GeneratedLeg g : legs) {
                if (!fromOf.containsKey(g.routeId())) {
                    continue;
                }
                if (g.busId() != null) {
                    lastRecordOfBus.merge(g.busId(), g,
                            (a, b) -> b.departure().isAfter(a.departure()) ? b : a);
                }
                if (g.cancelled()) {
                    continue;
                }
                Leg leg = new Leg(fromOf.get(g.routeId()), toOf.get(g.routeId()), g.departure(), g.arrival());
                if (g.busId() != null) {
                    lastLegOfBus.merge(g.busId(), g, (a, b) -> byArrival.compare(b, a) > 0 ? b : a);
                    busLast.merge(g.busId(), leg, (a, b) -> b.arrival().isAfter(a.arrival()) ? b : a);
                }
                if (g.driverId() != null) {
                    driverLast.merge(g.driverId(), leg, (a, b) -> b.arrival().isAfter(a.arrival()) ? b : a);
                    if (g.arrival().isAfter(firstDay.atStartOfDay())) {
                        Map<LocalDate, Integer> minutes = driverMinutes.computeIfAbsent(g.driverId(), id -> new HashMap<>());
                        minutesByDay(g.departure(), g.arrival()).forEach((d, m) -> {
                            if (!d.isBefore(firstDay)) {
                                minutes.merge(d, m, Integer::sum);
                            }
                        });
                    }
                }
            }

            Map<Long, Driver> driverById = new HashMap<>();
            drivers.forEach(d -> driverById.put(d.getUserId(), d));
            LocalDateTime after = firstDay.atStartOfDay().minusMinutes(1);
            int rebooked = 0;
            for (Bus bus : buses) {
                GeneratedLeg last = lastLegOfBus.get(bus.getId());
                if (last == null) {
                    continue;
                }
                Long at = toOf.get(last.routeId());
                if (at.equals(busHome.get(bus.getId()))) {
                    continue;
                }
                GeneratedLeg record = lastRecordOfBus.get(bus.getId());
                boolean cancelledThere = record != null && record.cancelled()
                        && at.equals(fromOf.get(record.routeId())) && record.departure().isAfter(last.departure());
                GeneratedLeg owner = cancelledThere ? record : last.extra() ? last : null;
                Driver driver = owner != null && owner.driverId() != null ? driverById.get(owner.driverId()) : null;
                Leg driverLeg = driver != null ? driverLast.get(driver.getUserId()) : null;
                if (driverLeg == null || !at.equals(driverLeg.toStationId())) {
                    continue;
                }
                Candidate back = findReturn(bus, driver, busLast.get(bus.getId()), driverLeg, Map.of(), at, after);
                if (back != null) {
                    book(bus, driver, back);
                    rebooked++;
                }
            }
            return rebooked;
        }

        // ---------------------------------------------------------------------
        // Chuyến thường
        // ---------------------------------------------------------------------

        private void assignRegular(Draft draft, List<Bus> busOrder, List<Driver> driverOrder, Set<Long> heldBuses,
                Set<Long> heldDrivers) {
            Route route = draft.route;
            Long from = fromOf.get(route.getId());
            LocalDateTime departure = draft.at(0);
            LocalDateTime arrival = departure.plusMinutes(route.getEstimatedDuration());
            Integer preferredType = route.getSuitableBusType() != null ? route.getSuitableBusType().getId() : null;

            List<Bus> candidates = busOrder.stream()
                    .filter(b -> !heldBuses.contains(b.getId())
                            && busCanRun(b, busLast.get(b.getId()), from, departure, arrival))
                    .toList();
            Bus bus = candidates.stream()
                    .filter(b -> preferredType != null && b.getBusType() != null
                            && preferredType.equals(b.getBusType().getId()))
                    .min(busReady())
                    .orElse(candidates.stream().min(busReady()).orElse(null));
            Driver driver = bus == null ? null : driverOrder.stream()
                    .filter(d -> !heldDrivers.contains(d.getUserId())
                            && driverCanRun(d, driverLast.get(d.getUserId()), Map.of(), from, departure, arrival))
                    .min(driverReady())
                    .orElse(null);

            if (bus != null && driver != null) {
                draft.regular = new Booking(bus, driver, 0);
                heldBuses.add(bus.getId());
                heldDrivers.add(driver.getUserId());
                if (!draft.cancelled) {
                    commit(bus, driver, new Leg(from, toOf.get(route.getId()), departure, arrival));
                } else if (!from.equals(busHome.get(bus.getId()))) {
                    // Chuyến bị hủy không chạy: xe và tài xế đứng yên. Ở bến xa thì họ về ngay
                    // ở chuyến kế tiếp (H1), để số xe ở bến xa không trôi theo các lần hủy.
                    Candidate back = findReturn(bus, driver, busLast.get(bus.getId()),
                            driverLast.get(driver.getUserId()), Map.of(), from, departure);
                    if (back != null) {
                        book(bus, driver, back);
                    }
                }
            } else if (!draft.extras.isEmpty()) {
                // Không có xe nào khác ở bến: chuyến đặt trước sẵn ở khung này làm chuyến thường.
                draft.regular = draft.extras.remove(0);
            }
        }

        // ---------------------------------------------------------------------
        // Chuyến tăng cường
        // ---------------------------------------------------------------------

        /**
         * Gửi xe lên trước cho chiều về (QĐ-21 (d)): khung draft là chiều đi từ bến có xe rảnh;
         * các khung chiều về của tuyến cặp (hôm nay và ngày mai) mà nhà xe muốn thêm chuyến,
         * chưa đủ xe, được chia cho các khung chiều đi MUỘN NHẤT còn kịp (mỗi khung tối đa 3
         * chuyến thêm). Phần chia đúng vào khung này thì gửi xe ngay bây giờ.
         */
        private void sendOuts(Draft draft, List<Bus> busOrder, List<Driver> driverOrder, Set<Long> heldBuses,
                Set<Long> heldDrivers) {
            Route out = draft.route;
            Route back = partner.get(out.getId());
            if (draft.cancelled || back == null || !busHomes.contains(fromOf.get(out.getId()))
                    || busHomes.contains(fromOf.get(back.getId()))) {
                return;
            }
            LocalDateTime now = draft.at(0);
            SlotKey here = new SlotKey(out.getId(), draft.day, draft.hourIndex);

            List<SlotKey> feeders = new ArrayList<>();
            Map<SlotKey, List<Integer>> feederMinutes = new HashMap<>();
            List<SlotKey> targets = new ArrayList<>();
            for (LocalDate day = draft.day; !day.isAfter(draft.day.plusDays(1)); day = day.plusDays(1)) {
                if (day.isAfter(simulation.horizonEnd())) {
                    break;
                }
                for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                    LocalDateTime time = day.atTime(HistorySimulation.DEPARTURE_HOURS.get(h), 0);
                    SlotKey feeder = new SlotKey(out.getId(), day, h);
                    if (!time.isBefore(now) && !isCancelled(feeder)) {
                        feeders.add(feeder);
                        feederMinutes.put(feeder, new ArrayList<>(freeMinutes(feeder)));
                    }
                    SlotKey target = new SlotKey(back.getId(), day, h);
                    if (time.isAfter(now) && !isCancelled(target)) {
                        targets.add(target);
                    }
                }
            }

            List<SlotKey> fromHere = new ArrayList<>();
            for (SlotKey target : targets) {
                List<Integer> targetMinutes = freeMinutes(target);
                int wanted = Math.min(simulation.wishedExtraTrips(back.getId(), target.day()), EXTRA_MINUTES.length);
                int need = Math.min(wanted - extrasAt(target).size(), targetMinutes.size());
                for (int k = 0; k < need; k++) {
                    for (int f = feeders.size() - 1; f >= 0; f--) {
                        SlotKey feeder = feeders.get(f);
                        List<Integer> minutes = feederMinutes.get(feeder);
                        if (!minutes.isEmpty() && fitsOneCrew(out, feeder, minutes.get(0), back, target,
                                targetMinutes.get(k))) {
                            minutes.remove(0);
                            if (feeder.equals(here)) {
                                fromHere.add(target);
                            }
                            break;
                        }
                    }
                }
            }
            for (SlotKey target : fromHere) {
                if (!sendOut(draft, target, busOrder, driverOrder, heldBuses, heldDrivers)) {
                    return; // bến hết xe hoặc tài xế rảnh
                }
            }
        }

        /**
         * Một tổ xe (một xe, một tài xế chưa lái gì) có chạy được lượt đi ở khung feeder rồi lượt về
         * ở khung target không — chỉ theo giờ giấc, chưa xét xe hay người cụ thể.
         */
        private boolean fitsOneCrew(Route out, SlotKey feeder, int feederMinute, Route back, SlotKey target,
                int targetMinute) {
            LocalDateTime dep1 = slotTime(feeder, feederMinute);
            LocalDateTime arr1 = dep1.plusMinutes(out.getEstimatedDuration());
            LocalDateTime dep2 = slotTime(target, targetMinute);
            LocalDateTime arr2 = dep2.plusMinutes(back.getEstimatedDuration());
            if (arr1.plus(BUS_REST).isAfter(dep2)) {
                return false;
            }
            Map<LocalDate, Integer> minutes = new HashMap<>(minutesByDay(dep1, arr1));
            minutesByDay(dep2, arr2).forEach((d, m) -> minutes.merge(d, m, Integer::sum));
            return minutes.values().stream().allMatch(m -> m <= MAX_DRIVING_MINUTES_PER_DAY);
        }

        private boolean sendOut(Draft draft, SlotKey target, List<Bus> busOrder, List<Driver> driverOrder,
                Set<Long> heldBuses, Set<Long> heldDrivers) {
            List<Integer> here = freeMinutes(new SlotKey(draft.route.getId(), draft.day, draft.hourIndex));
            List<Integer> there = freeMinutes(target);
            if (here.isEmpty() || there.isEmpty()) {
                return true; // khung đầy, bỏ qua lượt này nhưng vẫn xét lượt khác
            }
            Route out = draft.route;
            Route back = partner.get(out.getId());
            Long home = fromOf.get(out.getId());
            Long far = toOf.get(out.getId());
            LocalDateTime dep1 = draft.at(here.get(0));
            LocalDateTime arr1 = dep1.plusMinutes(out.getEstimatedDuration());
            LocalDateTime dep2 = slotTime(target, there.get(0));
            LocalDateTime arr2 = dep2.plusMinutes(back.getEstimatedDuration());
            Leg leg1 = new Leg(home, far, dep1, arr1);
            Leg leg2 = new Leg(far, home, dep2, arr2);
            for (Bus bus : freeBuses(busOrder, home, dep1, arr1, heldBuses)) {
                if (!busCanRun(bus, leg1, far, dep2, arr2)) {
                    continue;
                }
                for (Driver driver : crewAt(driverOrder, home, dep1, arr1, heldDrivers)) {
                    if (driverCanRun(driver, leg1, minutesByDay(dep1, arr1), far, dep2, arr2)) {
                        commit(bus, driver, leg1);
                        draft.extras.add(new Booking(bus, driver, here.get(0)));
                        book(bus, driver, new Candidate(target, there.get(0), leg2));
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Chuyến tăng cường nhà xe muốn ở các khung của một giờ (HistorySimulation.wishedExtraTrips),
         * chỉ ở khung chưa bị hủy, đã có chuyến chạy, và xuất phát từ bến có xe rảnh. Chuyến đặt
         * trước ở khung (xe về ngay, xe gửi lên trước) cũng chở khách tràn nên được tính vào ý
         * định. Bến hết xe hay tài xế thì dừng thêm chuyến ở bến đó trong giờ này.
         */
        private void wishedExtras(List<Draft> atHour, List<Bus> busOrder, List<Driver> driverOrder,
                Set<Long> heldBuses, Set<Long> heldDrivers) {
            List<Draft> wanting = atHour.stream()
                    .filter(d -> !d.cancelled && d.regular != null && busHomes.contains(fromOf.get(d.route.getId()))
                            && simulation.wishedExtraTrips(d.route.getId(), d.day) > 0)
                    .sorted(Comparator.comparingDouble((Draft d) -> d.mean).reversed()
                            .thenComparing(d -> d.route.getId())
                            .thenComparingInt(d -> d.hourIndex))
                    .toList();
            Set<Long> exhausted = new HashSet<>();
            for (int round = 1; round <= EXTRA_MINUTES.length; round++) {
                for (Draft draft : wanting) {
                    int wanted = Math.min(simulation.wishedExtraTrips(draft.route.getId(), draft.day),
                            EXTRA_MINUTES.length);
                    Long from = fromOf.get(draft.route.getId());
                    if (draft.extras.size() >= Math.min(round, wanted) || exhausted.contains(from)) {
                        continue;
                    }
                    if (!addOuting(draft, busOrder, driverOrder, heldBuses, heldDrivers)) {
                        exhausted.add(from);
                    }
                }
            }
        }

        /**
         * Một chuyến tăng cường từ bến nhà: xe rảnh lớn nhất, tài xế chờ lâu nhất. Tới bến xa thì
         * xe và tài xế về ngay ở chuyến chiều về sớm nhất cả hai kịp (H1); không tìm được lượt về
         * thì không chạy chuyến này.
         */
        private boolean addOuting(Draft draft, List<Bus> busOrder, List<Driver> driverOrder, Set<Long> heldBuses,
                Set<Long> heldDrivers) {
            List<Integer> minutes = freeMinutes(new SlotKey(draft.route.getId(), draft.day, draft.hourIndex));
            if (minutes.isEmpty()) {
                return true; // khung đã đủ 3 chuyến thêm — không phải hết xe
            }
            Route route = draft.route;
            Long from = fromOf.get(route.getId());
            Long to = toOf.get(route.getId());
            int minute = minutes.get(0);
            LocalDateTime departure = draft.at(minute);
            LocalDateTime arrival = departure.plusMinutes(route.getEstimatedDuration());
            Leg leg = new Leg(from, to, departure, arrival);
            for (Bus bus : freeBuses(busOrder, from, departure, arrival, heldBuses)) {
                for (Driver driver : crewAt(driverOrder, from, departure, arrival, heldDrivers)) {
                    Candidate back = to.equals(busHome.get(bus.getId())) ? null
                            : findReturn(bus, driver, leg, leg, minutesByDay(departure, arrival), to, draft.at(0));
                    if (back == null && !to.equals(busHome.get(bus.getId()))) {
                        continue;
                    }
                    commit(bus, driver, leg);
                    draft.extras.add(new Booking(bus, driver, minute));
                    if (back != null) {
                        book(bus, driver, back);
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Chuyến chiều về SỚM NHẤT (sau khung `after`) mà xe và tài xế cùng chạy được: tuyến từ bến
         * `at` về bến nhà của xe, khung không bị hủy, còn chỗ cho chuyến thêm. busLeg / driverLeg là
         * lượt gần nhất của họ tính cả lượt đang xét chưa cam kết; pending là số phút lái chưa cam kết.
         */
        private Candidate findReturn(Bus bus, Driver driver, Leg busLeg, Leg driverLeg,
                Map<LocalDate, Integer> pending, Long at, LocalDateTime after) {
            Long home = busHome.get(bus.getId());
            List<Route> ways = routes.stream()
                    .filter(r -> at.equals(fromOf.get(r.getId())) && home.equals(toOf.get(r.getId())))
                    .toList();
            if (ways.isEmpty()) {
                return null;
            }
            LocalDate first = after.toLocalDate();
            for (LocalDate day = first; !day.isAfter(first.plusDays(RETURN_SEARCH_DAYS)); day = day.plusDays(1)) {
                if (day.isAfter(simulation.horizonEnd())) {
                    return null;
                }
                for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                    if (!day.atTime(HistorySimulation.DEPARTURE_HOURS.get(h), 0).isAfter(after)) {
                        continue;
                    }
                    for (Route way : ways) {
                        SlotKey key = new SlotKey(way.getId(), day, h);
                        List<Integer> minutes = freeMinutes(key);
                        if (minutes.isEmpty() || isCancelled(key)) {
                            continue;
                        }
                        LocalDateTime departure = slotTime(key, minutes.get(0));
                        LocalDateTime arrival = departure.plusMinutes(way.getEstimatedDuration());
                        if (busCanRun(bus, busLeg, at, departure, arrival)
                                && driverCanRun(driver, driverLeg, pending, at, departure, arrival)) {
                            return new Candidate(key, minutes.get(0), new Leg(at, home, departure, arrival));
                        }
                    }
                }
            }
            return null;
        }

        // ---------------------------------------------------------------------
        // Luật cho từng xe, từng tài xế
        // ---------------------------------------------------------------------

        /** Xe có chạy được lượt [departure, arrival) từ bến from không, với lượt gần nhất last. */
        private boolean busCanRun(Bus bus, Leg last, Long from, LocalDateTime departure, LocalDateTime arrival) {
            if (heldByExistingTrip(bus.getId(), true, departure, arrival)
                    || (last != null && last.arrival().isAfter(departure))) {
                return false;
            }
            return LocationRule.canDepart(last, busHome.get(bus.getId()), from, departure, BUS_REST);
        }

        /**
         * Tài xế có lái được lượt [departure, arrival) từ bến from không: bằng còn hạn, đúng bến,
         * nghỉ đủ, và mỗi ngày lịch không quá 8 giờ — tính cả số phút đã cam kết lẫn pending.
         */
        private boolean driverCanRun(Driver driver, Leg last, Map<LocalDate, Integer> pending, Long from,
                LocalDateTime departure, LocalDateTime arrival) {
            Long id = driver.getUserId();
            if (!driver.isLicenseValid(departure.toLocalDate()) || heldByExistingTrip(id, false, departure, arrival)
                    || (last != null && last.arrival().isAfter(departure))
                    || !LocationRule.canDepart(last, driverHome.get(id), from, departure, DRIVER_REST)) {
                return false;
            }
            Map<LocalDate, Integer> done = driverMinutes.getOrDefault(id, Map.of());
            for (Map.Entry<LocalDate, Integer> e : minutesByDay(departure, arrival).entrySet()) {
                int total = done.getOrDefault(e.getKey(), 0) + pending.getOrDefault(e.getKey(), 0) + e.getValue();
                if (total > MAX_DRIVING_MINUTES_PER_DAY) {
                    return false;
                }
            }
            return true;
        }

        /** Chuyến có sẵn giữ xe / người trong mọi ngày lịch mà lượt [departure, arrival) chạm tới. */
        private boolean heldByExistingTrip(Long id, boolean bus, LocalDateTime departure, LocalDateTime arrival) {
            LocalDate last = arrival.isAfter(departure) ? arrival.minusNanos(1).toLocalDate() : departure.toLocalDate();
            for (LocalDate day = departure.toLocalDate(); !day.isAfter(last); day = day.plusDays(1)) {
                Occupied held = occupied.get(day);
                if (held != null && (bus ? held.busIds() : held.staffIds()).contains(id)) {
                    return true;
                }
            }
            return false;
        }

        /** Xe rảnh cho chuyến tăng cường: đang ở bến nhà của nó, chưa có lượt; xe lớn nhất trước. */
        private List<Bus> freeBuses(List<Bus> busOrder, Long station, LocalDateTime departure, LocalDateTime arrival,
                Set<Long> heldBuses) {
            return busOrder.stream()
                    .filter(b -> !heldBuses.contains(b.getId()) && station.equals(busHome.get(b.getId()))
                            && busCanRun(b, busLast.get(b.getId()), station, departure, arrival))
                    .sorted(Comparator.comparingInt((Bus b) -> -capacityOf(b)).thenComparing(busReady()))
                    .toList();
        }

        /** Tài xế đang ở bến, chạy được lượt này; chờ lâu nhất trước. */
        private List<Driver> crewAt(List<Driver> driverOrder, Long station, LocalDateTime departure,
                LocalDateTime arrival, Set<Long> heldDrivers) {
            return driverOrder.stream()
                    .filter(d -> !heldDrivers.contains(d.getUserId())
                            && driverCanRun(d, driverLast.get(d.getUserId()), Map.of(), station, departure, arrival))
                    .sorted(driverReady())
                    .toList();
        }

        /** Chờ lâu nhất trước: lượt gần nhất tới sớm nhất; chưa chạy lượt nào thì trước hết. */
        private Comparator<Bus> busReady() {
            return Comparator.comparing(b -> readyKey(busLast.get(b.getId())));
        }

        private Comparator<Driver> driverReady() {
            return Comparator.comparing(d -> readyKey(driverLast.get(d.getUserId())));
        }

        private static LocalDateTime readyKey(Leg last) {
            return last == null ? LocalDateTime.MIN : last.arrival();
        }

        private void commit(Bus bus, Driver driver, Leg leg) {
            busLast.put(bus.getId(), leg);
            driverLast.put(driver.getUserId(), leg);
            Map<LocalDate, Integer> minutes = driverMinutes.computeIfAbsent(driver.getUserId(), id -> new HashMap<>());
            minutesByDay(leg.departure(), leg.arrival()).forEach((d, m) -> minutes.merge(d, m, Integer::sum));
        }

        /** Cam kết lượt về và đặt nó làm chuyến tăng cường của khung tương ứng. */
        private void book(Bus bus, Driver driver, Candidate candidate) {
            commit(bus, driver, candidate.leg());
            extrasAt(candidate.key()).add(new Booking(bus, driver, candidate.minute()));
        }

        /** Danh sách chuyến thêm của một khung: của ngày đang xếp, hoặc đặt trước cho ngày sau. */
        private List<Booking> extrasAt(SlotKey key) {
            Draft draft = today.get(key);
            return draft != null ? draft.extras : booked.computeIfAbsent(key, k -> new ArrayList<>());
        }

        /** Các phút chuyến thêm còn trống của khung, tăng dần (chuyến đôn lên làm chuyến thường giữ phút của nó). */
        private List<Integer> freeMinutes(SlotKey key) {
            Set<Integer> used = new HashSet<>();
            Draft draft = today.get(key);
            List<Booking> list = draft != null ? draft.extras : booked.getOrDefault(key, List.of());
            list.forEach(b -> used.add(b.minute()));
            if (draft != null && draft.regular != null) {
                used.add(draft.regular.minute());
            }
            List<Integer> free = new ArrayList<>();
            for (int minute : EXTRA_MINUTES) {
                if (!used.contains(minute)) {
                    free.add(minute);
                }
            }
            return free;
        }

        private boolean isCancelled(SlotKey key) {
            return simulation.isClosed(key.routeId(), key.day())
                    || simulation.isRandomlyCancelled(key.routeId(), key.day(), key.hourIndex());
        }

        private static LocalDateTime slotTime(SlotKey key, int minute) {
            return key.day().atTime(HistorySimulation.DEPARTURE_HOURS.get(key.hourIndex()), minute);
        }

        /** Vé: lấp chuyến thường trước, phần tràn sang chuyến thêm theo thứ tự giờ đi. */
        private static PlannedSlot toPlannedSlot(Draft draft) {
            List<Booking> all = new ArrayList<>();
            if (draft.regular != null) {
                all.add(draft.regular);
            }
            draft.extras.stream().sorted(Comparator.comparingInt(Booking::minute)).forEach(all::add);
            int remaining = draft.demand;
            List<PlannedTrip> trips = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                Booking booking = all.get(i);
                int seats = capacityOf(booking.bus());
                int tickets = Math.min(remaining, seats);
                remaining -= tickets;
                trips.add(new PlannedTrip(booking.bus(), booking.driver(), seats, tickets, booking.minute(), i > 0));
            }
            return new PlannedSlot(draft.route, draft.hourIndex, draft.mean, draft.demand, draft.closed,
                    draft.cancelled, trips);
        }
    }

    /** Số phút của khoảng [start, end) rơi vào từng ngày lịch — chuyến qua đêm tách tại 00:00. */
    static Map<LocalDate, Integer> minutesByDay(LocalDateTime start, LocalDateTime end) {
        Map<LocalDate, Integer> minutes = new TreeMap<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime midnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime stop = end.isBefore(midnight) ? end : midnight;
            minutes.merge(cursor.toLocalDate(), (int) ChronoUnit.MINUTES.between(cursor, stop), Integer::sum);
            cursor = stop;
        }
        return minutes;
    }

    /**
     * Mỗi chuyến có sẵn chiếm xe và người của nó trong MỌI ngày lịch mà khoảng
     * [departure, end) chạm tới: chuyến qua đêm chiếm cả hai ngày, còn chuyến đến đúng 00:00
     * thì không chạm sang ngày sau. Giữ cả ngày chứ không chỉ giờ trùng: chuyến có sẵn nằm
     * ngoài vòng xe của bộ sinh, nên hôm đó bộ sinh để yên người và xe của nó.
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

    /** Hồ sơ mô phỏng của các tuyến được sinh, kèm tuyến chiều ngược của từng tuyến (QĐ-21 (a)). */
    static Map<Long, RouteProfile> profilesOf(List<Route> routes) {
        Map<Long, RouteProfile> profiles = new HashMap<>();
        for (Route route : routes) {
            int seats = route.getSuitableBusType() != null && route.getSuitableBusType().getCapacity() > 0
                    ? route.getSuitableBusType().getCapacity()
                    : DEFAULT_SEATS;
            Route reverse = partnerOf(route, routes);
            profiles.put(route.getId(), new RouteProfile(route.getId(), route.getHolidayFlow(), seats,
                    reverse != null ? reverse.getId() : null));
        }
        return profiles;
    }

    /** Tuyến chiều ngược trong danh sách: bến đi và bến đến đổi chỗ; nhiều tuyến thì lấy id nhỏ nhất. */
    static Route partnerOf(Route route, List<Route> routes) {
        Long from = fromStation(route);
        Long to = toStation(route);
        if (from == null || to == null) {
            return null;
        }
        return routes.stream()
                .filter(r -> !r.getId().equals(route.getId()) && to.equals(fromStation(r)) && from.equals(toStation(r)))
                .min(Comparator.comparing(Route::getId))
                .orElse(null);
    }

    private static Long fromStation(Route route) {
        Station station = route.getDepartureStation();
        return station != null ? station.getId() : null;
    }

    private static Long toStation(Route route) {
        Station station = route.getDestinationStation();
        return station != null ? station.getId() : null;
    }

    private static Long homeOf(Bus bus) {
        return bus.getHomeStation() != null ? bus.getHomeStation().getId() : null;
    }

    private static Long homeOf(Driver driver) {
        return driver.getHomeStation() != null ? driver.getHomeStation().getId() : null;
    }

    // =========================================================================
    // DB
    // =========================================================================

    /**
     * Các tuyến trong backfill.route-ids, kiểm đủ điều kiện: có trong DB, có thời lượng và
     * một tài xế chạy trọn được trong hạn mức ngày, có bến đi / bến đến, và có chiều ngược
     * cùng danh sách. Sai một điều thì DỪNG cả lần chạy (trả null) và in lý do.
     */
    private List<Route> selectedRoutes() {
        if (routeIds == null || routeIds.isEmpty()) {
            System.out.println("[Backfill] DỪNG — backfill.route-ids trống: không biết sinh tuyến nào.");
            return null;
        }
        // findAllWithStations(): suitableBusType và lộ trình được dùng ngoài session.
        Map<Long, Route> all = new HashMap<>();
        routeRepository.findAllWithStations().forEach(r -> all.put(r.getId(), r));
        List<Route> routes = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Long id : new TreeSet<>(routeIds)) {
            Route route = all.get(id);
            if (route == null) {
                problems.add("#" + id + " không có trong DB");
                continue;
            }
            if (route.getEstimatedDuration() == null || route.getEstimatedDuration() <= 0
                    || route.getEstimatedDuration() > MAX_ROUTE_DURATION_MINUTES) {
                problems.add("#" + id + " dài " + route.getEstimatedDuration() + " phút (cần 1–"
                        + MAX_ROUTE_DURATION_MINUTES + ")");
            }
            if (fromStation(route) == null || toStation(route) == null) {
                problems.add("#" + id + " chưa có bến đi / bến đến");
            }
            routes.add(route);
        }
        for (Route route : routes) {
            Long from = fromStation(route);
            Long to = toStation(route);
            if (from == null || to == null) {
                continue;
            }
            long reverses = routes.stream()
                    .filter(r -> !r.getId().equals(route.getId())
                            && to.equals(fromStation(r)) && from.equals(toStation(r)))
                    .count();
            if (reverses != 1) {
                // Cặp tuyến suy ra bằng cách đảo bến đầu / cuối, phải khớp đúng một tuyến (đề xuất CM-6).
                problems.add("#" + route.getId() + (reverses == 0 ? " chưa có chiều ngược trong danh sách"
                        : " có " + reverses + " tuyến chiều ngược trong danh sách"));
            }
        }
        if (!problems.isEmpty()) {
            System.out.println("[Backfill] DỪNG — danh sách tuyến backfill.route-ids không hợp lệ: " + problems);
            return null;
        }
        return routes;
    }

    /**
     * Những ngày đã có chuyến DẠNG BỘ SINH (tuyến được sinh, giờ 06/12/18, phút 0 hoặc
     * phút của chuyến thêm). Nạp một lần thay vì hỏi từng khung.
     */
    private Set<LocalDate> existingGeneratorDays(List<Route> routes, LocalDate lastDay) {
        Set<LocalDate> days = new HashSet<>();
        List<Long> ids = routes.stream().map(Route::getId).toList();
        for (LocalDateTime departure : tripRepository.findDepartureTimesByRouteIdsBetween(ids,
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

    /**
     * Các chuyến dạng bộ sinh đã ghi trước ngày firstMissing — để lần chạy bổ sung biết xe và
     * tài xế đang ở đâu (Dispatcher.resume). Hai truy vấn scalar dùng lại findResourceUseBetween:
     * một cho chuyến đã chạy, một cho chuyến bị hủy. Chuyến thêm nhận ra qua phút khởi hành ≠ 0.
     */
    private List<GeneratedLeg> loadGeneratorLegs(List<Route> routes, LocalDate firstMissing) {
        Set<Long> ids = new HashSet<>();
        routes.forEach(r -> ids.add(r.getId()));
        Map<Long, GeneratedLeg> legs = new LinkedHashMap<>();
        for (boolean cancelled : new boolean[] { false, true }) {
            Collection<TripStatus> statuses = cancelled ? EnumSet.of(TripStatus.CANCELLED) : OCCUPYING_STATUSES;
            for (Object[] row : tripRepository.findResourceUseBetween(statuses,
                    HistorySimulation.EPOCH.atStartOfDay(), firstMissing.atStartOfDay())) {
                LocalDateTime departure = (LocalDateTime) row[1];
                Long routeId = (Long) row[3];
                if (departure == null || !ids.contains(routeId) || !hasGeneratorTime(departure)) {
                    continue;
                }
                LocalDateTime arrival = row[2] != null ? (LocalDateTime) row[2]
                        : departure.plusMinutes((Integer) row[4]);
                legs.putIfAbsent((Long) row[0], new GeneratedLeg(routeId, departure, arrival, (Long) row[5],
                        (Long) row[6], departure.getMinute() != 0, cancelled));
            }
        }
        return new ArrayList<>(legs.values());
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
