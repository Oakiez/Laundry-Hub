package com.laundryhub.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull Action action) {

    public enum Action { NEXT, CANCEL }
}