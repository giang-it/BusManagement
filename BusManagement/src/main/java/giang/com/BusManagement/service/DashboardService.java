package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.AiStatsDto;
import giang.com.BusManagement.dto.BusAlertDto;
import giang.com.BusManagement.dto.ChartSeriesDto;
import giang.com.BusManagement.dto.DashboardOverviewDto;
import giang.com.BusManagement.dto.DriverStatsDto;
import giang.com.BusManagement.dto.DriverWorkloadDto;
import giang.com.BusManagement.dto.FleetStatsDto;
import giang.com.BusManagement.dto.OccupancyStatsDto;
import giang.com.BusManagement.dto.OperationalKpisDto;
import giang.com.BusManagement.dto.RoutePerformanceDto;
import giang.com.BusManagement.dto.RouteStatsDto;
import giang.com.BusManagement.dto.StrategicAnalyticsDto;
import giang.com.BusManagement.dto.TripPreviewDto;
import giang.com.BusManagement.dto.TripStatsDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Đọc-only reporting layer cho Dashboard & Analytics.
 * Chỉ tổng hợp dữ liệu đã có (Bus/Driver/Trip/Route) — không chứa business
 * rule mới, không ghi dữ liệu. Với logic đã tồn tại ở nơi khác (giờ lái tài
 * xế, ngưỡng "sắp hết hạn" bằng lái, ngưỡng "hot trip" 90%...), method này
 * gọi lại đúng chỗ đó thay vì tính lại.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final TripService tripService;

    private static final int PREVIEW_LIMIT = 5;
    private static final int MAINTENANCE_ALERT_LIMIT = 10;
    private static final int UPCOMING_TRIPS_WINDOW_HOURS = 48;

    /** Cùng ngưỡng "sắp hết hạn" mà TripService.findBestAvailableDriver() đang dùng cho bằng lái. */
    private static final int EXPIRING_LICENSE_DAYS = 7;

    /** Cùng ngưỡng với Trip.needsReinforcement() — không định nghĩa lại "hot trip". */
    private static final double HOT_TRIP_OCCUPANCY_THRESHOLD = 0.90;

    /** Trạng thái được coi là "đã vận hành thực tế" khi tính doanh thu/số liệu tuyến. */
    private static final List<TripStatus> OPERATIONAL_STATUSES = List.of(
            TripStatus.ACTIVE, TripStatus.DEPARTED, TripStatus.COMPLETED);

    @Transactional(readOnly = true)
    public DashboardOverviewDto getOverview() {
        // Dữ liệu tốn kém, dùng chung cho cả 2 nhóm Operational/Strategic — chỉ query 1 lần.
        List<Bus> allBuses = busRepository.findAllWithBusType();
        List<Trip> pendingTrips = tripService.getPendingTrips();
        List<Object[]> aiByStatusRows = tripRepository.countAiSuggestionsByStatus();

        OperationalKpisDto operational = buildOperationalKpis(allBuses, pendingTrips, aiByStatusRows);
        StrategicAnalyticsDto strategic = buildStrategicAnalytics(allBuses, aiByStatusRows);

        return new DashboardOverviewDto(operational, strategic);
    }

    // =========================================================================
    // OPERATIONAL KPIS
    // =========================================================================

    private OperationalKpisDto buildOperationalKpis(List<Bus> allBuses, List<Trip> pendingTrips,
            List<Object[]> aiByStatusRows) {

        long pendingAutoAssignedCount = pendingTrips.stream()
                .filter(t -> t.getBus() != null && t.getDriver() != null)
                .count();
        List<TripPreviewDto> pendingPreview = pendingTrips.stream()
                .limit(PREVIEW_LIMIT)
                .map(this::toTripPreviewDto)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        List<Trip> upcomingTrips = tripRepository.findByDepartureTimeBetweenAndStatus(
                now, now.plusHours(UPCOMING_TRIPS_WINDOW_HOURS), TripStatus.ACTIVE);
        List<TripPreviewDto> upcomingPreview = upcomingTrips.stream()
                .sorted(Comparator.comparing(Trip::getDepartureTime))
                .limit(PREVIEW_LIMIT)
                .map(this::toTripPreviewDto)
                .toList();

        List<BusAlertDto> maintenanceAlerts = buildMaintenanceAlerts(allBuses);
        long maintenanceOverdueCount = allBuses.stream().filter(Bus::needsMaintenance).count();
        long maintenanceNearCount = allBuses.stream()
                .filter(b -> !b.needsMaintenance() && b.isNearMaintenance(0))
                .count();

        long aiSuggestionsPendingCount = sumStatusCounts(aiByStatusRows, List.of(TripStatus.PENDING_APPROVAL));

        long availableBusesCount = allBuses.stream().filter(b -> b.getStatus() == BusStatus.READY).count();
        long activeDriversCount = driverRepository.countByIsActive(Boolean.TRUE);

        return new OperationalKpisDto(
                pendingTrips.size(), pendingAutoAssignedCount, pendingTrips.size() - pendingAutoAssignedCount,
                pendingPreview,
                upcomingTrips.size(), upcomingPreview,
                tripRepository.countByStatus(TripStatus.ACTIVE),
                maintenanceOverdueCount, maintenanceNearCount, maintenanceAlerts,
                aiSuggestionsPendingCount,
                availableBusesCount, activeDriversCount);
    }

    private List<BusAlertDto> buildMaintenanceAlerts(List<Bus> allBuses) {
        return allBuses.stream()
                .filter(b -> b.needsMaintenance() || b.isNearMaintenance(0))
                .map(b -> new BusAlertDto(b.getId(), b.getLicensePlate(), b.getKmSinceLastMaintenance(),
                        b.getMaintenanceThreshold() != null ? b.getMaintenanceThreshold() : 0.0,
                        b.needsMaintenance()))
                .sorted(Comparator.comparingDouble(BusAlertDto::getKmSinceLastMaintenance).reversed())
                .limit(MAINTENANCE_ALERT_LIMIT)
                .toList();
    }

    // =========================================================================
    // STRATEGIC ANALYTICS
    // =========================================================================

    private StrategicAnalyticsDto buildStrategicAnalytics(List<Bus> allBuses, List<Object[]> aiByStatusRows) {
        return new StrategicAnalyticsDto(
                buildFleetStats(allBuses),
                buildTripStats(),
                buildRouteStats(),
                buildDriverStats(),
                buildOccupancyStats(),
                buildAiStats(aiByStatusRows));
    }

    private FleetStatsDto buildFleetStats(List<Bus> allBuses) {
        long readyCount = allBuses.stream().filter(b -> b.getStatus() == BusStatus.READY).count();
        long travelingCount = allBuses.stream().filter(b -> b.getStatus() == BusStatus.TRAVELING).count();
        long repairingCount = allBuses.stream().filter(b -> b.getStatus() == BusStatus.REPAIRING).count();
        long totalBuses = allBuses.size();
        double utilizationRate = totalBuses > 0 ? (double) travelingCount / totalBuses : 0.0;

        ChartSeriesDto statusChart = new ChartSeriesDto(
                List.of("Sẵn sàng", "Đang chạy", "Bảo trì"),
                numbers(readyCount, travelingCount, repairingCount));

        Map<String, Long> busTypeCounts = allBuses.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getBusType() != null ? b.getBusType().getTypeName() : "Chưa phân loại",
                        Collectors.counting()));
        ChartSeriesDto busTypeChart = toChartSeries(busTypeCounts);

        return new FleetStatsDto(totalBuses, readyCount, travelingCount, repairingCount, utilizationRate,
                statusChart, busTypeChart);
    }

    private TripStatsDto buildTripStats() {
        long pendingCount = tripRepository.countByStatus(TripStatus.PENDING_APPROVAL);
        long activeCount = tripRepository.countByStatus(TripStatus.ACTIVE);
        long departedCount = tripRepository.countByStatus(TripStatus.DEPARTED);
        long completedCount = tripRepository.countByStatus(TripStatus.COMPLETED);
        long cancelledCount = tripRepository.countByStatus(TripStatus.CANCELLED);
        long totalTrips = pendingCount + activeCount + departedCount + completedCount + cancelledCount;

        List<Object[]> aggRows = tripRepository.sumTicketsSoldAndRevenueByStatuses(OPERATIONAL_STATUSES);
        Object[] agg = aggRows.isEmpty() ? null : aggRows.get(0);
        long totalTicketsSold = (agg != null && agg[0] != null) ? ((Number) agg[0]).longValue() : 0L;
        BigDecimal totalRevenue = (agg != null && agg[1] != null) ? (BigDecimal) agg[1] : BigDecimal.ZERO;

        ChartSeriesDto statusChart = new ChartSeriesDto(
                List.of("Chờ duyệt", "Đang chạy", "Đã khởi hành", "Hoàn thành", "Đã hủy"),
                numbers(pendingCount, activeCount, departedCount, completedCount, cancelledCount));

        return new TripStatsDto(totalTrips, pendingCount, activeCount, departedCount, completedCount,
                cancelledCount, totalTicketsSold, totalRevenue, statusChart);
    }

    private RouteStatsDto buildRouteStats() {
        long totalRoutes = routeRepository.count();
        long routesWithTrips = tripRepository.findDistinctRouteIdsWithTrips().size();

        Map<Long, Route> routesById = routeRepository.findAllWithStations().stream()
                .collect(Collectors.toMap(Route::getId, r -> r));

        List<RoutePerformanceDto> topRoutes = tripRepository.aggregateByRoute(OPERATIONAL_STATUSES).stream()
                .map(row -> {
                    Long routeId = (Long) row[0];
                    long tripCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                    BigDecimal revenue = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                    Route route = routesById.get(routeId);
                    String label = route != null
                            ? route.getDeparturePointDisplay() + " → " + route.getDestinationPointDisplay()
                            : "Tuyến #" + routeId;
                    return new RoutePerformanceDto(routeId, label, tripCount, revenue);
                })
                .sorted(Comparator.comparingLong(RoutePerformanceDto::getTripCount).reversed())
                .limit(PREVIEW_LIMIT)
                .toList();

        ChartSeriesDto tripsPerRouteChart = new ChartSeriesDto(
                topRoutes.stream().map(RoutePerformanceDto::getRouteLabel).toList(),
                topRoutes.stream().map(r -> (Number) r.getTripCount()).toList());

        return new RouteStatsDto(totalRoutes, totalRoutes - routesWithTrips, topRoutes, tripsPerRouteChart);
    }

    private DriverStatsDto buildDriverStats() {
        long totalDrivers = driverRepository.count();
        long activeDrivers = driverRepository.countByIsActive(Boolean.TRUE);
        long inactiveDrivers = totalDrivers - activeDrivers;

        LocalDate today = LocalDate.now();
        long expiringLicenseCount = driverRepository.countActiveDriversWithLicenseExpiringBetween(
                today, today.plusDays(EXPIRING_LICENSE_DAYS));

        Double avgExperience = driverRepository.findAverageExperienceYears();

        LocalDateTime now = LocalDateTime.now();
        List<DriverWorkloadDto> topLoadedDrivers = driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .map(d -> new DriverWorkloadDto(
                        d.getUserId(),
                        d.getUser() != null ? d.getUser().getFullName() : "Tài xế #" + d.getUserId(),
                        tripService.getDrivingHoursForDate(d, now)))
                .sorted(Comparator.comparingDouble(DriverWorkloadDto::getDrivingHoursToday).reversed())
                .limit(PREVIEW_LIMIT)
                .toList();

        ChartSeriesDto activeInactiveChart = new ChartSeriesDto(
                List.of("Đang hoạt động", "Ngừng hoạt động"),
                numbers(activeDrivers, inactiveDrivers));

        return new DriverStatsDto(totalDrivers, activeDrivers, inactiveDrivers, expiringLicenseCount,
                avgExperience != null ? avgExperience : 0.0, topLoadedDrivers, activeInactiveChart);
    }

    private OccupancyStatsDto buildOccupancyStats() {
        List<Double> occupancyRates = tripRepository.findSeatsAndSoldByStatus(TripStatus.ACTIVE).stream()
                .map(row -> {
                    int totalSeats = ((Number) row[0]).intValue();
                    int ticketsSold = ((Number) row[1]).intValue();
                    return totalSeats > 0 ? (double) ticketsSold / totalSeats : 0.0;
                })
                .toList();

        double averageOccupancyRate = occupancyRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        long hotTripsCount = occupancyRates.stream().filter(r -> r > HOT_TRIP_OCCUPANCY_THRESHOLD).count();

        long under50 = occupancyRates.stream().filter(r -> r < 0.5).count();
        long from50to70 = occupancyRates.stream().filter(r -> r >= 0.5 && r < 0.7).count();
        long from70to90 = occupancyRates.stream().filter(r -> r >= 0.7 && r <= 0.9).count();
        long over90 = occupancyRates.stream().filter(r -> r > 0.9).count();

        ChartSeriesDto occupancyBucketChart = new ChartSeriesDto(
                List.of("<50%", "50-70%", "70-90%", ">90%"),
                numbers(under50, from50to70, from70to90, over90));

        return new OccupancyStatsDto(averageOccupancyRate, hotTripsCount, occupancyBucketChart);
    }

    private AiStatsDto buildAiStats(List<Object[]> aiByStatusRows) {
        long pendingCount = sumStatusCounts(aiByStatusRows, List.of(TripStatus.PENDING_APPROVAL));
        long activatedCount = sumStatusCounts(aiByStatusRows,
                List.of(TripStatus.ACTIVE, TripStatus.DEPARTED, TripStatus.COMPLETED));
        long rejectedCount = sumStatusCounts(aiByStatusRows, List.of(TripStatus.CANCELLED));

        ChartSeriesDto outcomeChart = new ChartSeriesDto(
                List.of("Đang chờ duyệt", "Đã kích hoạt", "Bị từ chối"),
                numbers(pendingCount, activatedCount, rejectedCount));

        return new AiStatsDto(pendingCount + activatedCount + rejectedCount, activatedCount, rejectedCount,
                pendingCount, outcomeChart);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private TripPreviewDto toTripPreviewDto(Trip trip) {
        String routeLabel = trip.getRoute() != null
                ? trip.getRoute().getDeparturePointDisplay() + " → " + trip.getRoute().getDestinationPointDisplay()
                : "(Chưa gán tuyến)";
        boolean autoAssigned = trip.getBus() != null && trip.getDriver() != null;
        return new TripPreviewDto(trip.getId(), routeLabel, trip.getDepartureTime(),
                trip.getTotalSeats(), trip.getTicketsSold(), autoAssigned);
    }

    private long sumStatusCounts(List<Object[]> statusCountRows, Collection<TripStatus> targetStatuses) {
        long total = 0;
        for (Object[] row : statusCountRows) {
            TripStatus status = (TripStatus) row[0];
            Long count = (Long) row[1];
            if (targetStatuses.contains(status) && count != null) {
                total += count;
            }
        }
        return total;
    }

    /** List.of(long...) infer List<Long>, không gán được cho ChartSeriesDto.values (List<Number>). */
    private List<Number> numbers(long... values) {
        List<Number> result = new ArrayList<>(values.length);
        for (long v : values) {
            result.add(v);
        }
        return result;
    }

    private ChartSeriesDto toChartSeries(Map<String, Long> counts) {
        List<String> labels = new ArrayList<>(counts.keySet());
        List<Number> values = labels.stream().map(l -> (Number) counts.get(l)).collect(Collectors.toList());
        return new ChartSeriesDto(labels, values);
    }
}
