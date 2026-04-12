package giang.com.BusManagement.domain;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "routes")
@Data
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String departurePoint;
    private String destinationPoint;
    private Double distanceKm;
    private Integer estimatedDuration;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL)
    private List<RouteStation> routeStations;

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
}
