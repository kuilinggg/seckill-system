package com.seckill.user.controller;

import com.seckill.user.common.Result;
import com.seckill.user.entity.User;
import com.seckill.user.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<User> register(@RequestBody User user) {
        return userService.register(user);
    }

    @PostMapping("/login")
    public Result<User> login(@RequestBody LoginRequest request) {
        return userService.login(request.getAccount(), request.getPassword());
    }

    @Data
    public static class LoginRequest {
        private String account;
        private String password;
    }
}
