package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Route pairs CT7 (phần M1 của QĐ-22) — bảng 7 ngày của màn Dự báo gom theo cặp tuyến: hai chiều của
 * một cặp đặt liền nhau (chiều rời bến nhà trước, mỗi chiều theo giờ), để nhìn là thấy chiều nào được
 * dự báo đông. Tuyến không có đúng một tuyến chiều ngược nằm ở nhóm cuối.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPairGroupDto {

    /** "Bến A ↔ Bến B", hay nhãn của nhóm tuyến chưa có cặp. */
    private String label;

    /** true khi đây là một cặp thật (hai chiều). */
    private boolean pair;

    /** Các chuỗi của nhóm, theo thứ tự hiển thị. */
    private List<ForecastSeriesDto> series;
}
