package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả một lần chạy job đêm (route pairs CT8, QĐ-24): ai kích hoạt, mỗi bước làm được gì, bước nào lỗi. Chỉ
 * giữ trong bộ nhớ (lần chạy gần nhất), hiện trên màn Đề xuất.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NightlyRunDto {

    /** "hẹn giờ 01:00", "chạy bù khi khởi động", "Admin bấm Chạy ngay". */
    private String trigger;
    private LocalDateTime startedAt;
    private long elapsedMillis;

    // ── Bước 1: vận hành mô phỏng (CM-23) ──
    /** Bước 1 có chạy không (công tắc dispatch.nightly.simulate-operations). */
    private boolean simulated;
    private int closedTrips;
    private int closedSlots;
    private int simulatedTickets;
    private int cancelledPending;
    private List<String> serviced = new ArrayList<>();
    private int waitingSlots;

    // ── Bước 2: lịch thường lệ luôn đủ tới D+7 (CM-14) ──
    private int timetableCreated;
    private int timetablePending;
    private int maintenancePlanned;

    // ── Bước 4: vòng dự báo (QĐ-24, CM-26…28) ──
    private List<String> loopsCreated = new ArrayList<>();
    private int loopsKept;
    private List<String> loopsWithdrawnByForecast = new ArrayList<>();
    private List<String> loopsWithdrawnByDeadline = new ArrayList<>();
    /** Lý do không tự tạo vòng (thiếu dữ liệu, dữ liệu cũ), hay null. */
    private String loopsSkipped;
    /** Thẻ đáng tạo nhưng không tạo vì Admin đã từ chối vòng dự báo của khung đó. */
    private int loopsRefusedSkipped;

    /** Mỗi bước lỗi một dòng: bước, lý do. Bước lỗi không chặn các bước sau. */
    private List<String> errors = new ArrayList<>();
}
