package com.laundryhub.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** รูปแบบ error มาตรฐานของทุก endpoint (ตาม 00_SHARED_CONTRACTS ข้อ 7) */
@Schema(description = "รูปแบบ error มาตรฐานของ API")
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {
}
