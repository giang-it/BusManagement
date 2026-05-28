package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Driver;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    // có sẵn trong jpaRepository
    // List<Driver> findById(Long id);
    @Query("SELECT DISTINCT d FROM Driver d LEFT JOIN FETCH d.user")
    List<Driver> findAllWithUser();
}
