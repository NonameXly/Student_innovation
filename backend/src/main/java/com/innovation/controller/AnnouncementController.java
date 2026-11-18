package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.BusinessException;
import com.innovation.common.R;
import com.innovation.entity.Announcement;
import com.innovation.entity.User;
import com.innovation.mapper.AnnouncementMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementMapper announcementMapper;
    private final UserMapper userMapper;
    private final SecurityUtil securityUtil;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size,
                     @RequestParam(required = false) String type) {
        var wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(StringUtils.hasText(type), Announcement::getType, type)
                .orderByDesc(Announcement::getPinned)
                .orderByDesc(Announcement::getCreatedAt);
        var pageResult = announcementMapper.selectPage(new Page<>(page, size), wrapper);
        return R.ok(Map.of("total", pageResult.getTotal(), "list", pageResult.getRecords()));
    }

    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        return R.ok(ann);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> create(@Valid @RequestBody AnnouncementRequest req) {
        Long userId = securityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        Announcement ann = new Announcement();
        ann.setTitle(req.getTitle());
        ann.setType(req.getType());
        ann.setContent(req.getContent());
        ann.setPinned(req.getPinned() != null && req.getPinned());
        ann.setAuthor(user != null ? user.getName() : "");
        ann.setAuthorId(userId);
        announcementMapper.insert(ann);
        return R.ok("发布成功", Map.of("id", ann.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody AnnouncementRequest req) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        ann.setTitle(req.getTitle());
        ann.setType(req.getType());
        ann.setContent(req.getContent());
        if (req.getPinned() != null) ann.setPinned(req.getPinned());
        announcementMapper.updateById(ann);
        return R.ok("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> delete(@PathVariable Long id) {
        announcementMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    @PutMapping("/{id}/pin")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> togglePin(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        ann.setPinned(body.getOrDefault("pinned", false));
        announcementMapper.updateById(ann);
        return R.ok(ann.getPinned() ? "已置顶" : "已取消置顶", null);
    }

    @PostMapping("/{id}/view")
    public R<?> addView(@PathVariable Long id) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        announcementMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, id)
                .setSql("views = views + 1"));
        return R.ok(Map.of("views", ann.getViews() + 1));
    }

    @Data
    static class AnnouncementRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
        private String type = "normal";
        @NotBlank(message = "内容不能为空")
        private String content;
        private Boolean pinned;
    }
}
