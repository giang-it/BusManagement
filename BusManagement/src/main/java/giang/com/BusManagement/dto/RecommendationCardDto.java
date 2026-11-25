package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * này, sau các chuyến đã có trong khung và các chuyến thêm trước nó
 * (ForecastService.extraTripPassengers); doanh thu = số khách đó × giá vé lịch sử của khung.
 * Ghế của chuyến là sức chứa của xe được chọn; chưa chọn được xe (NO_RESOURCE) thì tính bằng xe
 * chuẩn của tuyến, để thẻ vẫn cho biết chuyến đó đáng giá bao nhiêu.
 *
 * Từ route pairs CT7 (QĐ-22, PA-B, cách 2a):
 * <ul>
 *   <li>khung đã có M chuyến (thường lệ, tăng cường, tạo tay) thì chỉ còn N − M thẻ, đánh số
 *       tiếp theo các chuyến đã có (khung cần 2 chuyến thêm, đã có 1 → một thẻ "chuyến thêm 2/2");</li>
 *   <li>mỗi thẻ là MỘT VÒNG: chuyến thêm cộng lượt đi kèm ({@link CompanionLeg}). Doanh thu, chi phí
 *       và lợi nhuận ước tính là của CẢ VÒNG; phần riêng của chuyến thêm ở tripRevenue / tripCost.</li>
 * </ul>
 *
 * Từ route pairs CT9 (CM-29), một thẻ "giữ xe" ({@link LoopLegKind#HOLD}) gánh HAI chuyến thêm: chuyến thêm
 * đi của thẻ và chuyến thêm về ở lượt đi kèm (khung, nhu cầu, thứ tự của nó ghi trong CompanionLeg); chi phí
 * cả vòng cộng thêm tiền chờ. Vì vậy số thẻ có thể nhỏ hơn số chuyến thêm.
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

    /** Thứ tự của chuyến thêm này trong khung, từ 1 — đếm cả chuyến thêm đã có trong khung. */
    private int extraIndex;

    /** Số chuyến thêm khung cần = số chuyến cần − 1. */
    private int extraCount;

    /**
     * Số chuyến đã có trong khung (chưa hủy) lúc xem, và tổng số ghế của chúng (CT7, cách 2a). Khung
     * chưa có chuyến nào — chưa tạo lịch — thì là 1 chuyến xe chuẩn (giả định); existingAssumed = true.
     */
    private int existingTrips;
    private int existingSeats;
    private boolean existingAssumed;

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

    /** Doanh thu riêng của chuyến thêm = expectedPassengers × ticketPrice, làm tròn tới đồng. */
    private BigDecimal tripRevenue;

    /** Chi phí riêng của chuyến thêm = nhiên liệu/km × quãng đường + lương/giờ × giờ × số tài xế. */
    private BigDecimal tripCost;

    /** Lượt đi kèm của vòng (CT7). Không bao giờ null. */
    private CompanionLeg companion;

    /** Doanh thu ước tính của CẢ VÒNG = tripRevenue + doanh thu lượt đi kèm. */
    private BigDecimal estimatedRevenue;

    // --- Ước tính chi phí & lợi nhuận (Phase 7 bước 3; theo tham số CostParameters) ---
    /** Chi phí ước tính của CẢ VÒNG = tripCost + chi phí lượt đi kèm. */
    private BigDecimal estimatedCost;

    /** Lợi nhuận CẢ VÒNG = doanh thu − chi phí. CÓ THỂ ÂM: thẻ gắn nhãn "lỗ dự kiến". */
    private BigDecimal estimatedProfit;

    /** Câu giải thích kết cục (vì sao đủ điều kiện / vì sao thiếu tài nguyên). */
    private String note;

    /**
     * Lượt đi kèm của một chuyến thêm (route pairs CT7, {@link LoopLegKind}): đi cùng khung nào, xe và
     * tài xế nào, chở bao nhiêu khách tràn của khung đó, tiền bao nhiêu.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanionLeg {

        private LoopLegKind kind;

        /** Tuyến của lượt đi kèm (chiều ngược); null khi không có lượt cụ thể. */
        private Long routeId;

        private String routeLabel;

        /** Giờ khởi hành (cùng giờ một chuyến thường lệ của chiều ngược); null khi không có lượt cụ thể. */
        private LocalDateTime departure;

        /**
         * true khi lượt này đã chọn được xe + tài xế và được giữ chỗ; false khi chỉ là KẾ HOẠCH (thẻ
         * thiếu tài nguyên, hay chưa xếp được lượt cụ thể).
         */
        private boolean reserved;

        private String busPlate;

        private String driverName;

        /** Số khách tràn kỳ vọng của khung đi kèm rơi vào ghế của lượt này. */
        private double passengers;

        /** null khi khung đi kèm chưa có giá vé lịch sử (khi đó cả vòng tính như 0 đồng tiền vé ở lượt này). */
        private BigDecimal revenue;

        private BigDecimal cost;

        // --- Route pairs CT9: chỉ có ở lượt giữ xe (kind = HOLD); null ở mọi loại khác ---

        /** Bến xa nơi xe và kíp chờ (bến đi của chuyến thêm về). */
        private String waitStation;

        /** Lần về ngay sớm nhất — mốc của tiền chờ (CM-30). */
        private LocalDateTime waitFrom;

        /** Số giờ chờ THÊM so với lần về ngay sớm nhất, và tiền chờ của chừng ấy giờ (CM-8). */
        private Double waitHours;

        private BigDecimal waitCost;

        /** Khung của chuyến thêm về: nhu cầu dự báo, ghế xe chuẩn, thứ tự chuyến thêm trong khung. */
        private Double servedDemand;

        private Integer servedStandardSeats;

        private Integer servedExtraIndex;

        private Integer servedExtraCount;

        /** Lãi theo kế hoạch của hai vòng riêng mà vòng giữ thay (chỉ phần dương), để so (CM-32). */
        private BigDecimal separateProfit;
    }
}
