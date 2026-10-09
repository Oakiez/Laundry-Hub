package com.laundryhub.service.payment;

import com.laundryhub.domain.entity.Payment;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

/**
 * หยอดเหรียญ: ใช้ได้เฉพาะกับรอบใช้เครื่อง (Self-Service) ซึ่งหยอดที่ตัวเครื่อง จึงถือว่าชำระสำเร็จทันที
 * ออเดอร์ฝากซักจ่ายด้วยเหรียญไม่ได้ → โยน {@link BusinessRuleException} (400) ไม่บันทึก payment
 * (ไม่คืน FAILED เพราะแถว FAILED จะชน UNIQUE ทำให้จ่ายใหม่ด้วยวิธีอื่นไม่ได้)
 *
 * เพิ่มคลาสนี้ตัวเดียวก็ใช้งานได้ โดยไม่ต้องแก้ {@link PaymentProcessorFactory} หรือ PaymentServiceImpl (OCP)
 */
@Component
public class CoinProcessor implements PaymentProcessor {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.COIN;
    }

    @Override
    public PaymentStatus process(Payment payment) {
        if (payment.getPayableType() != PayableType.USAGE_SESSION) {
            throw new BusinessRuleException("Coin payment is only available for machine sessions");
        }
        return PaymentStatus.PAID;
    }
}
