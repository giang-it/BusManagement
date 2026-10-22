package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một ngày được dự báo trong chuỗi (tuyến × khung giờ).
 *
 * Giá trị dự báo là NHU CẦU của khung — số khách muốn đi, cộng mọi chuyến của khung —
 * không bị kẹp ở số ghế (QĐ-2, GĐ5 của đợt thiết kế lại; xem javadoc của ForecastService).
 * Recommendation và What-if đọc thẳng predictedDemand và tripsNeeded (GĐ6, QĐ-17); cầu nối tỉ lệ
 * lấp đầy predictedOccupancy của GĐ5 đã bỏ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPointDto {

    private LocalDate date;

    /** Nhãn thứ trong tuần đã Việt hoá (T2..CN) — tính ở service, template chỉ hiển thị. */
    private String dayLabel;

    /** Có phải cuối tuần không — dùng để tô nhạt cột trên bảng. */
    private boolean weekend;

    /** Dịp lễ của ngày, ví dụ "Tết D−4"; null nếu là ngày thường. */
    private String holidayLabel;

    /** Nhu cầu dự báo của khung: số khách muốn đi, không bị kẹp ở số ghế. */
    private double predictedDemand;

    /** Nhu cầu ÷ số ghế xe chuẩn của tuyến, KHÔNG kẹp: 1,44 nghĩa là cần thêm 44% một xe. */
    private double demandRatio;

    /**
     * Nhu cầu vượt 90% số ghế xe chuẩn — cùng ngưỡng Trip.needsReinforcement() (> 0.90), áp
     * lên nhu cầu dự báo. Đây là tín hiệu Recommendation dùng để sinh thẻ tăng cường.
     */
    private boolean needsReinforcement;

    /**
     * Số chuyến cần = max(1, ⌈nhu cầu ÷ (0,9 × ghế xe chuẩn)⌉) (QĐ-16, G5-6). Số này ≥ 2 đúng
     * khi needsReinforcement — một định nghĩa "chuyến nóng", không phải hai. Recommendation sinh
     * một thẻ cho mỗi chuyến thêm, tức tripsNeeded − 1 thẻ (QĐ-17, G6-2).
     */
    private int tripsNeeded;
}
