package giang.com.BusManagement.domain;

public enum BusStatus {
    READY, // Xe sẵn sàng hoạt động
    TRAVELING, // Xe đang trên đường (đang chạy một chuyến nào đó)
    REPAIRING // Xe đang bảo trì, không thể gán vào chuyến mới
}
