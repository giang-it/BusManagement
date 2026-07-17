package giang.com.BusManagement.domain;

/**
 * Tiến độ xử lý một sự cố.
 *
 * KHÁC với TripStatus: các trạng thái này KHÔNG được quản lý bởi một Finite
 * State Machine — Admin được phép chuyển tự do giữa 3 giá trị (kể cả mở lại một
 * sự cố đã đóng nhầm). Ràng buộc duy nhất là mốc thời gian resolvedAt, do
 * IncidentService tự đóng/gỡ dấu theo trạng thái.
 */
public enum IncidentStatus {
    /** Vừa được báo, chưa ai xử lý. */
    OPEN,

    /** Đang được xử lý (điều xe cứu hộ, đưa xe đi sửa...). */
    IN_PROGRESS,

    /** Đã xử lý xong. */
    RESOLVED
}
