package com.laundryhub.exception;

/** จองเครื่องในช่วงเวลาที่ซ้อนกัน → HTTP 409 */
public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        super(message);
    }
}
