package com.bagu.service;

import com.bagu.dto.auth.DeleteAccountRequest;
import com.bagu.dto.auth.LoginRequest;
import com.bagu.dto.auth.LoginResponse;
import com.bagu.dto.auth.RegisterRequest;
import com.bagu.dto.auth.ResetPasswordRequest;
import com.bagu.dto.auth.SendCodeRequest;
import com.bagu.dto.auth.UserInfoVO;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void sendEmailCode(SendCodeRequest request, String clientIp);

    void sendDeleteAccountCode(String clientIp);

    void deleteAccount(DeleteAccountRequest request);

    LoginResponse register(RegisterRequest request);

    void resetPassword(ResetPasswordRequest request);

    void logout();

    UserInfoVO currentUser();
}
