package com.innovation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.entity.Message;
import com.innovation.entity.User;
import com.innovation.mapper.MessageMapper;
import com.innovation.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class MessageServiceImpl implements MessageService {
    @Autowired
    private MessageMapper messageMapper;

    @Override
    public long newAdd(){
        User user=new User();
        LocalDateTime lastLogin=user.getLastLogin();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Message::getCreatedAt,lastLogin);

        Long count = messageMapper.selectCount(wrapper);
        return count;
    }
}
