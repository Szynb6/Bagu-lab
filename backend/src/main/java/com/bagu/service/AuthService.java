package com.bagu.service;

import com.bagu.dto.auth.LoginRequest;
import com.bagu.dto.auth.LoginResponse;
import com.bagu.dto.auth.UserInfoVO;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout();

    UserInfoVO currentUser();
}
