package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.RouteStationRepository;
import giang.com.BusManagement.repository.StationRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
     * TẠO MỚI một tuyến kèm lộ trình. CHỈ dùng cho việc tạo — cập nhật phải đi qua
     * {@link #updateRoute(Long, Route, List)}.
     *
     * Trước đây một hàm saveRoute() làm cả hai việc và tự chọn nhánh theo
     * {@code route.getId() == null}. Đó chính là lỗi #21 ở tuyến: controller bind
     * Route bằng @ModelAttribute không có @InitBinder, nên một POST tự chế tới
     * /routes/create mang id có sẵn rơi vào nhánh "cập nhật" — đã tái hiện thật:
     * tuyến 1 từ 120 km/loại 1 thành 999 km/loại NULL (merge chép cả cột form
     * không gửi), lộ trình bị thay, 416 chuyến lịch sử trỏ vào tuyến đó đổi theo.
     * Tripwire dưới đây cùng khuôn BusService.saveBus() (lỗi #16).
     *
     * @param route      thông tin tuyến (distanceKm, estimatedDuration,
     *                   suitableBusType); id phải trống.
     * @param stationIds danh sách id trạm THEO ĐÚNG THỨ TỰ lộ trình; stopOrder
     *                   được đánh lại 1..n theo vị trí trong danh sách này.
     */
    @Transactional
    public void createRoute(Route route, List<Long> stationIds) {
        if (route.getId() != null) {
            throw new IllegalArgumentException(
                    "createRoute() chỉ dùng để tạo tuyến mới (id phải trống). Tuyến #" + route.getId()
                            + " đã tồn tại — muốn sửa thì dùng chức năng Sửa tuyến.");
        }
        validateRoute(route, stationIds);

        // routeStations của object gửi từ form luôn rỗng; lộ trình được dựng tường
        // minh bên dưới thay vì dựa vào cascade.
        route.setRouteStations(null);
        Route saved = routeRepository.save(route);
        rebuildStops(saved, stationIds);
    }

    /**
     * CẬP NHẬT một tuyến: nạp bản ghi có sẵn theo id từ URL rồi chép từng field
     * của form sang — khuôn của BusService.updateBus() và
     * IncidentService.updateIncident(), thay cho merge nguyên object form (merge
     * ghi đè cả cột mà form không gửi).
     *
     * Ba field chép nguyên như form gửi: quãng đường và thời lượng là input
     * `required`; loại xe phù hợp cho phép "-- Không giới hạn --" (null) và null ở
     * đó là ý định hợp lệ của Admin, nên KHÔNG áp luật "trống = giữ nguyên" của
     * BusService cho ô này.
     *
     * Lộ trình: xoá toàn bộ RouteStation cũ rồi dựng lại. Khoá chính của
     * RouteStation là tổ hợp (routeId, stationId) nên không thể "sửa" stationId
     * của một bản ghi — đổi trạm thực chất là xoá dòng cũ + thêm dòng mới; flush()
     * giữa hai bước để dòng mới không đụng dòng cũ trong cùng transaction.
     *
     * CẢNH BÁO, không chặn (mục Group C(c), chủ dự án chốt 2026-09-23): nếu quãng
     * đường ĐỔI trong lúc tuyến còn chuyến DEPARTED, số km mới sẽ được cộng vào
     * odometer của các chuyến đó khi chúng hoàn thành — updateTripStatus() đọc
     * route.distanceKm tại lúc COMPLETED, không phải lúc khởi hành. Không chặn vì
     * hệ thống không phân biệt được "sửa lỗi gõ" (nên áp cho chuyến đang chạy)
     * với "tuyến đổi lộ trình thật"; chụp số km vào chuyến lúc khởi hành là thêm
     * cột schema, trái §3 roadmap. Nên chỉ nói cho Admin biết, đúng kênh
     * "warning" mà TripService.createManualTrip()/updateManualTrip() đang dùng.
     *
     * @return câu cảnh báo để Controller đưa ra flash "warning", hoặc null nếu
     *         không có gì cần báo
     */
    @Transactional
    public String updateRoute(Long id, Route form, List<Long> stationIds) {
        Route existing = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tuyến với ID: " + id));

        validateRoute(form, stationIds);

        // Đọc TRƯỚC khi chép field: cần số km cũ để biết quãng đường có thật sự đổi.
        String warning = null;
        if (!Objects.equals(existing.getDistanceKm(), form.getDistanceKm())) {
            long departedTrips = tripRepository.countByRouteIdAndStatus(id, TripStatus.DEPARTED);
            if (departedTrips > 0) {
                warning = String.format("Tuyến này đang có %d chuyến trên đường (DEPARTED). Quãng đường mới "
                        + "(%s km, trước đây %s km) sẽ được cộng vào odometer của xe khi các chuyến đó hoàn thành. "
                        + "Nếu bạn chỉ sửa lỗi nhập liệu thì đây là điều mong muốn; nếu tuyến thật sự đổi lộ trình "
                        + "thì hãy kiểm tra lại odometer của các xe đó sau khi hoàn thành.",
                        departedTrips, formatKm(form.getDistanceKm()), formatKm(existing.getDistanceKm()));
            }
        }

        existing.setDistanceKm(form.getDistanceKm());
        existing.setEstimatedDuration(form.getEstimatedDuration());
        existing.setSuitableBusType(form.getSuitableBusType());

        // Buông tham chiếu tới collection cũ trước khi xoá các dòng của nó, y như
        // saveRoute() trước đây (merge với routeStations = null): cascade = ALL
        // không có orphanRemoval nên việc xoá phải làm tường minh qua repository.
        existing.setRouteStations(null);
        routeStationRepository.deleteAll(routeStationRepository.findByRouteIdOrderByStopOrderAsc(id));
        routeStationRepository.flush();

        rebuildStops(existing, stationIds);

        return warning;
    }

    /** 120.0 → "120", 120.5 → "120.5" — để câu cảnh báo không hiện "120.0 km". */
    private static String formatKm(Double km) {
        if (km == null) {
            return "?";
        }
        return km == Math.rint(km) ? String.valueOf(km.longValue()) : String.valueOf(km);
    }

    /** Ghi lộ trình cho một tuyến đã có id, stopOrder đánh 1..n theo thứ tự danh sách. */
    private void rebuildStops(Route route, List<Long> stationIds) {
        int stopOrder = 1;
        for (Long stationId : stationIds) {
            Station station = stationRepository.findById(stationId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bến xe #" + stationId));

            RouteStation rs = new RouteStation();
            rs.setId(new RouteStationId(route.getId(), station.getId()));
            rs.setRoute(route);
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
