package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Toàn bộ nội dung màn hình "Đề xuất tài xế khả dụng" cho một ngày cụ thể.
 *
 * Kèm theo danh sách đề xuất là các con số giải thích vì sao danh sách NGẮN
 * hơn tổng số tài xế đang hoạt động. Không có chúng, Admin nhìn 3 dòng trên 36
 * tài xế sẽ tưởng hệ thống lỗi; có chúng thì thấy rõ ai bị loại và vì lý do gì.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverRecommendationViewDto {

    /** Ngày Admin đã chọn (mặc định là hôm nay). */
    private LocalDate date;

    /** Tài xế còn hạn mức, sắp xếp theo tải TĂNG DẦN (rảnh nhất lên đầu). */
    private List<DriverRecommendationDto> recommendations;

    /** Tổng số tài xế đang hoạt động được đưa vào xét. */
    private long activeDriversConsidered;

    /** Bị loại vì đã dùng hết hạn mức giờ lái trong ngày. */
    private long noCapacityCount;

    /** Bị loại vì bằng lái đã hết hạn (theo Driver.isLicenseValid()). */
    private long licenseBlockedCount;
}
