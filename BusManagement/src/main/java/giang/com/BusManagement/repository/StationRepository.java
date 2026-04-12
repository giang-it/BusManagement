package giang.com.BusManagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.Station;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    // Tìm kiếm trạm theo tên (phục vụ tính năng gợi ý khi khách đặt vé)
    List<Station> findByStationNameContainingIgnoreCase(String name);
}