package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// AFTER (đúng - BusType chỉ mô tả loại xe, không mô tả tuyến)
@Entity
@Table(name = "bus_types")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem quy ước ở User.driver
@NoArgsConstructor
public class BusType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false, unique = true)
    private String typeName; // Ví dụ: "Giường nằm", "Ghế ngồi"

    @Column(nullable = false)
    private int capacity; // Số lượng ghế thực tế
}