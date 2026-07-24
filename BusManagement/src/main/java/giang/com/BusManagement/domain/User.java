package giang.com.BusManagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String password; // Nên được mã hóa BCrypt
    private String fullName;

    @Column(unique = true)
    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN, DRIVER, USER

    private Boolean status = true;

    /**
     * Quan hệ NGƯỢC tới Driver (phía mappedBy).
     *
     * BỊ LOẠI khỏi equals/hashCode/toString. Lý do: User và Driver tham chiếu lẫn
     * nhau, mà @Data sinh ba hàm đó dựa trên MỌI field — giữ lại thì
     * User.hashCode() gọi Driver.hashCode() rồi lại gọi User.hashCode()... vô tận
     * và ném StackOverflowError. Vòng lặp này có thật ngay sau một findById bình
     * thường: Hibernate gán lại chính đối tượng Driver đang nằm trong persistence
     * context vào đây, nên hai bên trỏ vòng vào nhau trong bộ nhớ.
     *
     * Cắt MỘT phía là đủ để đứt vòng; cắt ở phía nghịch đảo (mappedBy) là quy ước
     * được áp dụng ĐỒNG NHẤT cho cả ba quan hệ hai chiều của domain — xem
     * Route.routeStations và Station.routeStations.
     *
     * Hệ quả ngữ nghĩa duy nhất: hai User chỉ khác nhau ở hồ sơ Driver nay được
     * coi là bằng nhau. Không đáng kể, vì id và username đều unique và được so
     * sánh TRƯỚC field này. Trước khi sửa, hashCode() luôn luôn ném exception nên
     * không có code nào có thể đang phụ thuộc vào hành vi cũ.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @jakarta.persistence.OneToOne(mappedBy = "user", fetch = jakarta.persistence.FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    private Driver driver;
}