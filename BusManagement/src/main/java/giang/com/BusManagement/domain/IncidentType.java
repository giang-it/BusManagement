package giang.com.BusManagement.domain;

/**
 * Phân loại sự cố vận hành được báo về trung tâm điều hành.
 * Hai loại đầu bám đúng yêu cầu gốc ("xe hỏng, vấn đề trên đường"); các loại
 * còn lại tách ra để thống kê được theo nhóm thay vì dồn hết vào một mục.
 */
public enum IncidentType {
    /** Xe gặp hỏng hóc kỹ thuật (chết máy, thủng lốp, hỏng phanh...). */
    VEHICLE_BREAKDOWN,

    /** Tai nạn giao thông. */
    ACCIDENT,

    /** Sự cố ngoại cảnh trên đường: kẹt xe, ngập, cấm đường, thời tiết. */
    ROAD_ISSUE,

    /** Sự cố nhân sự: tài xế/phụ xe ốm, không thể tiếp tục hành trình. */
    STAFF_ISSUE,

    /** Các trường hợp khác không thuộc nhóm trên. */
    OTHER
}
