package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bus_types")
@Data
@NoArgsConstructor
public class BusType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String typeName; // Ví dụ: "Giường nằm", "Ghế ngồi"

    @Column(nullable = false)
    private int capacity; // Số lượng ghế thực tế

    // Quy định loại xe phù hợp cho tuyến này (ví dụ: Tuyến Bắc-Nam -> Giường nằm)
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