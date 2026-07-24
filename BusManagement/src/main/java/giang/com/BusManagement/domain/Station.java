package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.util.List;

@Entity
@Table(name = "stations")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem quy ước ở User.driver
@NoArgsConstructor
@AllArgsConstructor
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String stationName;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Liên kết ngược lại với RouteStation (Tùy chọn)
    // Giúp bạn biết trạm này nằm trên những lộ trình nào
    //
    // @ToString.Exclude: association LAZY, không kéo đồ thị khi log (và không nằm
    // trong equals/hashCode vì chỉ id được Include) — xem quy ước ở User.driver.
    @ToString.Exclude
    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL)
    private List<RouteStation> routeStations;
}
