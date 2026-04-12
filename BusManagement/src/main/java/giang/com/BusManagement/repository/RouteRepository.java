package giang.com.BusManagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.Route;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
}