package com.laundryhub.service.payment;

import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory Method: เลือก {@link PaymentProcessor} ตาม {@link PaymentMethod}.
 * Spring ฉีด processor ทุกตัวที่เป็น @Component เข้ามาทาง constructor
 * เพิ่มวิธีชำระใหม่ = เพิ่มคลาส @Component ใหม่ โดยไม่ต้องแก้ไฟล์นี้ (OCP).
 */
@Component
public class PaymentProcessorFactory {

    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    public PaymentProcessorFactory(List<PaymentProcessor> processorList) {
        for (PaymentProcessor processor : processorList) {
            PaymentProcessor previous = processors.put(processor.method(), processor);
            if (previous != null) {
                throw new IllegalStateException("Duplicate PaymentProcessor for method " + processor.method());
            }
        }
    }

    public PaymentProcessor getProcessor(PaymentMethod method) {
        PaymentProcessor processor = method == null ? null : processors.get(method);
        if (processor == null) {
            throw new BusinessRuleException("Unsupported payment method: " + method);
        }
        return processor;
    }
}
