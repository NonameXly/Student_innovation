package com.innovation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.entity.Registration;
import com.innovation.entity.User;
import com.innovation.mapper.RegistrationMapper;
import com.innovation.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class RegistrationServiceImpl implements RegistrationService {
    @Autowired
    private RegistrationMapper registrationMapper;

    @Override
    public long newAdd(){
        User user=new User();
        LocalDateTime lastLogin=user.getLastLogin();
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Registration::getCreatedAt,lastLogin);

        Long count = registrationMapper.selectCount(wrapper);
        return count;
    }
}
