package com.seckill.user.service;

import com.seckill.user.common.Result;
import com.seckill.user.entity.User;

public interface UserService {

    Result<User> register(User user);

    Result<User> login(String account, String password);
}
