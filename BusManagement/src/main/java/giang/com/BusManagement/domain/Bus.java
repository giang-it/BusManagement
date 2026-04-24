package giang.com.BusManagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "buses")
@Data
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String licensePlate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_type_id")
    private BusType busType;

    private String brand;
    private Double odometer;
    private Double lastMaintenanceOdometer;
    private Double maintenanceThreshold;

    @Enumerated(EnumType.STRING)
    private BusStatus status; // READY, TRAVELING, REPAIRING

    public double getKmSinceLastMaintenance() {
        if (odometer == null || lastMaintenanceOdometer == null) return 0.0;
        return odometer - lastMaintenanceOdometer;
    }

    public boolean needsMaintenance() {
        if (maintenanceThreshold == null) return false;
        return getKmSinceLastMaintenance() >= maintenanceThreshold;
    }
}
