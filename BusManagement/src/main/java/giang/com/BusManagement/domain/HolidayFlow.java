package giang.com.BusManagement.domain;

/**
 * Hướng dòng khách của một tuyến vào các dịp lễ (Route.holidayFlow).
 *
 * Dịp lễ, nhất là Tết, dòng khách đi MỘT chiều: trước Tết người lao động rời Hà
 * Nội / TP.HCM về quê, sau Tết đi ngược lại. Hai tuyến cùng hướng có cùng "hình
 * dạng" lễ; hai tuyến ngược hướng thì đỉnh của tuyến này rơi vào lúc tuyến kia
 * vắng, nên học gộp mọi tuyến sẽ làm hai hiệu ứng triệt tiêu nhau. Nhãn này để
 * Demand Forecast mới biết tuyến nào học hệ số lễ chung với tuyến nào (QĐ-8,
 * docs/development/forecast_decisions.md). Người đọc nó là bộ sinh dữ liệu (GĐ2)
 * và bộ dự báo (GĐ5) của đợt thiết kế lại; tới GĐ1 chưa nơi nào đọc.
 *
 * "TP lớn" là phán đoán nghiệp vụ của Admin — nơi tập trung người lao động —
 * KHÔNG suy từ cấp hành chính: Hải Phòng, Cần Thơ, Huế cũng là thành phố trực
 * thuộc trung ương. Route.holidayFlow = null nghĩa là "Không theo chiều" (hai đầu
 * tuyến tương đương, hoặc chưa phân loại); mô hình gom mọi tuyến null thành một
 * nhóm riêng.
 *
 * Chỉ dùng cho dự báo — không đụng phân công, validation hay giá vé.
 *
 * Lưu ý schema: Hibernate ánh xạ enum STRING thành kiểu ENUM gốc của MySQL (cột
 * routes.holiday_flow = enum('LEAVING_HUB','TOWARD_HUB'), giống buses.status), và
 * ddl-auto=update KHÔNG nới một cột đã có — thêm hằng mới thì phải ALTER cột bằng
 * tay.
 */
public enum HolidayFlow {
    /** Rời TP lớn: tuyến chở khách rời một trung tâm lao động (Hà Nội, TP.HCM). */
    LEAVING_HUB,
    /** Về TP lớn: tuyến chở khách về một trung tâm lao động. */
    TOWARD_HUB
}
