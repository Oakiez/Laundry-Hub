package com.laundryhub.exception;

/** ไม่พบทรัพยากรที่ร้องขอ → HTTP 404 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
