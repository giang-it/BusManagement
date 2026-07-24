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
    //
    // BỊ LOẠI khỏi equals/hashCode/toString — cùng lý do và cùng quy ước với
    // User.driver (xem javadoc đầy đủ ở đó): Station và RouteStation tham chiếu
    // lẫn nhau, nên nếu giữ lại thì Station.hashCode() → RouteStation.hashCode()
    // → Station.hashCode()... vô tận. Cắt ở phía nghịch đảo (mappedBy).
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL)
    private List<RouteStation> routeStations;
}
