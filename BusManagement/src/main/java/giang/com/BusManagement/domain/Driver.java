package giang.com.BusManagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "drivers")
@Data
public class Driver {
    @Id
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String licenseNumber;
    private Integer experienceYears;
    private Double totalDrivingHours24h;
    private Integer monthlyRestDays;
    private Boolean isActive = true;
    private java.time.LocalDate licenseExpiryDate;

    /**
     * Bằng lái còn hiệu lực vào MỘT NGÀY CỤ THỂ.
     *
     * Đây mới là câu hỏi đúng khi xét phân công: tài xế phải còn bằng vào ngày
     * KHỞI HÀNH, không phải vào ngày Admin bấm nút. Trước đây hệ thống chỉ có
     * bản không tham số (so với hôm nay), nên một chuyến khởi hành sau ngày bằng
     * hết hạn vẫn được duyệt trót lọt.
     *
     * @param onDate ngày cần kiểm tra hiệu lực (thường là ngày khởi hành)
     */
    public boolean isLicenseValid(java.time.LocalDate onDate) {
        return licenseExpiryDate != null && licenseExpiryDate.isAfter(onDate);
    }

    /**
     * Bằng lái còn hiệu lực NGAY HÔM NAY.
     *
     * Chỉ dùng cho các màn hình hiển thị tình trạng hiện tại của tài xế (ví dụ
     * nhãn "Hết hạn" ở danh sách tài xế). Mọi kiểm tra liên quan tới việc gán
     * người vào chuyến phải dùng bản isLicenseValid(LocalDate) với ngày khởi
     * hành — xem javadoc ở trên.
     */
    public boolean isLicenseValid() {
        return isLicenseValid(java.time.LocalDate.now());
    }
}