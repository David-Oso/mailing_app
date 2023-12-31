package com.mail.mini_mailing_app.spring.boot.services.impl;

import com.mail.mini_mailing_app.spring.boot.data.dto.request.AdminLoginRequest;
import com.mail.mini_mailing_app.spring.boot.data.dto.response.AuthenticationResponse;
import com.mail.mini_mailing_app.spring.boot.data.model.Admin;
import com.mail.mini_mailing_app.spring.boot.data.model.AppUser;
import com.mail.mini_mailing_app.spring.boot.data.model.Role;
import com.mail.mini_mailing_app.spring.boot.data.repository.AdminRepository;
import com.mail.mini_mailing_app.spring.boot.services.AdminService;
import com.mail.mini_mailing_app.spring.boot.services.AppUserService;
import com.mail.mini_mailing_app.spring.boot.services.JwtTokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final AdminRepository adminRepository;
    private final AppUserService appUserService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;


    @Value("${adminFirstName}")
    private String adminFirstName;
    @Value("${adminLastName}")
    private String adminLastName;
    @Value("${adminPhoneNumber}")
    private String adminPhoneNumber;
    @Value("${adminEmail}")
    private String adminEmail;
    @Value("${adminPassword}")
    private String adminPassword;
    @Value("${adminId}")
    private String adminId;

//    @PostConstruct
    private void registerAdmin(){
        String encodedPassword = passwordEncoder.encode(adminPassword);
        AppUser appUser = AppUser.builder()
                .email(adminEmail)
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .isBlocked(false)
                .isEnabled(true)
                .phoneNumber(adminPhoneNumber)
                .password(encodedPassword)
                .role(Role.ADMIN)
                .build();
        Admin admin = new Admin();
        admin.setUserDetails(appUser);
        admin.setIdentity(adminId);
        adminRepository.save(admin);
    }

    @Override
    public AuthenticationResponse login(AdminLoginRequest request) {
        try{
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));
            AppUser appUser = appUserService.getUserByEmail(request.getEmail());
            Admin admin = adminRepository.findByUserDetails(appUser).orElse(null);
            if(admin != null && admin.getIdentity().equals(request.getIdentity())){
                String message = "Authentication Successful";
                return this.jwtTokenService
                        .getAuthenticationResponse(appUser, message);
            }
        }catch (AuthenticationException exception){
            throw new RuntimeException("Incorrect password", exception);
        }
        throw new RuntimeException("Authentication Failure");
    }
}
