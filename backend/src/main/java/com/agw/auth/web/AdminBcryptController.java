package com.agw.auth.web;

import com.agw.auth.web.dto.BcryptGenerateRequest;
import com.agw.auth.web.dto.BcryptGenerateResponse;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/bcrypt")
public class AdminBcryptController {

    private final PasswordEncoder passwordEncoder;

    public AdminBcryptController(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/generate")
    public BcryptGenerateResponse generate(@Valid @RequestBody BcryptGenerateRequest request) {
        return new BcryptGenerateResponse(passwordEncoder.encode(request.password()));
    }
}
