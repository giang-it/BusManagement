package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "route_stations")
@Data
@NoArgsConstructor
public class RouteStation {

    @EmbeddedId
    private RouteStationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("routeId") // Ánh xạ routeId trong RouteStationId vào trường này
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stationId") // Ánh xạ stationId trong RouteStationId vào trường này
    @JoinColumn(name = "station_id")
    private Station station;

    @Column(name = "stop_order")
    private Integer stopOrder; // Ví dụ: Trạm 1, Trạm 2, Trạm 3...
}