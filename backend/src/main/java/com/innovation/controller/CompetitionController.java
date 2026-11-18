package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.BusinessException;
import com.innovation.common.R;
import com.innovation.entity.Competition;
import com.innovation.entity.Score;
import com.innovation.entity.User;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.mapper.ScoreMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/competition")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionMapper competitionMapper;
    private final ScoreMapper scoreMapper;
    private final UserMapper userMapper;
    private final SecurityUtil securityUtil;

    @GetMapping("/list")
    public R<?> list(@RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String category,
                     @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size) {
        String st = StringUtils.hasText(status) ? status.trim() : "";
        boolean draftOnly = "draft".equalsIgnoreCase(st);

        var wrapper = new LambdaQueryWrapper<Competition>()
                .like(StringUtils.hasText(keyword), Competition::getName, keyword)
                .eq(StringUtils.hasText(category), Competition::getCategory, category)
                .orderByDesc(Competition::getCreatedAt);

        if (!securityUtil.isAdmin()) {
            wrapper.eq(Competition::getIsDraft, false);
            if (StringUtils.hasText(st) && !draftOnly) {
                wrapper.eq(Competition::getStatus, st);
            }
            if (draftOnly) {
                wrapper.eq(Competition::getId, -1L);
            }
        } else if (draftOnly) {
            wrapper.eq(Competition::getIsDraft, true);
            Long uid = securityUtil.getCurrentUserId();
            if (uid != null) {
                wrapper.eq(Competition::getCreatedBy, uid);
            } else {
                wrapper.eq(Competition::getId, -1L);
            }
        } else if (StringUtils.hasText(st)) {
            wrapper.eq(Competition::getStatus, st);
            wrapper.eq(Competition::getIsDraft, false);
        } else {
            Long uid = securityUtil.getCurrentUserId();
            if (uid != null) {
                wrapper.and(w -> w.eq(Competition::getIsDraft, false)
                        .or(sub -> sub.eq(Competition::getIsDraft, true).eq(Competition::getCreatedBy, uid)));
            } else {
                wrapper.eq(Competition::getIsDraft, false);
            }
        }
        var pageResult = competitionMapper.selectPage(new Page<>(page, size), wrapper);
        return R.ok(Map.of("total", pageResult.getTotal(), "list", pageResult.getRecords()));
    }

    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        Competition comp = competitionMapper.selectById(id);
        if (comp == null) throw new BusinessException("竞赛不存在");
        assertDraftVisibleToCurrentUser(comp);
        fillAdvisorDisplay(comp);
        long published = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getCompetitionId, id)
                .eq(Score::getShow, 1));
        comp.setScorePublished(published > 0);
        return R.ok(comp);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> create(@Valid @RequestBody CompetitionRequest req) {
        validateCreateTimeline(req);
        validateResponsibleTeacher(req.getAdvisor());
        Competition comp = toEntity(req);
        comp.setCreatedBy(securityUtil.getCurrentUserId());
        comp.setStatus(calculateStatus(req.getEnrollStart(), req.getEnrollEnd(), req.getStartDate(), req.getEndDate()));
        competitionMapper.insert(comp);
        return R.ok("发布成功", Map.of("id", comp.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody CompetitionRequest req) {
        Competition comp = competitionMapper.selectById(id);
        if (comp == null) throw new BusinessException("竞赛不存在");
        assertDraftMutableByCurrentUser(comp);
        validateUpdateTimeline(req);
        validateResponsibleTeacher(req.getAdvisor());

        Competition updated = toEntity(req);
        updated.setId(id);
        LocalDate enrollStart = req.getEnrollStart();
        LocalDate enrollEnd = req.getEnrollEnd();
        LocalDate startDate = req.getStartDate();
        LocalDate endDate = req.getEndDate();
        String status = calculateStatus(enrollStart, enrollEnd, startDate, endDate);
        updated.setStatus(status);
        competitionMapper.updateById(updated);
        return R.ok("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> delete(@PathVariable Long id) {
        Competition comp = competitionMapper.selectById(id);
        if (comp == null) throw new BusinessException("竞赛不存在");
        assertDraftMutableByCurrentUser(comp);
        competitionMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    @PostMapping("/draft")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> saveDraft(@RequestBody CompetitionRequest req) {
        Competition comp = toEntity(req);
        comp.setIsDraft(true);
        comp.setCreatedBy(securityUtil.getCurrentUserId());
        competitionMapper.insert(comp);
        return R.ok("草稿已保存", Map.of("id", comp.getId()));
    }

    /** 草稿仅创建人可在详情中查看（其他人视为不存在）。 */
    private void assertDraftVisibleToCurrentUser(Competition comp) {
        if (!Boolean.TRUE.equals(comp.getIsDraft())) {
            return;
        }
        Long uid = securityUtil.getCurrentUserId();
        if (uid != null && uid.equals(comp.getCreatedBy())) {
            return;
        }
        throw new BusinessException("竞赛不存在");
    }

    /** 草稿仅创建人可改删；非草稿仍走原有业务校验。 */
    private void assertDraftMutableByCurrentUser(Competition comp) {
        if (!Boolean.TRUE.equals(comp.getIsDraft())) {
            return;
        }
        Long uid = securityUtil.getCurrentUserId();
        if (uid != null && uid.equals(comp.getCreatedBy())) {
            return;
        }
        throw new BusinessException("无权操作该草稿");
    }

    private Competition toEntity(CompetitionRequest req) {
        Competition comp = new Competition();
        comp.setName(req.getName());
        comp.setDescription(req.getDescription());
        comp.setCategory(req.getCategory());
        comp.setLevel(req.getLevel());
        comp.setEnrollStart(req.getEnrollStart());
        comp.setEnrollEnd(req.getEnrollEnd());
        comp.setStartDate(req.getStartDate());
        comp.setEndDate(req.getEndDate());
        comp.setRequirements(req.getRequirements());
        comp.setAwards(req.getAwards());
        comp.setMaxParticipants(req.getMaxParticipants());
        comp.setTeamMin(req.getTeamMin());
        comp.setTeamMax(req.getTeamMax());
        comp.setAdvisor(req.getAdvisor());
        comp.setContact(req.getContact());
        comp.setProjectUploadEnabled(Boolean.TRUE.equals(req.getProjectUploadEnabled()));
        comp.setStatus("preparing");
        comp.setIsDraft(false);
        return comp;
    }

    /**
     * 日期字段按「开始日 0:00 起、结束日当日 24:00 止」理解（仅存 LocalDate，含首尾两日）。
     * 报名截止日的次日及之后才可选作竞赛开始日（截止日与竞赛开始日不得同一天）。
     */
    private String calculateStatus(LocalDate enrollStart, LocalDate enrollEnd, LocalDate startDate, LocalDate endDate) {
        LocalDate now = LocalDate.now();
        if (endDate != null && now.isAfter(endDate)) {
            return "finished";
        }
        if (enrollStart != null && enrollEnd != null && !now.isBefore(enrollStart) && !now.isAfter(enrollEnd)) {
            return "enrolling";
        }
        if (startDate != null && endDate != null && !now.isBefore(startDate) && !now.isAfter(endDate)) {
            return "ongoing";
        }
        if (enrollEnd != null && startDate != null && now.isAfter(enrollEnd) && now.isBefore(startDate)) {
            return "enrollend";
        }
        return "preparing";
    }

    private void validateCreateTimeline(CompetitionRequest req) {
        LocalDate now = LocalDate.now();
        LocalDate enrollStart = req.getEnrollStart();
        LocalDate enrollEnd = req.getEnrollEnd();
        LocalDate startDate = req.getStartDate();
        LocalDate endDate = req.getEndDate();

        if (enrollStart != null && enrollStart.isBefore(now)) {
            throw new BusinessException("报名开始时间不能早于当前日期");
        }
        if (enrollStart != null && enrollEnd != null && enrollEnd.isBefore(enrollStart)) {
            throw new BusinessException("报名截止日期不能早于报名开始日期（可为同一天，均为当日 0:00～24:00）");
        }
        if (enrollEnd != null && startDate != null && !startDate.isAfter(enrollEnd)) {
            throw new BusinessException("竞赛开始日期须晚于报名截止日期（截止日含当日 24:00，竞赛最早从次日开始）");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("竞赛结束日期不能早于竞赛开始日期（可为同一天）");
        }
    }

    private void validateResponsibleTeacher(String advisor) {
        if (!StringUtils.hasText(advisor)) {
            throw new BusinessException("负责老师不能为空");
        }
        String t = advisor.trim();
        if (t.length() > 500) {
            throw new BusinessException("负责老师信息过长");
        }
        if (t.matches("\\d+(\\s*,\\s*\\d+)*")) {
            List<Long> ids = new ArrayList<>();
            for (String part : t.split("\\s*,\\s*")) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ex) {
                    throw new BusinessException("负责老师数据格式无效");
                }
            }
            ids = ids.stream().distinct().collect(Collectors.toList());
            if (ids.isEmpty()) {
                throw new BusinessException("请至少选择一位负责老师");
            }
            List<User> users = userMapper.selectBatchIds(ids);
            if (users.size() != ids.size()) {
                throw new BusinessException("存在无效的负责老师账号");
            }
            for (User u : users) {
                String role = u.getRole();
                if (!"teacher".equals(role) && !"admin".equals(role)) {
                    throw new BusinessException("负责老师须为教师或管理员账号");
                }
            }
        }
    }

    private void fillAdvisorDisplay(Competition comp) {
        if (comp == null || !StringUtils.hasText(comp.getAdvisor())) {
            comp.setAdvisorDisplay(null);
            return;
        }
        String raw = comp.getAdvisor().trim();
        if (raw.matches("\\d+(\\s*,\\s*\\d+)*")) {
            List<Long> ids = new ArrayList<>();
            for (String part : raw.split("\\s*,\\s*")) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ex) {
                    comp.setAdvisorDisplay(raw);
                    return;
                }
            }
            ids = ids.stream().distinct().collect(Collectors.toList());
            if (ids.isEmpty()) {
                comp.setAdvisorDisplay(null);
                return;
            }
            List<User> users = userMapper.selectBatchIds(ids);
            Map<Long, User> map = users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            List<String> names = new ArrayList<>();
            for (Long id : ids) {
                User u = map.get(id);
                names.add(u != null ? u.getName() : "用户#" + id);
            }
            comp.setAdvisorDisplay(String.join("、", names));
            return;
        }
        comp.setAdvisorDisplay(raw);
    }

    private void validateUpdateTimeline(CompetitionRequest req) {
        LocalDate enrollStart = req.getEnrollStart();
        LocalDate enrollEnd = req.getEnrollEnd();
        LocalDate startDate = req.getStartDate();
        LocalDate endDate = req.getEndDate();

        if (enrollStart != null && enrollEnd != null && enrollEnd.isBefore(enrollStart)) {
            throw new BusinessException("报名截止日期不能早于报名开始日期（可为同一天，均为当日 0:00～24:00）");
        }
        if (enrollEnd != null && startDate != null && !startDate.isAfter(enrollEnd)) {
            throw new BusinessException("竞赛开始日期须晚于报名截止日期（截止日含当日 24:00，竞赛最早从次日开始）");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("竞赛结束日期不能早于竞赛开始日期（可为同一天）");
        }
    }

    @Data
    static class CompetitionRequest {
        @NotBlank(message = "竞赛名称不能为空")
        private String name;
        private String description;
        private String category;
        private String level;
        private LocalDate enrollStart;
        private LocalDate enrollEnd;
        private LocalDate startDate;
        private LocalDate endDate;
        private String requirements;
        private String awards;
        private Integer maxParticipants;
        private Integer teamMin;
        private Integer teamMax;
        private String advisor;
        private String contact;
        /** 开启后，报名队长可在竞赛详情/列表上传项目材料 */
        private Boolean projectUploadEnabled;
    }
}
