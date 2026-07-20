package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một tài xế được đề xuất vì CÒN khả năng nhận thêm chuyến trong ngày đã chọn.
 *
 * Đây là chiều ngược lại của DriverWorkloadDto (bảng "Tài xế có tải cao nhất
 * hôm nay" trên Dashboard): DriverWorkloadDto trả lời "ai đang làm nhiều
 * nhất?", DTO này trả lời "ai còn nhận được việc?". Không tái sử dụng
 * DriverWorkloadDto vì nó chỉ có 3 trường (userId/fullName/drivingHoursToday)
 * và không mang nổi hạn mức còn lại, tình trạng bằng lái hay lý do đề xuất —
 * những thứ khiến đây là một đề xuất chứ không phải một con số theo dõi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverRecommendationDto {

    private Long userId;
    private String fullName;

    /** Số giờ đã được phân công lái trong ngày đã chọn — do TripService tính. */
    private double drivingHours;

    /** Hạn mức giờ lái còn lại trong ngày (luôn > 0, vì hết hạn mức thì không được đề xuất). */
    private double remainingHours;

    private boolean licenseValid;
    private LocalDate licenseExpiryDate;
    private Integer experienceYears;

    /** Nhãn ngắn cho cột "Tình trạng" — suy ra từ drivingHours, không phải field trong DB. */
    private String statusLabel;

    /** Vì sao tài xế này xuất hiện trong danh sách — hiển thị nguyên văn cho Admin. */
    private String reason;
}
