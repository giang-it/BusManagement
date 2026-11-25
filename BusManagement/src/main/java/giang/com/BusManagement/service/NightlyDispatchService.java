package giang.com.BusManagement.service;

import giang.com.BusManagement.config.SimulatedOperations;
import giang.com.BusManagement.dto.NightlyRunDto;
import giang.com.BusManagement.dto.RegularTimetableRunDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JOB ĐÊM (route pairs CT8, QĐ-24; CM-10 cách T2). Bốn bước, mỗi bước một transaction riêng — bước nào lỗi
 * thì ghi lại, các bước sau vẫn chạy:
 * <ol>
 * <li>vận hành mô phỏng: đóng các chuyến đã chạy xong, vé mô phỏng ({@link SimulatedOperations}, CM-23 V4) — chỉ
 * khi bật {@code dispatch.nightly.simulate-operations};</li>
 * <li>lịch thường lệ luôn đủ tới D+7 ({@link RegularTimetableService#generate}, CM-14) — khung đã có chuyến thì bỏ
 * qua, ngày bảo trì CT7d tự xếp theo;</li>
 * <li>dự báo học lại (trong bước 4: ForecastService khớp lại khi dữ liệu đổi, dùng lại khi không);</li>
 * <li>vòng tăng cường chờ duyệt cho D+3…D+7, rút vòng không còn cần ({@link ForecastLoopService}).</li>
 * </ol>
 *
 * Ba cách kích hoạt: hẹn giờ ({@code dispatch.nightly.cron}, mặc định 01:00 — đã sang ngày mới, trước chuyến
 * 06:00), MỘT lần chạy bù khi app khởi động (app chạy trên máy cá nhân, đêm thường tắt), và nút "Chạy ngay" của màn
 * Đề xuất. Mọi bước chạy lại được an toàn, nên chạy hai lần một ngày không sao. Hẹn giờ và chạy bù chỉ chạy khi
 * {@code dispatch.nightly.enabled=true} — tắt trong test và khi chạy app để kiểm tra chỉ đọc trên DB thật; nút
 * bấm là thao tác chủ ý của Admin nên chạy cả khi tắt. Không bao giờ chạy hai lần chồng nhau.
 *
 * Job này KHÔNG reset giờ lái và không sửa hồ sơ bảo trì ngoài việc mô phỏng ngày bảo trì lịch đã xếp
 * (roadmap §4, sửa theo QĐ-24).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NightlyDispatchService {

    private final SimulatedOperations simulatedOperations;
    private final RegularTimetableService regularTimetableService;
    private final ForecastLoopService forecastLoopService;

    @Value("${dispatch.nightly.enabled:false}")
    private boolean enabled;

    @Value("${dispatch.nightly.simulate-operations:false}")
    private boolean simulateOperations;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile NightlyRunDto lastRun;

    @Scheduled(cron = "${dispatch.nightly.cron:0 0 1 * * *}")
    public void nightly() {
        if (enabled) {
            run("hẹn giờ");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (enabled) {
            run("chạy bù khi khởi động");
        }
    }

    /** Lần chạy gần nhất (null nếu app chưa chạy lần nào kể từ khi khởi động). */
    public NightlyRunDto lastRun() {
        return lastRun;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSimulatingOperations() {
        return simulateOperations;
    }

    /**
     * Chạy cả bốn bước một lần. Đang có lần chạy khác thì không chạy chồng, trả null.
     *
     * @param trigger ai kích hoạt, để ghi vào kết quả
     */
    public NightlyRunDto run(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.warn("🌙 [Job đêm] Đang có một lần chạy khác — bỏ qua lần '{}'", trigger);
            return null;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            long started = System.currentTimeMillis();
            NightlyRunDto result = new NightlyRunDto();
            result.setTrigger(trigger);
            result.setStartedAt(now.truncatedTo(ChronoUnit.SECONDS));
            log.info("🌙 [Job đêm] Bắt đầu ({})", trigger);

            if (simulateOperations) {
                step(result, "Vận hành mô phỏng", () -> {
                    SimulatedOperations.Result ops = simulatedOperations.closeFinishedTrips(now);
                    result.setSimulated(true);
                    result.setClosedTrips(ops.closedTrips());
                    result.setClosedSlots(ops.closedSlots());
                    result.setSimulatedTickets(ops.tickets());
                    result.setCancelledPending(ops.cancelledPending());
                    result.setServiced(ops.serviced());
                    result.setWaitingSlots(ops.waitingSlots());
                    if (ops.skipped() != null) {
                        result.getErrors().add("Vận hành mô phỏng: bỏ qua — " + ops.skipped());
                    }
                });
            }
            step(result, "Lịch thường lệ", () -> {
                RegularTimetableRunDto timetable = regularTimetableService.generate(LocalDate.now(),
                        RegularTimetableService.DEFAULT_DAYS + 1);
                result.setTimetableCreated(timetable.getCreated());
                result.setTimetablePending(timetable.getPending());
                result.setMaintenancePlanned(timetable.getMaintenancePlanned().size());
            });
            step(result, "Vòng dự báo", () -> {
                ForecastLoopService.Result loops = forecastLoopService.reconcile(LocalDateTime.now());
                result.setLoopsCreated(loops.created());
                result.setLoopsKept(loops.kept());
                result.setLoopsWithdrawnByForecast(loops.withdrawnByForecast());
                result.setLoopsWithdrawnByDeadline(loops.withdrawnByDeadline());
                result.setLoopsSkipped(loops.skipped());
                result.setLoopsRefusedSkipped(loops.refusedSkipped());
            });

            result.setElapsedMillis(System.currentTimeMillis() - started);
            lastRun = result;
            log.info("🌙 [Job đêm] Xong ({} ms): đóng {} chuyến, lịch +{} chuyến (+{} chờ phân công), vòng dự báo +{} / giữ {}"
                    + " / rút {}, lỗi {}", result.getElapsedMillis(), result.getClosedTrips(), result.getTimetableCreated(),
                    result.getTimetablePending(), result.getLoopsCreated().size(), result.getLoopsKept(),
                    result.getLoopsWithdrawnByForecast().size() + result.getLoopsWithdrawnByDeadline().size(),
                    result.getErrors().size());
            return result;
        } finally {
            running.set(false);
        }
    }

    /** Một bước trong transaction riêng của nó: lỗi thì ghi lại và đi tiếp, không làm hỏng các bước khác. */
    private static void step(NightlyRunDto result, String name, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            log.error("🌙 [Job đêm] Bước '{}' lỗi: {}", name, e.getMessage(), e);
            result.getErrors().add(name + ": " + e.getMessage());
        }
    }
}
