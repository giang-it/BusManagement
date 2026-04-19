package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.Driver;
import lombok.Getter;

/**
 * Kết quả của quá trình AI tự động phân công tài nguyên cho chuyến tăng cường.
 */
@Getter
public class AutoAssignResult {

    private final boolean success;
    private final Bus bus;
    private final Driver driver;
    private final Driver assistant; // Phụ xe - có thể null nếu không tìm được
    private final String failureReason;

    private AutoAssignResult(boolean success, Bus bus, Driver driver,
            Driver assistant, String failureReason) {
        this.success = success;
        this.bus = bus;
        this.driver = driver;
        this.assistant = assistant;
        this.failureReason = failureReason;
    }

    public static AutoAssignResult success(Bus bus, Driver driver, Driver assistant) {
        return new AutoAssignResult(true, bus, driver, assistant, null);
    }

    public static AutoAssignResult failure(String reason) {
        return new AutoAssignResult(false, null, null, null, reason);
    }
}