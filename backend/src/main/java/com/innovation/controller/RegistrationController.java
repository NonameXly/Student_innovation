package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.innovation.common.BusinessException;
import com.innovation.common.OptimisticLockHelper;
import com.innovation.common.R;
import com.innovation.entity.*;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.mapper.RegistrationMapper;
import com.innovation.mapper.TeamMapper;
import com.innovation.mapper.TeamMemberMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationMapper registrationMapper;
    private final CompetitionMapper competitionMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final SecurityUtil securityUtil;

    @GetMapping("/list")
    public R<?> list(@RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long competition,
                     @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size) {
        // 使用 MyBatis Plus 分页
        var wrapper = new LambdaQueryWrapper<Registration>()
                .eq(StringUtils.hasText(status), Registration::getStatus, status)
                .eq(competition != null, Registration::getCompetitionId, competition)
                .orderByDesc(Registration::getEnrollTime);
        var pageResult = registrationMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Registration reg : pageResult.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", reg.getId());
            map.put("status", reg.getStatus());
            map.put("teamName", reg.getTeamName());
            map.put("enrollTime", reg.getEnrollTime() != null ?
                    reg.getEnrollTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);

            User user = userMapper.selectById(reg.getUserId());
            if (user != null) {
                map.put("studentName", user.getName());
                map.put("studentId", user.getStudentId());
                map.put("college", user.getCollege());
            }
            Competition comp = competitionMapper.selectById(reg.getCompetitionId());
            if (comp != null) {
                map.put("competition", comp.getName());
            }
            resultList.add(map);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", resultList));
    }

    @PostMapping("/enroll")
    public R<?> enroll(@Valid @RequestBody EnrollRequest req) {
        Long userId = securityUtil.getCurrentUserId();
        // 检查是否重复报名
        long count = registrationMapper.selectCount(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getCompetitionId, req.getCompetitionId())
                .ne(Registration::getStatus, "rejected"));
        if (count > 0) throw new BusinessException("您已报名该竞赛");

        Registration reg = new Registration();
        reg.setUserId(userId);
        reg.setCompetitionId(req.getCompetitionId());
        reg.setTeamName(req.getTeamName());
        reg.setStatus("pending");
        reg.setEnrollTime(LocalDateTime.now());
        registrationMapper.insert(reg);
        return R.ok("报名申请已提交，请等待审核", null);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional(rollbackFor = Exception.class)
    public R<?> approve(@PathVariable Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) throw new BusinessException("报名记录不存在");
        if (!"pending".equals(reg.getStatus())) {
            throw new BusinessException("该报名已审核，请刷新后重试");
        }
        reg.setStatus("approved");
        reg.setReviewedBy(securityUtil.getCurrentUserId());
        reg.setReviewedAt(LocalDateTime.now());
        OptimisticLockHelper.assertUpdated(registrationMapper.updateById(reg));
        syncTeamStatus(reg, "approved");

        // 更新竞赛参与人数
        competitionMapper.update(null, new LambdaUpdateWrapper<Competition>()
                .eq(Competition::getId, reg.getCompetitionId())
                .setSql("participants = participants + 1"));

        User user = userMapper.selectById(reg.getUserId());
        String name = user != null ? user.getName() : "";
        return R.ok("已通过" + name + "的报名", null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional(rollbackFor = Exception.class)
    public R<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) throw new BusinessException("报名记录不存在");
        if (!"pending".equals(reg.getStatus())) {
            throw new BusinessException("该报名已审核，请刷新后重试");
        }
        reg.setStatus("rejected");
        reg.setRejectReason(body.get("reason"));
        reg.setReviewedBy(securityUtil.getCurrentUserId());
        reg.setReviewedAt(LocalDateTime.now());
        OptimisticLockHelper.assertUpdated(registrationMapper.updateById(reg));
        syncTeamStatus(reg, "rejected");

        User user = userMapper.selectById(reg.getUserId());
        String name = user != null ? user.getName() : "";
        return R.ok("已驳回" + name + "的报名", null);
    }

    @PostMapping("/batch/approve")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional(rollbackFor = Exception.class)
    public R<?> batchApprove(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        if (rawIds == null || rawIds.isEmpty()) throw new BusinessException("请选择报名记录");
        Long reviewerId = securityUtil.getCurrentUserId();
        int approved = 0;
        for (Integer rawId : rawIds) {
            Long id = rawId.longValue();
            Registration reg = registrationMapper.selectById(id);
            if (reg != null && "pending".equals(reg.getStatus())) {
                reg.setStatus("approved");
                reg.setReviewedBy(reviewerId);
                reg.setReviewedAt(LocalDateTime.now());
                OptimisticLockHelper.assertUpdated(registrationMapper.updateById(reg));
                syncTeamStatus(reg, "approved");
                competitionMapper.update(null, new LambdaUpdateWrapper<Competition>()
                        .eq(Competition::getId, reg.getCompetitionId())
                        .setSql("participants = participants + 1"));
                approved++;
            }
        }
        return R.ok("已批量通过" + approved + "条报名", null);
    }

    @PostMapping("/batch/reject")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional(rollbackFor = Exception.class)
    public R<?> batchReject(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        String reason = (String) body.get("reason");
        if (rawIds == null || rawIds.isEmpty()) throw new BusinessException("请选择报名记录");
        Long reviewerId = securityUtil.getCurrentUserId();
        int rejected = 0;
        for (Integer rawId : rawIds) {
            Long id = rawId.longValue();
            Registration reg = registrationMapper.selectById(id);
            if (reg != null && "pending".equals(reg.getStatus())) {
                reg.setStatus("rejected");
                reg.setRejectReason(reason);
                reg.setReviewedBy(reviewerId);
                reg.setReviewedAt(LocalDateTime.now());
                OptimisticLockHelper.assertUpdated(registrationMapper.updateById(reg));
                syncTeamStatus(reg, "rejected");
                rejected++;
            }
        }
        return R.ok("已批量驳回" + rejected + "条报名", null);
    }

    @GetMapping("/{id}/team-detail")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> teamDetail(@PathVariable Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) throw new BusinessException("报名记录不存在");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", reg.getId());
        detail.put("status", reg.getStatus());
        detail.put("teamName", reg.getTeamName());
        detail.put("enrollTime", reg.getEnrollTime() != null ?
                reg.getEnrollTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
        detail.put("rejectReason", reg.getRejectReason());

        User captain = userMapper.selectById(reg.getUserId());
        if (captain != null) {
            detail.put("studentName", captain.getName());
            detail.put("studentId", captain.getStudentId());
            detail.put("college", captain.getCollege());
        }

        Competition comp = competitionMapper.selectById(reg.getCompetitionId());
        if (comp != null) {
            detail.put("competition", comp.getName());
        }

        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getCompetitionId, reg.getCompetitionId())
                .eq(Team::getCaptainUserId, reg.getUserId())
                .eq(StringUtils.hasText(reg.getTeamName()), Team::getTeamName, reg.getTeamName())
                .last("limit 1"));

        List<Map<String, Object>> memberList = new ArrayList<>();
        Set<Long> addedUserIds = new HashSet<>();

        if (captain != null) {
            Map<String, Object> captainMap = new LinkedHashMap<>();
            captainMap.put("name", captain.getName());
            captainMap.put("studentId", captain.getStudentId());
            captainMap.put("college", captain.getCollege());
            captainMap.put("role", "captain");
            memberList.add(captainMap);
            addedUserIds.add(captain.getId());
        }

        detail.put("projectFile", team != null ? team.getProjectFile() : null);
        if (team != null) {
            detail.put("teamId", team.getId());
            detail.put("teamStatus", team.getStatus());

            List<TeamMember> members = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, team.getId())
                    .orderByAsc(TeamMember::getId));

            for (TeamMember tm : members) {
                if (tm.getUserId() == null || addedUserIds.contains(tm.getUserId())) continue;
                User memberUser = userMapper.selectById(tm.getUserId());
                if (memberUser == null) continue;

                Map<String, Object> memberMap = new LinkedHashMap<>();
                memberMap.put("name", memberUser.getName());
                memberMap.put("studentId", memberUser.getStudentId());
                memberMap.put("college", memberUser.getCollege());
                memberMap.put("role", StringUtils.hasText(tm.getRole()) ? tm.getRole() : "member");
                memberList.add(memberMap);
                addedUserIds.add(tm.getUserId());
            }
        }

        detail.put("memberList", memberList);
        return R.ok(detail);
    }

    private void syncTeamStatus(Registration reg, String status) {
        if (reg == null) return;
        teamMapper.update(null, new LambdaUpdateWrapper<Team>()
                .eq(Team::getCompetitionId, reg.getCompetitionId())
                .eq(Team::getCaptainUserId, reg.getUserId())
                .eq(StringUtils.hasText(reg.getTeamName()), Team::getTeamName, reg.getTeamName())
                .set(Team::getStatus, status));
    }



    @Data
    static class EnrollRequest {
        @NotNull(message = "竞赛ID不能为空")
        private Long competitionId;
        private String teamName;
        private String captainName;
        private String captainStudentId;
        private List<Map<String, String>> members;
    }
}
