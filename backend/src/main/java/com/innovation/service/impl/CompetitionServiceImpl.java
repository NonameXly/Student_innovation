package com.innovation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.entity.Competition;
import com.innovation.entity.User;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class CompetitionServiceImpl implements CompetitionService {
    @Autowired
    private CompetitionMapper competitionMapper;

    @Override
    public long newAdd(){
        User user=new User();
        LocalDateTime lastLogin=user.getLastLogin();
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Competition::getCreatedAt,lastLogin);
        Long count = competitionMapper.selectCount(wrapper);
        return count;
    }
}
