package com.seckill.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.user.common.Result;
import com.seckill.user.entity.User;
import com.seckill.user.mapper.UserMapper;
import com.seckill.user.service.UserService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public Result<User> register(User user) {
        if (user == null) {
            return Result.error("请求参数不能为空");
        }
        if (!StringUtils.hasText(user.getUsername())) {
            return Result.error("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getPhone())) {
            return Result.error("手机号不能为空");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            return Result.error("密码不能为空");
        }

        Long usernameCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, user.getUsername()));
        if (usernameCount != null && usernameCount > 0) {
            return Result.error("用户名已存在");
        }

        Long phoneCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, user.getPhone()));
        if (phoneCount != null && phoneCount > 0) {
            return Result.error("手机号已存在");
        }

        String encryptedPassword = DigestUtils.md5DigestAsHex(
                user.getPassword().getBytes(StandardCharsets.UTF_8));
        user.setPassword(encryptedPassword);

        int rows = userMapper.insert(user);
        if (rows <= 0) {
            return Result.error("注册失败");
        }

        user.setPassword(null);
        return Result.success("注册成功", user);
    }

    @Override
    public Result<User> login(String account, String password) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            return Result.error("账号或密码不能为空");
        }

        User dbUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getPhone, account)
                .last("limit 1"));

        if (dbUser == null) {
            return Result.error("账号或密码错误");
        }

        String encryptedPassword = DigestUtils.md5DigestAsHex(
                password.getBytes(StandardCharsets.UTF_8));
        if (!encryptedPassword.equals(dbUser.getPassword())) {
            return Result.error("账号或密码错误");
        }

        dbUser.setPassword(null);
        return Result.success("登录成功", dbUser);
    }
}
