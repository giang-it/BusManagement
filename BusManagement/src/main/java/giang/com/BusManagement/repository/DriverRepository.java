package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    // có sẵn trong jpaRepository
    // List<Driver> findById(Long id);
}
