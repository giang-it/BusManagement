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

    /** Các thẻ đề xuất, xếp theo tỉ lệ dự báo GIẢM DẦN (đông nhất lên đầu). */
    private List<RecommendationCardDto> cards;

    // --- Bối cảnh forecast (chuyển tiếp từ DemandForecastViewDto) ---
    private LocalDate historyEnd;
    private long historyStaleDays;
    private LocalDate forecastFrom;
    private int horizonDays;

    // --- Số liệu tổng hợp ---
    /** Tổng số ngày-khung được dự báo vượt ngưỡng tăng cường (số ứng viên). */
    private int totalCandidates;
    private int recommendedCount;
    private int noResourceCount;

    /**
     * Có ít nhất hai thẻ rơi vào CÙNG (ngày, khung giờ) hay không. Khi đúng, các
     * thẻ đó có thể cùng đề xuất một xe/tài xế vì mỗi thẻ được đánh giá độc lập,
     * không "giữ chỗ" tài nguyên giữa các thẻ — giao diện cảnh báo rõ để người
     * đọc hiểu đây là danh sách gợi ý, không phải lịch phân công khả thi đồng
     * thời. Tính từ dữ liệu, không hardcode.
     */
    private boolean sharedSlotContention;

    // --- Tham số chi phí đang áp dụng (Phase 7 bước 3) — để trang hiển thị minh
    // bạch "chi phí tính theo giá nào" và dẫn tới màn hình chỉnh. ---
    /** Chi phí nhiên liệu/km đang dùng (đồng). */
    private BigDecimal costFuelPerKm;
    /** Lương tài xế/giờ đang dùng (đồng). */
    private BigDecimal costWagePerHour;
}
