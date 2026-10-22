package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PHASE 7 — MỘT thẻ đề xuất: "khung (tuyến × ngày × giờ) này được dự báo đông, đây
 * là MỘT chuyến thêm cho nó, xe + tài xế có thể chạy, và ước tính doanh thu / chi
 * phí / lợi nhuận" (chi phí & lợi nhuận thêm ở bước 3, theo tham số CostParameters).
 *
 * Thẻ được TÍNH LẠI mỗi lần mở trang (chốt của chủ dự án: chỉ đọc, không lưu
 * DB, không tạo chuyến). Vì vậy thẻ không mang id hay vòng đời — nó là ảnh chụp
 * tại thời điểm xem.
 *
 * Từ GĐ6 (QĐ-17) mỗi thẻ là MỘT chuyến thêm: khung cần N chuyến thì có N − 1 thẻ,
 * "chuyến thêm k/(N − 1)". Số khách kỳ vọng là phần khách TRÀN rơi vào ghế của chuyến
 * này, sau xe chuẩn và các chuyến thêm trước nó (ForecastService.extraTripPassengers);
 * doanh thu = số khách đó × giá vé lịch sử của khung. Chi phí = nhiên liệu × quãng
 * đường + lương × giờ × số tài xế, theo tham số Admin; lợi nhuận = doanh thu − chi phí
 * (chỉ khi có giá vé). Ghế của chuyến là sức chứa của xe được chọn; chưa chọn được xe
 * (NO_RESOURCE) thì tính bằng xe chuẩn của tuyến, để thẻ vẫn cho biết chuyến đó đáng
 * giá bao nhiêu.
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

    /** Nhu cầu dự báo của cả khung (số khách muốn đi) — lý do khung được đề xuất. */
    private double predictedDemand;

    /** Số ghế xe chuẩn của tuyến, mẫu số của ngưỡng 90%. */
    private int standardSeats;

    /** Nhu cầu ÷ ghế xe chuẩn, không kẹp: 1,44 nghĩa là cần thêm 44% một xe. */
    private double demandRatio;

    /** Thứ tự của chuyến thêm này trong khung, từ 1. */
    private int extraIndex;

    /** Số chuyến thêm khung cần = số chuyến cần − 1. */
    private int extraCount;

    // --- Kết cục & tài nguyên ---
    private RecommendationStatus status;

    /** Biển số xe được chọn (null nếu không chọn được xe). */
    private String busPlate;

    /** Sức chứa của xe được chọn — số ghế của chuyến thêm này. */
    private Integer busCapacity;

    /** Tên tài xế được chọn (null nếu NO_RESOURCE). */
    private String driverName;

    // --- Ước tính doanh thu ---
    /** Số khách kỳ vọng mà chuyến thêm này chở được: phần khách tràn rơi vào ghế của nó. */
    private Double expectedPassengers;

    /** Giá vé lịch sử của khung (chuyến COMPLETED gần nhất cùng tuyến+giờ). */
    private BigDecimal ticketPrice;

    /** Doanh thu ước tính = expectedPassengers × ticketPrice, làm tròn tới đồng. */
    private BigDecimal estimatedRevenue;

    // --- Ước tính chi phí & lợi nhuận (Phase 7 bước 3; theo tham số CostParameters) ---
    /** Chi phí vận hành ước tính = nhiên liệu/km × quãng đường + lương/giờ × giờ × số tài xế. */
    private BigDecimal estimatedCost;

    /** Lợi nhuận ước tính = doanh thu − chi phí. CÓ THỂ ÂM: thẻ gắn nhãn "lỗ dự kiến". */
    private BigDecimal estimatedProfit;

    /** Câu giải thích kết cục (vì sao đủ điều kiện / vì sao thiếu tài nguyên). */
    private String note;
}
