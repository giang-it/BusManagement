package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "stations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String stationName;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Liên kết ngược lại với RouteStation (Tùy chọn)
    // Giúp bạn biết trạm này nằm trên những lộ trình nào
    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL)
    private List<RouteStation> routeStations;
}