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

    public boolean isLicenseValid() {
        return licenseExpiryDate != null && licenseExpiryDate.isAfter(java.time.LocalDate.now());
    }
}