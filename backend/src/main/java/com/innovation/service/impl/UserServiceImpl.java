package com.innovation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.common.BusinessException;
import com.innovation.entity.User;
import com.innovation.mapper.UserMapper;
import com.innovation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;


    @Override
    public long newAdd(){
        User user=new User();
        LocalDateTime lastLogin=user.getLastLogin();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreatedAt,lastLogin);

        Long count = userMapper.selectCount(wrapper);
        return count;
    }

    @Override
    public int createUser(User user) {
        // 检查用户名重复
        long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (exists > 0) throw new BusinessException("用户名已存在");
        return userMapper.insert(user);
    }
}
