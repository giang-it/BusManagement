package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một ngày được dự báo trong chuỗi (tuyến × khung giờ).
 *
 * Giá trị dự báo là TỈ LỆ LẤP ĐẦY (0..1), không phải số vé — xem javadoc của
 * ForecastService để biết vì sao đây là đơn vị bắt buộc chứ không phải lựa chọn
 * phong cách.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPointDto {

    private LocalDate date;

    /** Nhãn thứ trong tuần đã Việt hoá (T2..CN) — tính ở service, template chỉ hiển thị. */
    private String dayLabel;

    /** Có phải cuối tuần không — dùng để tô nhạt cột trên biểu đồ. */
    private boolean weekend;

    /** Tỉ lệ lấp đầy dự báo, đã kẹp trong [0, 1]. */
    private double predictedOccupancy;

    /**
     * Vượt ngưỡng "chuyến đông" của hệ thống (Trip.needsReinforcement(), > 0.90).
     * Đây chính là tín hiệu Phase 7 sẽ dùng để sinh chuyến tăng cường ứng viên.
     */
    private boolean needsReinforcement;
}
