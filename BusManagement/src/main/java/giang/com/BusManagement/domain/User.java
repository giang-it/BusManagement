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
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem javadoc field driver
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
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
     * ================================================================
     * QUY ƯỚC equals/hashCode/toString CHO MỌI ENTITY (áp dụng đồng nhất)
     * ================================================================
     * Mọi entity mang @Data, vốn sinh ba hàm đó dựa trên MỌI field. Với entity
     * JPA điều đó sai: chuẩn là so theo ĐỊNH DANH (id), không theo association
     * hay field hay đổi. Quy ước đã chốt:
     *
     * 1) equals/hashCode CHỈ theo id — @EqualsAndHashCode(onlyExplicitlyIncluded
     *    = true) ở class + @EqualsAndHashCode.Include trên đúng field id. Hai bản
     *    ghi bằng nhau khi và chỉ khi cùng một dòng DB. Nhờ đó:
     *      - KHÔNG đệ quy: id là Long, không kéo entity khác. (Trước đây User và
     *        Driver tham chiếu vòng làm hashCode chạy vô tận → StackOverflowError.)
     *      - KHÔNG lazy-load: hashCode không chạm association nên không bắn query
     *        phụ, và không ném LazyInitializationException trên entity detached.
     *      - hashCode ỔN ĐỊNH khi field đổi: bỏ một Trip vào HashSet rồi bán thêm
     *        vé (ticketsSold đổi) vẫn tìm lại được.
     *
     * 2) toString: mọi association được @ToString.Exclude, để log một entity không
     *    kéo cả đồ thị quan hệ và không lazy-load.
     *
     * Lưu ý duy nhất (không xảy ra trong codebase này): hai entity CHƯA save đều
     * có id null nên coi là bằng nhau — đừng bỏ entity chưa save vào HashSet.
     *
     * NGƯỜI THÊM ENTITY MỚI (vd Booking ở Phase 9): thêm @EqualsAndHashCode.Include
     * lên id, @ToString.Exclude lên mọi association, và bổ sung vào
     * EntityEqualsHashCodeCycleTest — test đó canh đúng quy ước này.
     */
    @ToString.Exclude
    @jakarta.persistence.OneToOne(mappedBy = "user", fetch = jakarta.persistence.FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    private Driver driver;
}