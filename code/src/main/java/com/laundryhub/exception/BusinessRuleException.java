package com.laundryhub.exception;

/** ผิดกฎทางธุรกิจ เช่น เปลี่ยนสถานะผิดลำดับ → HTTP 400 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
