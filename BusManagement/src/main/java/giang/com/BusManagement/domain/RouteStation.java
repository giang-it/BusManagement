package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "route_stations")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id (khóa kép) — xem quy ước ở User.driver
@NoArgsConstructor
public class RouteStation {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private RouteStationId id;

    // @ToString.Exclude: association LAZY (route/station cũng đã nằm sẵn trong id
    // qua @MapsId) — xem quy ước ở User.driver.
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("routeId") // Ánh xạ routeId trong RouteStationId vào trường này
    @JoinColumn(name = "route_id")
    private Route route;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stationId") // Ánh xạ stationId trong RouteStationId vào trường này
    @JoinColumn(name = "station_id")
    private Station station;

    @Column(name = "stop_order")
    private Integer stopOrder; // Ví dụ: Trạm 1, Trạm 2, Trạm 3...
}
