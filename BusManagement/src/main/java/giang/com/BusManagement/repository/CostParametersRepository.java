package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.CostParameters;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Kho tham số chi phí (Phase 7, bước 3). Toàn hệ thống chỉ giữ MỘT dòng cấu
 * hình — quy ước "một dòng duy nhất" được thực thi ở CostParameterService, không
 * ở tầng schema, nên repository ở đây chỉ là CRUD chuẩn.
 */
public interface CostParametersRepository extends JpaRepository<CostParameters, Long> {
}
