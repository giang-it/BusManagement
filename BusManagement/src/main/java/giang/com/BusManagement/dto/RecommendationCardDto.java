package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PHASE 7 — MỘT thẻ đề xuất: "khung (tuyến × ngày × giờ) này được dự báo đông,
 * đây là xe + tài xế có thể chạy, và ước tính doanh thu / chi phí / lợi nhuận"
 * (chi phí & lợi nhuận thêm ở bước 3, theo tham số CostParameters).
 *
 * Thẻ được TÍNH LẠI mỗi lần mở trang (chốt của chủ dự án: chỉ đọc, không lưu
 * DB, không tạo chuyến). Vì vậy thẻ không mang id hay vòng đời — nó là ảnh chụp
 * tại thời điểm xem.
 *
 * Ước tính doanh thu tuân đúng thiết kế Phase 6: dự báo là TỈ LỆ lấp đầy, nên
 * số khách = tỉ lệ × sức chứa của CHÍNH chiếc xe được chọn, và doanh thu = số
 * khách × giá vé lịch sử của khung. Chi phí (bước 3) = nhiên liệu × quãng đường
 * + lương × giờ × số tài xế, theo tham số Admin; lợi nhuận = doanh thu − chi phí
 * (chỉ khi có giá vé). Các trường tài nguyên/doanh thu/chi phí chỉ có giá trị khi
 * đã chọn được xe + tài xế (status RECOMMENDED/STALE); ở NO_RESOURCE chúng để
 * null và giao diện hiển thị tín hiệu nhu cầu kèm lời nhắc thiếu tài nguyên.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCardDto {

    // --- Khung ứng viên (từ chuỗi dự báo) ---
    private Long routeId;

    /** "Hà Nội → Hải Phòng" — lấy nguyên từ ForecastSeriesDto, không dựng lại. */
    private String routeLabel;

    private int departureHour;

    /** "06:00" — nhãn khung giờ, lấy từ ForecastSeriesDto. */
    private String slotLabel;

    private LocalDate date;

    /** Nhãn thứ trong tuần đã Việt hoá (T2..CN), lấy từ ForecastPointDto. */
    private String dayLabel;

    private boolean weekend;

    /** Tỉ lệ lấp đầy dự báo cho ngày này (0..1) — lý do khung được đề xuất. */
    private double predictedOccupancy;

    // --- Kết cục & tài nguyên ---
    private RecommendationStatus status;

    /** Biển số xe được chọn (null nếu NO_RESOURCE). */
    private String busPlate;

    /** Sức chứa của xe được chọn — mẫu số để quy tỉ lệ dự báo ra số khách. */
    private Integer busCapacity;

    /** Tên tài xế được chọn (null nếu NO_RESOURCE). */
    private String driverName;

    // --- Ước tính doanh thu (chỉ khi chọn được xe) ---
    /** Số khách kỳ vọng = round(predictedOccupancy × busCapacity). */
    private Integer expectedPassengers;

    /** Giá vé lịch sử của khung (chuyến COMPLETED gần nhất cùng tuyến+giờ). */
    private BigDecimal ticketPrice;

    /** Doanh thu ước tính = expectedPassengers × ticketPrice. */
    private BigDecimal estimatedRevenue;

    // --- Ước tính chi phí & lợi nhuận (Phase 7 bước 3; theo tham số CostParameters) ---
    /** Chi phí vận hành ước tính = nhiên liệu/km × quãng đường + lương/giờ × giờ × số tài xế. */
    private BigDecimal estimatedCost;

    /** Lợi nhuận ước tính = doanh thu − chi phí. CÓ THỂ ÂM (chi phí vượt doanh thu). */
    private BigDecimal estimatedProfit;

    /** Câu giải thích kết cục (vì sao đề xuất / vì sao thiếu tài nguyên). */
    private String note;
}
