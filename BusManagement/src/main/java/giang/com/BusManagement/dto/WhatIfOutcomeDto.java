package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PHASE 8 — MỘT cột kết quả mô phỏng (dùng cho cả "hiện trạng" lẫn "kịch bản"),
 * để giao diện đặt hai cột cạnh nhau so sánh.
 *
 * Mọi con số ở đây là ước tính TỔNG HỢP, tính bằng số học trên các đại lượng
 * tổng (đội xe, tài xế, dự báo, tham số chi phí) — KHÔNG chạy lại engine phân
 * công. Xem honesty banner trên màn và Developer Notes trong THESIS_ROADMAP.md.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfOutcomeDto {

    // --- Nguồn lực trong kịch bản ---
    private int availableBuses;
    private int activeDrivers;
    /** Tổng ghế của đội xe khả dụng trong kịch bản (READY + Δxe × sức chứa TB). */
    private long totalSeats;

    // --- Năng lực suy ra ---
    private double busThroughputPerDay;
    private double driverThroughputPerDay;
    /** min(busThroughput, driverThroughput) — số chuyến/ngày đội xe kham được. */
    private double servablePerDay;
    /** Bên bó nhỏ hơn khi thiếu năng lực — "mua xe hay tuyển tài xế". */
    private WhatIfBottleneck bottleneck;

    // --- Nhu cầu vs độ phủ ---
    /** Số ngày-khung vượt ngưỡng tăng cường tại hệ số nhu cầu của kịch bản. */
    private int hotSlots;
    /**
     * Số khung phủ được, cộng dồn THEO TỪNG NGÀY:
     * {@code floor(Σ_ngày min(số khung đông của ngày, servablePerDay))}.
     *
     * Cố ý KHÔNG phải {@code min(hotSlots, servablePerDay × số ngày)} — công thức gộp
     * đó cho năng lực nhàn rỗi của ngày vắng "cho vay" sang ngày cao điểm, và vì nhu
     * cầu dự báo dồn vào cuối tuần nên nó báo đủ năng lực trong khi thực tế đang
     * thiếu. Xem javadoc của {@code WhatIfSimulationService} và
     * docs/todo/current_bugs_found.md mục #7.
     */
    private int coverable;
    /** hotSlots − coverable — số khung nhu cầu cao nhưng không phủ nổi. */
    private int gap;
    /** coverable ÷ hotSlots (%). null khi hotSlots = 0 (không có nhu cầu để phủ). */
    private Double coveragePct;

    // --- Tài chính của kế hoạch PHỦ ĐƯỢC (theo tham số chi phí của cột này) ---
    /** null nếu thiếu tham số/quãng đường cho mọi khung. */
    private BigDecimal estimatedCost;
    /** null nếu không khung nào có giá vé lịch sử để ước doanh thu. */
    private BigDecimal estimatedRevenue;
    /** revenue − cost; null khi thiếu doanh thu. Có thể âm (hiển thị đỏ). */
    private BigDecimal estimatedProfit;
}
