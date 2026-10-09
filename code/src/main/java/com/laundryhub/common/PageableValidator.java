package com.laundryhub.common;

import com.laundryhub.exception.BusinessRuleException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * ตรวจฟิลด์ที่ client ขอเรียง (?sort=...) ก่อนส่งถึง repository
 * ถ้าปล่อยให้ชื่อฟิลด์ที่ไม่มีหลุดไป Spring Data จะ error และกลายเป็น 500
 * ตรวจที่นี่แล้วตอบ 400 พร้อมบอกฟิลด์ที่ใช้ได้ (และไม่เปิดให้เรียงด้วยฟิลด์ภายในที่ไม่ตั้งใจเปิด)
 */
public final class PageableValidator {

    private PageableValidator() {
    }

    public static void requireSortableBy(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BusinessRuleException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + new TreeSet<>(allowedProperties));
            }
        }
    }
}
