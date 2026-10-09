package com.laundryhub.service;

import com.laundryhub.dto.request.RegisterRequest;
import com.laundryhub.dto.response.UserResponse;

public interface AuthService {

    /** Creates a CUSTOMER account together with its profile. */
    UserResponse register(RegisterRequest request);
}
