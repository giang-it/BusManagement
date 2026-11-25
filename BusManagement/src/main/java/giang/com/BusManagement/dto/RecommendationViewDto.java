package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * PHASE 7 — toàn bộ nội dung màn hình "Đề Xuất Tăng Cường" (bước 2: doanh thu;
 * bước 3: + chi phí/lợi nhuận và tham số chi phí đang áp dụng).
 *
 * Ngoài danh sách thẻ, DTO mang theo phạm vi/độ tươi của dữ liệu lịch sử (lấy
 * NGUYÊN từ cùng một DemandForecastViewDto đã dùng để sinh đề xuất) — vì đề
 * xuất chỉ đáng tin ngang với dự báo sinh ra nó, và dự báo lại dựa trên bộ dữ
 * liệu MÔ PHỎNG có mốc kết thúc cố định, mỗi ngày một cũ hơn. Che điều đó đi sẽ
 * khiến người xem tưởng là số liệu thật.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationViewDto {

    /**
     * Mỗi chuyến thêm một thẻ — trừ thẻ giữ xe (CT9), gánh hai chuyến thêm — xếp theo lợi nhuận ước tính
     * GIẢM DẦN (chuyến có lãi lên đầu).
     * Xe + tài xế đã được giữ chỗ giữa các thẻ, nên các thẻ "đủ điều kiện" chạy được cùng lúc
     * (GĐ6, QĐ-17).
     */
    private List<RecommendationCardDto> cards;

    // --- Bối cảnh forecast (chuyển tiếp từ DemandForecastViewDto) ---
    private LocalDate historyEnd;
    private long historyStaleDays;
    private LocalDate forecastFrom;
    private int horizonDays;

    // --- Số liệu tổng hợp ---
    /** Số ngày-khung được dự báo vượt ngưỡng tăng cường. */
    private int hotSlotCount;
    /**
     * Số chuyến thêm các khung đó còn cần (đã trừ chuyến có sẵn trong khung, CT7). Từ CT9 có thể lớn hơn số
     * thẻ: một thẻ giữ xe gánh hai chuyến thêm.
     */
    private int totalCandidates;
    private int recommendedCount;
    private int noResourceCount;
    /** Số thẻ có lợi nhuận ước tính (cả vòng, từ CT7) dương. */
    private int profitableCount;
    /** Số thẻ có lợi nhuận ước tính (cả vòng) âm ("lỗ dự kiến"). */
    private int lossCount;

    // --- Tham số chi phí đang áp dụng (Phase 7 bước 3) — để trang hiển thị minh
    // bạch "chi phí tính theo giá nào" và dẫn tới màn hình chỉnh. ---
    /** Chi phí nhiên liệu/km đang dùng (đồng). */
    private BigDecimal costFuelPerKm;
    /** Lương tài xế/giờ đang dùng (đồng). */
    private BigDecimal costWagePerHour;

    // --- Route pairs CT7 (cách 2a) ---
    /**
     * Số chuyến thêm mà các khung đông cần nhưng ĐÃ CÓ trong hệ thống (job 10 giây, Admin tạo tay…),
     * nên không có thẻ. Ví dụ khung cần 2 chuyến thêm, đã có 1 → 1 ở đây, và 1 thẻ.
     */
    private int alreadyInSlotCount;

    // --- Route pairs CT9 (CM-8, CM-29) ---
    /** Số thẻ giữ xe: một xe và một kíp chạy chuyến thêm đi, chờ ở bến xa, rồi chạy chuyến thêm về. */
    private int heldLoopCount;
    /** Lương chờ đồng/người/ngày và phí xe nằm bến đồng/ngày đang dùng cho tiền chờ. */
    private BigDecimal costWaitingWagePerDay;
    private BigDecimal costParkingPerDay;
}
