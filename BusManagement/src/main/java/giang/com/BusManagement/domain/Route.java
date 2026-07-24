package giang.com.BusManagement.domain;

import java.util.Comparator;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "routes")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem quy ước ở User.driver
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // ĐÃ XÓA: private String departurePoint;
    // ĐÃ XÓA: private String destinationPoint;
    // Lý do: 2 trường String tự do này từng tồn tại song song và không liên kết
    // với entity Station, gây bất nhất dữ liệu. Nguồn sự thật duy nhất giờ là
    // RouteStation, sắp xếp theo stopOrder (xem helper method bên dưới).

    private Double distanceKm;
    private Integer estimatedDuration;

    // @ToString.Exclude: routeStations là LAZY — không kéo cả danh sách trạm dừng
    // mỗi lần log Route (và không nằm trong equals/hashCode vì chỉ id được Include).
    // Xem quy ước ở User.driver.
    @ToString.Exclude
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL)
    @BatchSize(size = 20)
    private List<RouteStation> routeStations;

    @ToString.Exclude // association LAZY — xem quy ước ở User.driver
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suitable_bus_type_id")
    private BusType suitableBusType;

    /**
     * Hàm lấy loại xe phù hợp (Smart Logic)
     * Nếu Admin chưa gán loại xe cụ thể, hệ thống tự gợi ý dựa trên quãng đường
     */
    public BusType getSuitableBusType() {
        if (this.suitableBusType != null) {
            return this.suitableBusType;
        }

        // Logic AI đơn giản: Nếu đi trên 300km thì ưu tiên xe giường nằm (giả định)
        // Lưu ý: Logic này cần được xử lý ở tầng Service nếu cần truy vấn DB tìm
        // BusType giường nằm
        return null;
    }

    // =====================================================================
    // HELPER METHODS — Điểm đi/đến được suy ra từ RouteStation (stopOrder),
    // KHÔNG còn lưu trùng lặp dưới dạng String trong Route.
    // routeStations dùng @BatchSize(20) thay vì JOIN FETCH tường minh trong các
    // query Trip, vì Hibernate KHÔNG cho phép JOIN FETCH 2 collection kiểu List
    // (bag) cùng lúc trong 1 JPQL (MultipleBagFetchException) — ở đây là xung
    // đột với Trip.coDrivers. @BatchSize vẫn tránh N+1 (gom nhiều route lại
    // fetch 1 lần) dù không tránh được hoàn toàn 1 query phụ.
    // =====================================================================

    /**
     * Lộ trình đầy đủ, đã sắp theo stopOrder tăng dần.
     * Tầng Thymeleaf không sắp xếp được (SpEL không hỗ trợ lambda cho
     * Comparator), nên việc sắp xếp để hiển thị được gom về đây — cùng chỗ với
     * getDepartureStation()/getDestinationStation() vốn đã dựa vào stopOrder.
     */
    public List<RouteStation> getOrderedRouteStations() {
        if (routeStations == null) {
            return List.of();
        }
        return routeStations.stream()
                .sorted(Comparator.comparing(RouteStation::getStopOrder))
                .toList();
    }

    /**
     * Trạm xuất phát = bản ghi RouteStation có stopOrder nhỏ nhất.
     * Trả về null nếu tuyến chưa được gán trạm dừng nào.
     */
    public Station getDepartureStation() {
        if (routeStations == null || routeStations.isEmpty()) {
            return null;
        }
        return routeStations.stream()
                .min(Comparator.comparing(RouteStation::getStopOrder))
                .map(RouteStation::getStation)
                .orElse(null);
    }

    /**
     * Trạm đích = bản ghi RouteStation có stopOrder lớn nhất.
     * Trả về null nếu tuyến chưa được gán trạm dừng nào.
     */
    public Station getDestinationStation() {
        if (routeStations == null || routeStations.isEmpty()) {
            return null;
        }
        return routeStations.stream()
                .max(Comparator.comparing(RouteStation::getStopOrder))
                .map(RouteStation::getStation)
                .orElse(null);
    }

    /**
     * Tên hiển thị điểm đi, dùng thay cho departurePoint (String) cũ ở tầng
     * Thymeleaf. Trả về "(Chưa gán trạm)" nếu chưa seed RouteStation, để tránh
     * NullPointerException ngoài view.
     */
    public String getDeparturePointDisplay() {
        Station s = getDepartureStation();
        return (s != null) ? s.getStationName() : "(Chưa gán trạm)";
    }

    /**
     * Tên hiển thị điểm đến, dùng thay cho destinationPoint (String) cũ ở tầng
     * Thymeleaf.
     */
    public String getDestinationPointDisplay() {
        Station s = getDestinationStation();
        return (s != null) ? s.getStationName() : "(Chưa gán trạm)";
    }
}