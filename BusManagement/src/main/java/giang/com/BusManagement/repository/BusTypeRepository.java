package giang.com.BusManagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.BusType;

@Repository
public interface BusTypeRepository extends JpaRepository<BusType, Integer> {
}
