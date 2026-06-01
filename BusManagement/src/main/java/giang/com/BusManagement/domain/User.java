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

    @jakarta.persistence.OneToOne(mappedBy = "user", fetch = jakarta.persistence.FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    private Driver driver;
}