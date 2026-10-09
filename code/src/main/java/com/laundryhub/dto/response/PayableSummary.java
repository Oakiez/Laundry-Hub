package com.laundryhub.dto.response;

import com.laundryhub.domain.enums.PayableType;

import java.math.BigDecimal;

/** สรุปสิ่งที่กำลังจะชำระ (ชนิด หมายเลข ยอด) ใช้แสดงหน้าชำระเงินก่อนกดยืนยัน */
public record PayableSummary(PayableType payableType, Long payableId, BigDecimal amount) {
}
