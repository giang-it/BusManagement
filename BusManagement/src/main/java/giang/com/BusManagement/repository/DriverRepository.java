package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Driver;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    // có sẵn trong jpaRepository
    // List<Driver> findById(Long id);
    @Query("SELECT DISTINCT d FROM Driver d LEFT JOIN FETCH d.user")
    List<Driver> findAllWithUser();

    /** Dùng cho Dashboard: đếm tài xế theo isActive (true = đang hoạt động). */
    long countByIsActive(Boolean isActive);

    /** Dùng cho Dashboard: kinh nghiệm trung bình (năm) của toàn bộ tài xế. */
    @Query("SELECT AVG(d.experienceYears) FROM Driver d")
    Double findAverageExperienceYears();

    /**
     * Dùng cho Dashboard: đếm tài xế đang hoạt động có bằng lái còn hạn nhưng
     * sắp hết hạn trong khoảng [from, to]. Cùng khái niệm "sắp hết hạn" mà
     * TripService.findBestAvailableDriver() đã dùng (ngưỡng 7 ngày), chỉ khác
     * là ở đây trả về số đếm thay vì lọc danh sách gán tài xế.
     */
    @Query("SELECT COUNT(d) FROM Driver d WHERE d.isActive = true AND d.licenseExpiryDate BETWEEN :from AND :to")
    long countActiveDriversWithLicenseExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
