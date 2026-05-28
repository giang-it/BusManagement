package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {
    List<Bus> findByStatus(BusStatus status);

    List<Bus> findByStatusAndBusType(BusStatus status, BusType busType);

    @Query("SELECT DISTINCT b FROM Bus b LEFT JOIN FETCH b.busType")
    List<Bus> findAllWithBusType();
}
