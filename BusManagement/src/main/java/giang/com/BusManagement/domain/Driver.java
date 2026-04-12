package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "drivers")
@Data
public class Driver {

    @Id
    private Long userId; // Giữ nguyên theo cấu trúc cũ

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // === THÔNG TIN BẰNG LÁI (mở rộng) ===
    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @Column(name = "license_class")
    private String licenseClass; // Hạng bằng: B1, B2, C, D, E, FB2

    @Column(name = "license_issue_date")
    private LocalDate licenseIssueDate; // Ngày cấp

    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate; // Ngày hết hạn

    @Column(name = "experience_years")
    private Integer experienceYears; // Giữ lại từ code cũ

    // === THÔNG TIN LIÊN LẠC (mới) ===
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "emergency_contact")
    private String emergencyContact; // Tên người liên hệ khẩn cấp

    @Column(name = "emergency_phone")
    private String emergencyPhone; // SĐT người liên hệ khẩn cấp

    @Column(name = "address")
    private String address;

    // === THEO DÕI THỜI GIAN LÁI XE ===
    @Column(name = "total_driving_hours_24h", columnDefinition = "DOUBLE DEFAULT 0")
    private Double totalDrivingHours24h = 0.0; // Giữ lại từ code cũ

    @Column(name = "monthly_rest_days", columnDefinition = "INT DEFAULT 0")
    private Integer monthlyRestDays = 0; // Giữ lại từ code cũ

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isActive = true;

    // === HELPER METHODS ===

    // Kiểm tra bằng lái còn hạn
    public Boolean isLicenseValid() {
        if (licenseExpiryDate == null)
            return false;
        return licenseExpiryDate.isAfter(LocalDate.now());
    }

    // Kiểm tra đã nghỉ đủ chỉ tiêu chưa (2 ngày/tháng)
    public Boolean hasMetLeaveQuota() {
        return monthlyRestDays >= 2;
    }

    // Kiểm tra có thể lái thêm không (< 8h trong 24h)
    public Boolean canDriveMore() {
        return totalDrivingHours24h < 8.0;
    }
}