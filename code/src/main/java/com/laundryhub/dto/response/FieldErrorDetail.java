package com.laundryhub.dto.response;

/** รายละเอียด error ของ field เดียว (ใช้กับ validation) */
public record FieldErrorDetail(String field, String message) {
}
