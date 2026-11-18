package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.BusinessException;
import com.innovation.common.R;
import com.innovation.entity.Notice;
import com.innovation.entity.NoticeRead;
import com.innovation.mapper.NoticeMapper;
import com.innovation.mapper.NoticeReadMapper;
import com.innovation.security.SecurityUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final SecurityUtil securityUtil;

    /**
     * 全员/角色广播：recipient_user_id 为空且满足 target_role；
     * 定向通知：recipient_user_id = 当前用户。
     */
    private LambdaQueryWrapper<Notice> visibleWrapper(String role, Long uid) {
        LambdaQueryWrapper<Notice> w = new LambdaQueryWrapper<>();
        w.and(outer -> {
            outer.and(broadcast -> broadcast
                    .isNull(Notice::getRecipientUserId)
                    .and(inner -> inner.isNull(Notice::getTargetRole)
                            .or()
                            .eq(Notice::getTargetRole, role)));
            if (uid != null) {
                outer.or().eq(Notice::getRecipientUserId, uid);
            }
        });
        return w;
    }

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size) {
        String role = securityUtil.getCurrentRole();
        if (!StringUtils.hasText(role)) {
            throw new BusinessException("无法识别当前角色");
        }
        Long uid = securityUtil.getCurrentUserId();
        var wrapper = visibleWrapper(role, uid).orderByDesc(Notice::getCreatedAt);
        var pageResult = noticeMapper.selectPage(new Page<>(page, size), wrapper);
        List<Notice> records = pageResult.getRecords();
        Set<Long> readIds = Collections.emptySet();
        if (uid != null && !records.isEmpty()) {
            List<Long> ids = records.stream().map(Notice::getId).collect(Collectors.toList());
            readIds = noticeReadMapper.selectList(new LambdaQueryWrapper<NoticeRead>()
                            .eq(NoticeRead::getUserId, uid)
                            .in(NoticeRead::getNoticeId, ids))
                    .stream()
                    .map(NoticeRead::getNoticeId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalReadIds = readIds;
        records.forEach(n -> n.setHasRead(uid != null && finalReadIds.contains(n.getId())));
        return R.ok(Map.of("total", pageResult.getTotal(), "list", records));
    }

    @GetMapping("/unread-count")
    public R<?> unreadCount() {
        String role = securityUtil.getCurrentRole();
        if (!StringUtils.hasText(role)) {
            return R.ok(Map.of("count", 0L));
        }
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            return R.ok(Map.of("count", 0L));
        }
        List<Long> visibleIds = noticeMapper.selectList(visibleWrapper(role, uid).select(Notice::getId))
                .stream()
                .map(Notice::getId)
                .collect(Collectors.toList());
        if (visibleIds.isEmpty()) {
            return R.ok(Map.of("count", 0L));
        }
        Set<Long> readSet = noticeReadMapper.selectList(new LambdaQueryWrapper<NoticeRead>()
                        .eq(NoticeRead::getUserId, uid)
                        .in(NoticeRead::getNoticeId, visibleIds))
                .stream()
                .map(NoticeRead::getNoticeId)
                .collect(Collectors.toSet());
        long unread = visibleIds.stream().filter(id -> !readSet.contains(id)).count();
        return R.ok(Map.of("count", unread));
    }

    @PostMapping("/{id}/read")
    public R<?> markRead(@PathVariable Long id) {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        String role = securityUtil.getCurrentRole();
        if (!isVisibleTo(notice, role, uid)) {
            throw new BusinessException("通知不存在");
        }
        long c = noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getNoticeId, id)
                .eq(NoticeRead::getUserId, uid));
        if (c == 0) {
            NoticeRead nr = new NoticeRead();
            nr.setNoticeId(id);
            nr.setUserId(uid);
            noticeReadMapper.insert(nr);
        }
        return R.ok("已读", null);
    }

    private boolean isVisibleTo(Notice notice, String role, Long uid) {
        if (notice.getRecipientUserId() != null) {
            return uid != null && notice.getRecipientUserId().equals(uid);
        }
        if (!StringUtils.hasText(notice.getTargetRole())) {
            return true;
        }
        return StringUtils.hasText(role) && notice.getTargetRole().equalsIgnoreCase(role);
    }

    /** 管理端发布通知（可选；便于后续扩展管理页） */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> create(@Valid @RequestBody NoticeRequest req) {
        Notice n = new Notice();
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setType(StringUtils.hasText(req.getType()) ? req.getType().trim() : "info");
        n.setTargetRole(StringUtils.hasText(req.getTargetRole()) ? req.getTargetRole().trim().toLowerCase() : null);
        n.setLinkUrl(StringUtils.hasText(req.getLinkUrl()) ? req.getLinkUrl().trim() : null);
        noticeMapper.insert(n);
        return R.ok("发布成功", Map.of("id", n.getId()));
    }

    @Data
    static class NoticeRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
        private String content;
        private String type;
        private String targetRole;
        private String linkUrl;
    }
}
