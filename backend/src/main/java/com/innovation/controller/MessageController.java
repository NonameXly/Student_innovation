package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.R;
import com.innovation.entity.Message;
import com.innovation.entity.MessageLike;
import com.innovation.entity.MessageReply;
import com.innovation.entity.User;
import com.innovation.mapper.MessageLikeMapper;
import com.innovation.mapper.MessageMapper;
import com.innovation.mapper.MessageReplyMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageMapper messageMapper;
    private final MessageReplyMapper replyMapper;
    private final MessageLikeMapper likeMapper;
    private final UserMapper userMapper;
    private final SecurityUtil securityUtil;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size,
                     @RequestParam(required = false) String keyword) {
        Long currentUserId = securityUtil.getCurrentUserId();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .orderByDesc(Message::getCreatedAt);
        applyKeywordFilter(wrapper, keyword);
        var pageResult = messageMapper.selectPage(new Page<>(page, size), wrapper);

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Message msg : pageResult.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", msg.getId());
            map.put("content", msg.getContent());
            map.put("tag", msg.getTag());
            map.put("likes", msg.getLikes());
            map.put("time", msg.getCreatedAt() != null ? msg.getCreatedAt().format(FMT) : "");
            map.put("userId", msg.getUserId());

            // 点赞状态
            long likeCount = likeMapper.selectCount(new LambdaQueryWrapper<MessageLike>()
                    .eq(MessageLike::getMessageId, msg.getId())
                    .eq(MessageLike::getUserId, currentUserId));
            map.put("liked", likeCount > 0);

            // 作者信息
            User author = userMapper.selectById(msg.getUserId());
            map.put("author", author != null ? author.getName() : "匿名");

            // 回复列表
            List<MessageReply> replies = replyMapper.selectList(new LambdaQueryWrapper<MessageReply>()
                    .eq(MessageReply::getMessageId, msg.getId())
                    .orderByAsc(MessageReply::getCreatedAt));
            List<Map<String, Object>> replyList = new ArrayList<>();
            for (MessageReply r : replies) {
                Map<String, Object> rMap = new LinkedHashMap<>();
                rMap.put("id", r.getId());
                rMap.put("content", r.getContent());
                rMap.put("time", r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "");
                User rUser = userMapper.selectById(r.getUserId());
                rMap.put("author", rUser != null ? rUser.getName() : "匿名");
                replyList.add(rMap);
            }
            map.put("replies", replyList);
            resultList.add(map);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", resultList));
    }

    @PostMapping
    public R<?> create(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return R.fail("留言内容不能为空");
        }
        Message msg = new Message();
        msg.setUserId(securityUtil.getCurrentUserId());
        msg.setContent(content.trim());
        msg.setTag(body.get("tag"));
        msg.setLikes(0);
        messageMapper.insert(msg);
        return R.ok("发布成功", Map.of("id", msg.getId()));
    }

    @PostMapping("/{id}/like")
    public R<?> like(@PathVariable Long id) {
        Long userId = securityUtil.getCurrentUserId();
        long exists = likeMapper.selectCount(new LambdaQueryWrapper<MessageLike>()
                .eq(MessageLike::getMessageId, id)
                .eq(MessageLike::getUserId, userId));
        if (exists > 0) {
            return R.fail("已点赞");
        }
        MessageLike ml = new MessageLike();
        ml.setMessageId(id);
        ml.setUserId(userId);
        likeMapper.insert(ml);
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id).setSql("likes = likes + 1"));
        Message msg = messageMapper.selectById(id);
        return R.ok(Map.of("likes", msg.getLikes(), "liked", true));
    }

    @DeleteMapping("/{id}/like")
    public R<?> unlike(@PathVariable Long id) {
        Long userId = securityUtil.getCurrentUserId();
        likeMapper.delete(new LambdaQueryWrapper<MessageLike>()
                .eq(MessageLike::getMessageId, id)
                .eq(MessageLike::getUserId, userId));
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id).setSql("likes = GREATEST(likes - 1, 0)"));
        Message msg = messageMapper.selectById(id);
        return R.ok(Map.of("likes", msg.getLikes(), "liked", false));
    }

    @PostMapping("/{id}/reply")
    public R<?> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return R.fail("回复内容不能为空");
        }
        Long userId = securityUtil.getCurrentUserId();
        MessageReply reply = new MessageReply();
        reply.setMessageId(id);
        reply.setUserId(userId);
        reply.setContent(content.trim());
        replyMapper.insert(reply);

        User user = userMapper.selectById(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", reply.getId());
        result.put("author", user != null ? user.getName() : "匿名");
        result.put("content", reply.getContent());
        result.put("time", reply.getCreatedAt() != null ? reply.getCreatedAt().format(FMT) : "");
        return R.ok(result);
    }

    /**
     * 删除整条留言及点赞、回复：教师/管理员可删任意帖；其他用户仅可删除本人发布的留言。
     */
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        Long uid = securityUtil.getCurrentUserId();
        Message msg = messageMapper.selectById(id);
        if (msg == null) {
            return R.fail("留言不存在");
        }
        boolean owner = uid != null && msg.getUserId() != null && msg.getUserId().equals(uid);
        if (!securityUtil.isStaff() && !owner) {
            return R.fail(403, "无权删除留言");
        }
        likeMapper.delete(new LambdaQueryWrapper<MessageLike>().eq(MessageLike::getMessageId, id));
        replyMapper.delete(new LambdaQueryWrapper<MessageReply>().eq(MessageReply::getMessageId, id));
        messageMapper.deleteById(id);
        return R.ok();
    }

    /**
     * 模糊搜索：留言正文、标签、作者姓名/用户名，以及回复内容（命中回复时返回对应主帖）。
     */
    private void applyKeywordFilter(LambdaQueryWrapper<Message> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        List<Long> authorIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .like(User::getName, kw)
                        .or()
                        .like(User::getUsername, kw))
                .stream()
                .map(User::getId)
                .distinct()
                .collect(Collectors.toList());
        List<Long> replyMessageIds = replyMapper.selectList(new LambdaQueryWrapper<MessageReply>()
                        .like(MessageReply::getContent, kw))
                .stream()
                .map(MessageReply::getMessageId)
                .distinct()
                .collect(Collectors.toList());

        wrapper.and(w -> {
            w.like(Message::getContent, kw);
            w.or().like(Message::getTag, kw);
            if (!authorIds.isEmpty()) {
                w.or().in(Message::getUserId, authorIds);
            }
            if (!replyMessageIds.isEmpty()) {
                w.or().in(Message::getId, replyMessageIds);
            }
        });
    }

}
