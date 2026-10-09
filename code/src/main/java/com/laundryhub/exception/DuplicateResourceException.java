package com.laundryhub.exception;

/** ข้อมูลซ้ำ เช่น ชำระเงินซ้ำ → HTTP 409 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
