package giang.com.BusManagement.config;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * PHASE 5 — Sinh dữ liệu chuyến xe LỊCH SỬ để Demand Forecast (Phase 6) có cái
 * mà học.
 *
 * Vì sao cần: dữ liệu vận hành thật không thể tích lũy kịp trong thời gian làm
 * đồ án, nên bộ dữ liệu lịch sử được sinh tổng hợp. Đây là dữ liệu MÔ PHỎNG và
 * phải được nói rõ như vậy khi bảo vệ — không được trình bày như số liệu thật.
 *
 * ====================================================================
 * CÁCH CHẠY
 * ====================================================================
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill
 *
 * Profile `backfill` KHÔNG kích hoạt `demo`, nên DataInitializer (@Profile
 * ("demo")) không chạy và KHÔNG có gì bị xóa. application.properties mặc định
 * (ddl-auto=update) vẫn được áp dụng như thường.
 *
 * TUYỆT ĐỐI KHÔNG chạy kèm profile `demo` — profile đó dùng
 * ddl-auto=create-drop và sẽ xóa sạch schema trước khi bean này kịp chạy.
 *
 * ====================================================================
 * CHẠY LẠI NHIỀU LẦN ĐƯỢC (idempotent) — không nhân bản dữ liệu
 * ====================================================================
 * Mỗi ô dữ liệu là một cặp (tuyến × mốc khởi hành) sinh ra một cách TẤT ĐỊNH.
 * Trước khi tạo, bean hỏi tripRepository.existsByRouteIdAndDepartureTime(...);
 * ô nào đã có thì bỏ qua. Chạy lần thứ hai sẽ chèn 0 dòng. Nhờ vậy không cần
 * thêm cột "đây là dữ liệu backfill" vào Trip — đúng nguyên tắc không để Trip
 * phình thêm trách nhiệm.
 *
 * ====================================================================
 * VÌ SAO GHI THẲNG QUA REPOSITORY, KHÔNG QUA TripService
 * ====================================================================
 * createManualTrip() kiểm tra ràng buộc theo TRẠNG THÁI HIỆN TẠI: xe có đang
 * TRAVELING không, odometer đã tới ngưỡng bảo trì chưa... Những câu hỏi đó vô
 * nghĩa với một chuyến của ba tháng trước, và sẽ khiến gần như toàn bộ dữ liệu
 * lịch sử bị từ chối. DataInitializer đã có tiền lệ: dữ liệu seed ghi thẳng qua
 * repository.
 *
 * Bù lại, dữ liệu sinh ra vẫn TÔN TRỌNG các quy tắc nghiệp vụ theo cách dựng
 * sẵn (xem generateForDay): mỗi tài xế tối đa 1 chuyến/ngày và mỗi xe tối đa 1
 * chuyến/ngày, cộng với việc chỉ chọn tuyến dài không quá 8h. Nhờ đó không có
 * chuyến nào trùng lịch xe/tài xế, và không tài xế nào vượt hạn mức 8h/ngày —
 * tức bộ dữ liệu không bịa ra những tình huống mà chính hệ thống coi là sai.
 *
 * ====================================================================
 * ODOMETER: CỘNG CẢ HAI MỐC, GIỮ NGUYÊN kmSinceLastMaintenance
 * ====================================================================
 * Ở luồng thật, FSM cộng quãng đường tuyến vào Bus.odometer khi chuyến
 * DEPARTED → COMPLETED. Nếu bỏ qua, 1.260 chuyến hoàn thành (~195.000 km) sẽ
 * không để lại dấu vết nào trên odometer, và Phase 7 (Vehicle Replacement —
 * xếp hạng theo odometer trọn đời) sẽ chấm điểm trên số liệu seed không liên
 * quan gì tới bộ dữ liệu lịch sử.
 *
 * Nhưng cộng thẳng vào odometer thôi thì HỎNG HỆ THỐNG: ~10.300 km/xe trong
 * khi maintenanceThreshold trung bình chỉ 5.000 km ⇒ needsMaintenance() đúng
 * với TOÀN BỘ đội xe ⇒ findBestAvailableBus() từ chối mọi xe ⇒ điều phối và AI
 * auto-assign đứng hình.
 *
 * Cách làm đúng, và là cách bean này dùng: cộng CÙNG một lượng km vào CẢ
 * odometer LẪN lastMaintenanceOdometer. Vì
 * kmSinceLastMaintenance = odometer - lastMaintenanceOdometer, hiệu số này
 * KHÔNG ĐỔI — needsMaintenance(), isNearMaintenance(), cảnh báo bảo dưỡng trên
 * Dashboard và thứ tự ưu tiên trong findBestAvailableBus() đều giữ nguyên hành
 * vi cũ. Về mặt nghiệp vụ, điều này mô phỏng đúng chuyện xe có chạy nhiều thì
 * cũng đã được bảo dưỡng định kỳ tương ứng trong 12 tuần đó.
 *
 * Chỉ chuyến COMPLETED mới cộng km — đúng như FSM; chuyến CANCELLED không chạy.
 * Chỉ cộng km của những chuyến do LẦN CHẠY NÀY tạo ra, nên chạy lại (tạo 0
 * chuyến) cũng cộng 0 km: tính idempotent của odometer đi kèm luôn với tính
 * idempotent của dữ liệu, không cần cột đánh dấu riêng.
 *
 * ====================================================================
 * TRỤC THỜI GIAN LÀ departureTime — KHÔNG PHẢI createdAt
 * ====================================================================
 * createdAt do @CreationTimestamp đóng dấu lúc INSERT và cột này
 * updatable = false, nên mọi dòng backfill đều mang ngày CHẠY SCRIPT ở
 * createdAt. Điều đó là bình thường và vô hại: mọi truy vấn theo thời gian đều
 * dùng departureTime. TUYỆT ĐỐI KHÔNG "sửa" bằng native SQL để lùi createdAt —
 * đó là làm sai lệch một mốc kỹ thuật để phục vụ việc mà departureTime đã làm
 * đúng (xem THESIS_ROADMAP.md, Developer Notes).
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

    /** Độ dài bộ dữ liệu lịch sử: 12 tuần, đủ để thấy cả xu hướng lẫn chu kỳ tuần. */
    private static final int BACKFILL_DAYS = 84;

    /**
     * Các khung giờ khởi hành trong ngày. Đây chính là "time slot" mà Phase 6 sẽ
     * gom nhóm để dự báo, nên chúng cố định và rời rạc chứ không rải ngẫu nhiên.
     */
    private static final List<Integer> DEPARTURE_HOURS = List.of(6, 12, 18);

    /**
     * Chỉ backfill các tuyến mà MỘT tài xế chạy trọn được trong hạn mức ngày.
     *
     * Giá trị 8h khai báo lại tại đây theo đúng lý do đã ghi ở
     * DriverRecommendationService: trong TripService số 8.0 mang ba ý nghĩa khác
     * nhau, không gom được thành một hằng số chung. Ở đây nó phản chiếu hạn mức
     * giờ lái/ngày — nếu tuyến dài hơn thế thì chuyến bắt buộc phải có tài xế phụ
     * và phụ xe, và bộ dữ liệu sẽ phải bịa thêm nhân sự cho từng chuyến.
     * Dữ liệu thật hiện có vài tuyến 30h/50h/75h (rõ ràng là dữ liệu thử), các
     * tuyến đó bị loại khỏi backfill.
     */
    private static final int MAX_ROUTE_DURATION_MINUTES = 8 * 60;

    /** Tỉ lệ chuyến bị hủy — có để Phase 6 buộc phải lọc theo trạng thái. */
    private static final double CANCELLED_RATE = 0.06;

    /** Chuyến được mở bán trước ngày chạy bao lâu (mốc nghiệp vụ, không phải audit). */
    private static final int SALE_OPENS_DAYS_BEFORE = 30;

    /** Seed cố định: chạy lại trên máy khác vẫn ra đúng bộ số liệu đó. */
    private static final long RANDOM_SEED = 20260720L;

    private static final int DEFAULT_SEATS = 40;

    @Override
    public void run(String... args) {
        System.out.println("========================================");
        System.out.println("[Backfill] Phase 5 — sinh dữ liệu chuyến LỊCH SỬ (dữ liệu MÔ PHỎNG)");

        List<Route> routes = eligibleRoutes();
        // findAllWithBusType() chứ không phải findAll(): CommandLineRunner chạy
        // NGOÀI session Hibernate (open-in-view chỉ áp dụng cho request web), nên
        // bus.getBusType() lazy sẽ ném LazyInitializationException khi lấy số ghế.
        List<Bus> buses = busRepository.findAllWithBusType().stream()
                .sorted(Comparator.comparing(Bus::getId))
                .toList();
        List<Driver> drivers = driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .sorted(Comparator.comparing(Driver::getUserId))
                .toList();

        int tripsPerDay = routes.size() * DEPARTURE_HOURS.size();

        if (routes.isEmpty() || buses.isEmpty() || drivers.isEmpty()) {
            System.out.printf("[Backfill] DỪNG — thiếu dữ liệu nền (tuyến hợp lệ=%d, xe=%d, tài xế=%d).%n",
                    routes.size(), buses.size(), drivers.size());
            return;
        }
        // Bất biến giữ cho dữ liệu không tự mâu thuẫn: trong một ngày, không tài xế
        // nào và không xe nào bị dùng hai lần.
        if (tripsPerDay > drivers.size() || tripsPerDay > buses.size()) {
            System.out.printf("[Backfill] DỪNG — %d chuyến/ngày vượt quá số tài xế (%d) hoặc số xe (%d); "
                    + "sinh tiếp sẽ tạo ra trùng lịch. Giảm số tuyến hoặc số khung giờ.%n",
                    tripsPerDay, drivers.size(), buses.size());
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(BACKFILL_DAYS);
        System.out.printf("[Backfill] Cửa sổ: %s → %s | %d tuyến × %d khung giờ = %d chuyến/ngày%n",
                firstDay, today.minusDays(1), routes.size(), DEPARTURE_HOURS.size(), tripsPerDay);

        Random random = new Random(RANDOM_SEED);
        int created = 0;
        int skipped = 0;
        // km cần cộng vào odometer, gom theo xe — CHỈ từ các chuyến do lần chạy này
        // tạo ra, nên chạy lại sẽ cộng 0.
        Map<Long, Double> kmByBusId = new HashMap<>();

        for (int dayIndex = 0; dayIndex < BACKFILL_DAYS; dayIndex++) {
            LocalDate day = firstDay.plusDays(dayIndex);
            List<Trip> batch = generateForDay(day, dayIndex, routes, buses, drivers, tripsPerDay, random);
            skipped += tripsPerDay - batch.size();
            if (!batch.isEmpty()) {
                tripRepository.saveAll(batch);
                created += batch.size();
                accumulateOdometerKm(batch, kmByBusId);
            }
        }

        int busesUpdated = applyOdometer(kmByBusId);

        System.out.printf("[Backfill] XONG — đã tạo %d chuyến, bỏ qua %d ô đã có sẵn.%n", created, skipped);
        System.out.printf("[Backfill] Odometer: cộng %.0f km cho %d xe (kèm lastMaintenanceOdometer, "
                + "nên kmSinceLastMaintenance không đổi).%n",
                kmByBusId.values().stream().mapToDouble(Double::doubleValue).sum(), busesUpdated);
        if (created == 0) {
            System.out.println("[Backfill] Không có gì mới: bộ dữ liệu đã được sinh trước đó (idempotent).");
        }
        System.out.println("[Backfill] LƯU Ý: createdAt của các dòng này là ngày chạy script, không phải ngày "
                + "khởi hành — trục thời gian của dataset là departureTime.");
        System.out.println("========================================");
    }

    /**
     * Gom quãng đường của các chuyến ĐÃ HOÀN THÀNH theo xe.
     *
     * Chỉ COMPLETED, đúng như FSM (updateTripStatus chỉ cộng odometer ở bước
     * DEPARTED → COMPLETED) — chuyến CANCELLED không hề chạy nên không cộng km.
     */
    private void accumulateOdometerKm(List<Trip> batch, Map<Long, Double> kmByBusId) {
        for (Trip trip : batch) {
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
     * Cộng km đã gom vào odometer VÀ lastMaintenanceOdometer của từng xe.
     *
     * Cộng cùng một lượng vào cả hai là điểm mấu chốt: hiệu số
     * kmSinceLastMaintenance giữ nguyên, nên mọi quy tắc bảo dưỡng hiện có
     * (needsMaintenance, isNearMaintenance, cảnh báo Dashboard, thứ tự ưu tiên
     * của findBestAvailableBus) hành xử y hệt trước — chỉ có quãng đường trọn
     * đời là tăng lên thật.
     *
     * Xe nào thiếu một trong hai mốc (null) thì BỎ QUA: khi đó
     * getKmSinceLastMaintenance() đang trả 0.0 theo quy ước của entity, và cộng
     * vào chỉ một vế sẽ vô tình bịa ra một khoảng km kể từ lần bảo dưỡng cuối,
     * tức là làm thay đổi hành vi bảo dưỡng — đúng thứ cần tránh.
     *
     * @return số xe thực sự được cập nhật
     */
    private int applyOdometer(Map<Long, Double> kmByBusId) {
        if (kmByBusId.isEmpty()) {
            return 0;
        }
        List<Bus> updated = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : kmByBusId.entrySet()) {
            Bus bus = busRepository.findById(entry.getKey()).orElse(null);
            if (bus == null || bus.getOdometer() == null || bus.getLastMaintenanceOdometer() == null) {
                System.out.printf("[Backfill] Bỏ qua odometer của xe #%s (thiếu mốc odometer).%n", entry.getKey());
                continue;
            }
            double km = entry.getValue();
            bus.setOdometer(bus.getOdometer() + km);
            bus.setLastMaintenanceOdometer(bus.getLastMaintenanceOdometer() + km);
            updated.add(bus);
        }
        busRepository.saveAll(updated);
        return updated.size();
    }

    /**
     * Tuyến đủ điều kiện: có quãng đường/thời lượng, và một tài xế chạy trọn được
     * trong hạn mức ngày.
     */
    private List<Route> eligibleRoutes() {
        return routeRepository.findAll().stream()
                .filter(r -> r.getEstimatedDuration() != null && r.getEstimatedDuration() > 0)
                .filter(r -> r.getEstimatedDuration() <= MAX_ROUTE_DURATION_MINUTES)
                .sorted(Comparator.comparing(Route::getId))
                .toList();
    }

    /**
     * Sinh các chuyến của một ngày.
     *
     * Xe và tài xế được quay vòng theo chỉ số chạy liên tục qua các ngày. Vì số
     * chuyến/ngày luôn nhỏ hơn số xe và số tài xế (đã kiểm tra ở run()), các chỉ
     * số trong cùng một ngày luôn khác nhau — nên không tài xế/xe nào bị xếp hai
     * chuyến cùng ngày. Việc quay vòng qua các ngày cũng khiến tải trải đều thay
     * vì dồn vào vài người đầu danh sách.
     */
    private List<Trip> generateForDay(LocalDate day, int dayIndex, List<Route> routes, List<Bus> buses,
            List<Driver> drivers, int tripsPerDay, Random random) {

        List<Trip> batch = new ArrayList<>();
        int slotIndex = 0;

        for (Route route : routes) {
            for (int hour : DEPARTURE_HOURS) {
                LocalDateTime departure = day.atTime(hour, 0);
                int rotation = dayIndex * tripsPerDay + slotIndex;
                slotIndex++;

                // Rút số ngẫu nhiên TRƯỚC khi quyết định bỏ qua, để chuỗi ngẫu nhiên
                // không lệch giữa lần chạy đầu và các lần chạy lại — nhờ đó dataset
                // vẫn tất định dù chạy nhiều lần.
                double noise = 1.0 + (random.nextDouble() - 0.5) * 0.16;
                boolean cancelled = random.nextDouble() < CANCELLED_RATE;

                if (tripRepository.existsByRouteIdAndDepartureTime(route.getId(), departure)) {
                    continue;
                }

                Bus bus = buses.get(rotation % buses.size());
                Driver driver = drivers.get(rotation % drivers.size());

                batch.add(buildTrip(route, bus, driver, departure, dayIndex, noise, cancelled));
            }
        }
        return batch;
    }

    private Trip buildTrip(Route route, Bus bus, Driver driver, LocalDateTime departure,
            int dayIndex, double noise, boolean cancelled) {

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));

        int seats = (bus.getBusType() != null && bus.getBusType().getCapacity() > 0)
                ? bus.getBusType().getCapacity()
                : DEFAULT_SEATS;
        trip.setTotalSeats(seats);
        trip.setTicketsSold((int) Math.round(seats * occupancy(route, departure, dayIndex, noise)));

        trip.setPrice(priceFor(route));
        trip.setStatus(cancelled ? TripStatus.CANCELLED : TripStatus.COMPLETED);
        trip.setSaleOpenedAt(departure.minusDays(SALE_OPENS_DAYS_BEFORE));
        trip.setExtraTrip(false);
        return trip;
    }

    /**
     * Tỉ lệ lấp đầy mô phỏng — đây chính là "nhu cầu" mà Phase 6 sẽ dự báo.
     *
     * Bốn thành phần cố ý tách bạch để thuật toán dự báo có cái để phát hiện:
     * nền theo tuyến, hệ số theo khung giờ, chu kỳ cuối tuần, và một xu hướng
     * tăng nhẹ dần đều theo thời gian (moving average bắt được chu kỳ; linear
     * regression bắt được xu hướng).
     */
    private double occupancy(Route route, LocalDateTime departure, int dayIndex, double noise) {
        double base = 0.52 + (route.getId() % 4) * 0.07;

        double slotFactor = switch (departure.getHour()) {
            case 6 -> 1.05;
            case 12 -> 0.85;
            default -> 1.12;
        };

        DayOfWeek dow = departure.getDayOfWeek();
        double weekendFactor = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) ? 1.20 : 1.0;

        // Tăng trưởng tuyến tính ~15% trên toàn cửa sổ.
        double trendFactor = 1.0 + 0.15 * ((double) dayIndex / BACKFILL_DAYS);

        double value = base * slotFactor * weekendFactor * trendFactor * noise;
        return Math.max(0.15, Math.min(1.0, value));
    }

    /** Giá vé suy ra từ quãng đường, làm tròn tới 10.000đ cho giống dữ liệu thật. */
    private BigDecimal priceFor(Route route) {
        double km = route.getDistanceKm() != null ? route.getDistanceKm() : 100.0;
        long price = Math.round(km * 1000 / 10_000.0) * 10_000L;
        return BigDecimal.valueOf(Math.max(price, 50_000L));
    }
}
