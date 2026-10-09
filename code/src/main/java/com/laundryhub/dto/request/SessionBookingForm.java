package com.laundryhub.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

/** Web input deliberately excludes owner and amount; both come from the server. */
@Getter
@Setter
public class SessionBookingForm {
    @NotNull(message = "กรุณาเลือกเวลาเริ่ม")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @NotNull(message = "กรุณาระบุระยะเวลา")
    @Min(value = 10, message = "ระยะเวลาต้องไม่น้อยกว่า 10 นาที")
    @Max(value = 180, message = "ระยะเวลาต้องไม่เกิน 180 นาที")
    private Integer durationMinutes = 30;
}
