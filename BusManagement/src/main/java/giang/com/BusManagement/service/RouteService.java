package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.RouteStationRepository;
import giang.com.BusManagement.repository.StationRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Quản lý tuyến đường (CRUD) cùng lộ trình các trạm dừng của tuyến.
 *
 * Lộ trình được lưu ở bảng nối RouteStation với stopOrder tăng dần: trạm đầu
 * (stopOrder nhỏ nhất) là điểm đi, trạm cuối là điểm đến — Route.java suy ra
 * điểm đi/đến từ đây chứ không lưu trùng lặp, nên service này chỉ cần bảo đảm
 * thứ tự stopOrder là đúng.
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStationRepository routeStationRepository;
    private final StationRepository stationRepository;
    private final TripRepository tripRepository;

    /** Một tuyến tối thiểu phải có điểm đi và điểm đến. */
    private static final int MIN_STATIONS_PER_ROUTE = 2;

    public List<Route> findAllWithStations() {
        return routeRepository.findAllWithStations();
    }

    public Optional<Route> findByIdWithStations(Long id) {
        return routeRepository.findByIdWithStations(id);
    }

    public List<Station> findAllStations() {
        return stationRepository.findAll();
    }

    /**
     * Tạo mới hoặc cập nhật một tuyến kèm lộ trình.
     *
     * @param route      thông tin tuyến (distanceKm, estimatedDuration,
     *                   suitableBusType). Có id -> cập nhật, null -> tạo mới.
     * @param stationIds danh sách id trạm THEO ĐÚNG THỨ TỰ lộ trình; stopOrder
     *                   được đánh lại 1..n theo vị trí trong danh sách này.
     */
    @Transactional
    public void saveRoute(Route route, List<Long> stationIds) {
        validateRoute(route, stationIds);

        boolean isNew = route.getId() == null;

        // routeStations của object gửi từ form luôn rỗng; nếu để nguyên rồi save,
        // cascade sẽ không đụng tới các bản ghi cũ (không có orphanRemoval), nên
        // lộ trình được xử lý tường minh bên dưới thay vì dựa vào cascade.
        route.setRouteStations(null);
        Route saved = routeRepository.save(route);

        if (!isNew) {
            // Xóa toàn bộ lộ trình cũ rồi dựng lại: khóa chính của RouteStation là
            // tổ hợp (routeId, stationId) nên không thể "sửa" stationId của một bản
            // ghi — đổi trạm thực chất là xóa dòng cũ + thêm dòng mới.
            routeStationRepository.deleteAll(routeStationRepository.findByRouteIdOrderByStopOrderAsc(saved.getId()));
            routeStationRepository.flush();
        }

        int stopOrder = 1;
        for (Long stationId : stationIds) {
            Station station = stationRepository.findById(stationId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bến xe #" + stationId));

            RouteStation rs = new RouteStation();
            rs.setId(new RouteStationId(saved.getId(), station.getId()));
            rs.setRoute(saved);
            rs.setStation(station);
            rs.setStopOrder(stopOrder++);
            routeStationRepository.save(rs);
        }
    }

    /**
     * Xóa tuyến. Chặn xóa nếu tuyến đã từng được dùng cho chuyến xe nào — giữ
     * nguyên lịch sử vận hành, cùng nguyên tắc BusService.deleteBus() áp dụng
     * cho xe.
     *
     * Các bản ghi RouteStation của tuyến được xóa kèm nhờ cascade = ALL khai báo
     * ở Route.routeStations.
     */
    @Transactional
    public void deleteRoute(Long id) {
        Route route = routeRepository.findByIdWithStations(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tuyến cần xóa với ID: " + id));

        if (tripRepository.existsByRouteId(id)) {
            throw new RuntimeException(
                    "Không thể xóa tuyến này vì đã có chuyến xe sử dụng (dữ liệu lịch sử vận hành và doanh thu "
                            + "phải được giữ nguyên)!");
        }

        routeRepository.delete(route);
    }

    private void validateRoute(Route route, List<Long> stationIds) {
        if (stationIds == null || stationIds.size() < MIN_STATIONS_PER_ROUTE) {
            throw new IllegalArgumentException(
                    "Lộ trình phải có ít nhất " + MIN_STATIONS_PER_ROUTE + " trạm (điểm đi và điểm đến)!");
        }

        // RouteStation dùng khóa chính tổ hợp (routeId, stationId) nên một trạm
        // không thể xuất hiện 2 lần trên cùng tuyến — chặn sớm ở đây để báo lỗi
        // nghiệp vụ rõ ràng thay vì để vỡ ràng buộc khóa chính ở tầng DB.
        Set<Long> unique = new HashSet<>(stationIds);
        if (unique.size() != stationIds.size()) {
            throw new IllegalArgumentException(
                    "Một bến xe chỉ được xuất hiện một lần trong lộ trình. Vui lòng kiểm tra lại các trạm đã chọn!");
        }

        if (route.getDistanceKm() == null || route.getDistanceKm() <= 0) {
            throw new IllegalArgumentException("Quãng đường phải lớn hơn 0 km!");
        }
        if (route.getEstimatedDuration() == null || route.getEstimatedDuration() <= 0) {
            throw new IllegalArgumentException("Thời gian di chuyển dự kiến phải lớn hơn 0 phút!");
        }
    }
}
