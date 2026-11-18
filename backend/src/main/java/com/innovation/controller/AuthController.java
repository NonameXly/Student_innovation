package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.common.BusinessException;
import com.innovation.common.R;
import com.innovation.entity.User;
import com.innovation.mapper.UserMapper;
import com.innovation.security.JwtTokenProvider;
import com.innovation.service.CompetitionStatusScheduleService;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import com.innovation.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CompetitionStatusScheduleService competitionStatusScheduleService;
    @Autowired
    UserService userService;

    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return R.fail(401, "用户名或密码错误");
        }
        if (!user.getActive()) {
            return R.fail(403, "账号已被禁用");
        }
        // 更新最后登录时间
        user.setLastLogin(LocalDateTime.now());
        userMapper.updateById(user);

        // 登录成功后同步刷新一次竞赛状态，避免白天首次登录仍看到过期状态
        competitionStatusScheduleService.refreshCompetitionStatusNow();

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return R.ok(Map.of(
                "token", token,
                "userId", user.getId(),
                "role", user.getRole(),
                "username", user.getUsername(),
                "name", user.getName()
        ));
    }

    /**
     * 在 Access Token 尚未过期时换取新 Token，用于前端滑动续期（仍在使用的会话）。
     */
    @PostMapping("/refresh")
    public R<?> refresh(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearer) || !bearer.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = bearer.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return R.fail(401, "登录已过期，请重新登录");
        }
        Long userId = jwtTokenProvider.getUserId(token);
        User user = userMapper.selectById(userId);
        if (user == null || !user.getActive()) {
            return R.fail(403, "账号不可用");
        }
        String newToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return R.ok(Map.of("token", newToken));
    }

    @PostMapping("/logout")
    public R<?> logout() {
        return R.ok("登出成功", null);
    }

    @PostMapping("/register")
    public R<?> register(@RequestBody UserController.UserRequest req) {
        String role = StringUtils.hasText(req.getRole()) ? req.getRole() : "student";
        User user = new User();
        user.setName(req.getName());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setRole(role);
        user.setCollege(req.getCollege());
        if ("student".equals(role)) {
            if (!StringUtils.hasText(req.getStudentId())) {
                throw new BusinessException("学号不能为空");
            }
            user.setStudentId(req.getStudentId().trim());
        } else {
            user.setStudentId(null);
        }
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setActive(true);
        userService.createUser(user);
        return R.ok("用户创建成功", Map.of("id", user.getId()));
    }

    @Data
    static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
