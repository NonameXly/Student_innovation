package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.common.BusinessException;
import com.innovation.common.R;
import com.innovation.entity.Competition;
import com.innovation.entity.InnovationProject;
import com.innovation.entity.Registration;
import com.innovation.entity.Team;
import com.innovation.entity.TeamMember;
import com.innovation.entity.User;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.mapper.InnovationProjectMapper;
import com.innovation.mapper.RegistrationMapper;
import com.innovation.mapper.TeamMapper;
import com.innovation.mapper.TeamMemberMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import com.innovation.service.ProjectMaterialService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/apply")
@RequiredArgsConstructor
public class ApplyController {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final RegistrationMapper registrationMapper;
    private final CompetitionMapper competitionMapper;
    private final UserMapper userMapper;
    private final InnovationProjectMapper innovationProjectMapper;
    private final SecurityUtil securityUtil;
    private final ProjectMaterialService projectMaterialService;

    @PostMapping("/enroll")
    @Transactional(rollbackFor = Exception.class)
    public R<?> enroll(@Valid @RequestBody EnrollRequest req) {
        Long captainUserId = securityUtil.getCurrentUserId();
        if (captainUserId == null) throw new BusinessException("请先登录");

        Competition competition = competitionMapper.selectById(req.getCompetitionId());
        if (competition == null) throw new BusinessException("竞赛不存在");
        if (Boolean.TRUE.equals(competition.getIsDraft())) throw new BusinessException("竞赛不存在");
        if (!"enrolling".equals(competition.getStatus())) throw new BusinessException("当前不在报名阶段");

        // 队长不能对同一个竞赛重复建队
        long captainTeamCount = teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                .eq(Team::getCompetitionId, req.getCompetitionId())
                .eq(Team::getCaptainUserId, captainUserId));
        if (captainTeamCount > 0) throw new BusinessException("您已报名该竞赛");

        List<MemberRequest> submittedMembers = Optional.ofNullable(req.getMembers())
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull)
                .filter(m -> StringUtils.hasText(m.getName()) || StringUtils.hasText(m.getUsername()))
                .collect(Collectors.toList());

        Integer teamMax = competition.getTeamMax() == null || competition.getTeamMax() <= 0 ? 1 : competition.getTeamMax();
        if (submittedMembers.size() + 1 > teamMax) {
            throw new BusinessException("队伍人数超过该竞赛上限");
        }

        // 校验队友账号、去重，避免同人重复加入
        Set<String> usernameSet = new HashSet<>();
        List<User> teammateUsers = new ArrayList<>();
        for (MemberRequest member : submittedMembers) {
            String username = member.getUsername() == null ? "" : member.getUsername().trim();
            if (!StringUtils.hasText(username)) {
                throw new BusinessException("队友账号不能为空");
            }
            if (!usernameSet.add(username)) {
                throw new BusinessException("队友账号存在重复");
            }
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null) throw new BusinessException("队友账号不存在: " + username);
            if (Objects.equals(user.getId(), captainUserId)) {
                throw new BusinessException("队友列表中不能包含本人");
            }
            teammateUsers.add(user);
        }

        // 校验队友是否已在当前竞赛加入其他队伍
        List<Team> competitionTeams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getCompetitionId, req.getCompetitionId()));
        if (!competitionTeams.isEmpty() && !teammateUsers.isEmpty()) {
            List<Long> teamIds = competitionTeams.stream().map(Team::getId).collect(Collectors.toList());
            Set<Long> teammateUserIds = teammateUsers.stream().map(User::getId).collect(Collectors.toSet());
            long joinedCount = teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                    .in(TeamMember::getTeamId, teamIds)
                    .in(TeamMember::getUserId, teammateUserIds));
            if (joinedCount > 0) throw new BusinessException("存在队友已加入该竞赛其他队伍");
        }

        Team team = new Team();
        team.setCompetitionId(req.getCompetitionId());
        team.setTeamName(StringUtils.hasText(req.getTeamName()) ? req.getTeamName().trim() : defaultTeamName(captainUserId));
        team.setCaptainUserId(captainUserId);
        team.setCreateTime(LocalDateTime.now());
        team.setStatus("pending");
        team.setProjectSubmitLocked(Boolean.FALSE);
        teamMapper.insert(team);

        TeamMember captainMember = new TeamMember();
        captainMember.setTeamId(team.getId());
        captainMember.setUserId(captainUserId);
        captainMember.setRole("captain");
        captainMember.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(captainMember);

        for (User teammate : teammateUsers) {
            TeamMember member = new TeamMember();
            member.setTeamId(team.getId());
            member.setUserId(teammate.getId());
            member.setRole("member");
            member.setJoinTime(LocalDateTime.now());
            teamMemberMapper.insert(member);
        }

        // 复用 registration 审核流：写入待审核报名记录，保证报名管理可见
        Registration reg = new Registration();
        reg.setUserId(captainUserId);
        reg.setCompetitionId(req.getCompetitionId());
        reg.setTeamName(team.getTeamName());
        reg.setStatus("pending");
        reg.setEnrollTime(LocalDateTime.now());
        registrationMapper.insert(reg);

        return R.ok("报名成功", Map.of("teamId", team.getId()));
    }

    @GetMapping("/my-registration")
    public R<?> myRegistration(@RequestParam Long competitionId) {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        Registration reg = findLatestRegistration(uid, competitionId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (reg == null) {
            m.put("exists", false);
            m.put("owner", false);
            return R.ok(m);
        }
        m.put("exists", true);
        m.put("id", reg.getId());
        m.put("status", reg.getStatus());
        Team captainTeam = findCaptainTeam(uid, competitionId, reg.getTeamName());
        m.put("projectFile", captainTeam != null ? captainTeam.getProjectFile() : null);
        m.put("projectSubmitLocked", captainTeam != null && Boolean.TRUE.equals(captainTeam.getProjectSubmitLocked()));
        m.put("owner", uid.equals(reg.getUserId()));
        return R.ok(m);
    }

    /**
     * 当前学生在本场竞赛下所属队伍关联的创新项目档案中，已填写的教师评价（队长/队员均可查）。
     */
    @GetMapping("/my-teacher-evaluations")
    @PreAuthorize("hasRole('STUDENT')")
    public R<?> myTeacherEvaluations(@RequestParam Long competitionId) {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        if (competitionId == null) {
            throw new BusinessException("竞赛ID不能为空");
        }
        List<TeamMember> tms = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, uid));
        if (tms.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        Set<Long> memberTeamIds = tms.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
        List<Team> teamsInComp = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getCompetitionId, competitionId)
                .in(Team::getId, memberTeamIds));
        if (teamsInComp.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        List<Long> allowedTeamIds = teamsInComp.stream().map(Team::getId).collect(Collectors.toList());
        List<InnovationProject> projects = innovationProjectMapper.selectList(new LambdaQueryWrapper<InnovationProject>()
                .eq(InnovationProject::getCompetitionId, competitionId)
                .in(InnovationProject::getTeamId, allowedTeamIds)
                .orderByDesc(InnovationProject::getCreatedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (InnovationProject p : projects) {
            if (!StringUtils.hasText(p.getTeacherEvaluation())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("teacherEvaluation", p.getTeacherEvaluation());
            m.put("teacherEvaluationAt", p.getTeacherEvaluationAt());
            m.put("createdAt", p.getCreatedAt());
            out.add(m);
        }
        return R.ok(out);
    }

    @GetMapping("/project-upload-status")
    public R<?> projectUploadStatus(@RequestParam String competitionIds) {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        if (!StringUtils.hasText(competitionIds)) {
            return R.ok(Collections.emptyMap());
        }
        String[] parts = competitionIds.split(",");
        if (parts.length > 40) {
            throw new BusinessException("一次最多查询40场竞赛");
        }
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            long cid;
            try {
                cid = Long.parseLong(part.trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            Competition c = competitionMapper.selectById(cid);
            boolean enabled = c != null && Boolean.TRUE.equals(c.getProjectUploadEnabled());
            Registration reg = findLatestRegistration(uid, cid);
            Team captainTeam = reg == null ? null : findCaptainTeam(uid, cid, reg.getTeamName());
            boolean canUpload = enabled && c != null && "ongoing".equals(c.getStatus())
                    && reg != null && uid.equals(reg.getUserId()) && "approved".equals(reg.getStatus())
                    && captainTeam != null && !Boolean.TRUE.equals(captainTeam.getProjectSubmitLocked());
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("canUpload", canUpload);
            one.put("hasFile", captainTeam != null && StringUtils.hasText(captainTeam.getProjectFile()));
            one.put("submitLocked", captainTeam != null && Boolean.TRUE.equals(captainTeam.getProjectSubmitLocked()));
            out.put(String.valueOf(cid), one);
        }
        return R.ok(out);
    }

    @PostMapping("/project-upload")
    @Transactional(rollbackFor = Exception.class)
    public R<?> uploadProject(@RequestParam Long competitionId,
                              @RequestParam("projectName") String projectName,
                              @RequestParam(value = "projectDescription", required = false) String projectDescription,
                              @RequestParam("file") MultipartFile file) throws IOException {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        Competition comp = competitionMapper.selectById(competitionId);
        if (comp == null) {
            throw new BusinessException("竞赛不存在");
        }
        if (!Boolean.TRUE.equals(comp.getProjectUploadEnabled())) {
            throw new BusinessException("该竞赛未开放项目上传");
        }
        if ("finished".equals(comp.getStatus())) {
            throw new BusinessException("竞赛已结束，不可再上传项目");
        }
        if (!"ongoing".equals(comp.getStatus())) {
            throw new BusinessException("仅在竞赛进行中时可上传项目");
        }
        Registration reg = findLatestRegistration(uid, competitionId);
        if (reg == null) {
            throw new BusinessException("请先报名该竞赛");
        }
        if (!uid.equals(reg.getUserId())) {
            throw new BusinessException("仅报名队长可上传项目材料");
        }
        if ("rejected".equals(reg.getStatus())) {
            throw new BusinessException("报名未通过，无法上传项目");
        }
        if (!"approved".equals(reg.getStatus())) {
            throw new BusinessException("报名审核通过后方可上传项目");
        }
        Team team = findCaptainTeam(uid, competitionId, reg.getTeamName());
        if (team == null) {
            throw new BusinessException("未找到队伍记录，请重新报名或联系管理员");
        }
        if (Boolean.TRUE.equals(team.getProjectSubmitLocked())) {
            throw new BusinessException("管理员已为队伍评定成绩，不可再提交新的项目材料");
        }
        String relative = projectMaterialService.storeProjectFile(competitionId, file);
        // 每次上传新增一条 innovation_project，历史附件保留；team.projectFile 仅指向最近一次，供学生端「已上传/下载」用
        team.setProjectFile(relative);
        teamMapper.updateById(team);
        String nameTrim = projectName == null ? "" : projectName.trim();
        if (!StringUtils.hasText(nameTrim)) {
            throw new BusinessException("请输入项目名称");
        }
        if (nameTrim.length() > 200) {
            throw new BusinessException("项目名称不超过200字");
        }
        String descTrim = projectDescription == null ? null : projectDescription.trim();
        if (descTrim != null && descTrim.length() > 2000) {
            throw new BusinessException("项目描述不超过2000字");
        }
        if (descTrim != null && descTrim.isEmpty()) {
            descTrim = null;
        }
        insertInnovationProjectFromStudentUpload(team, uid, relative, nameTrim, descTrim);
        return R.ok("上传成功", Map.of("projectFile", relative));
    }

    /**
     * 学生端队长每次上传竞赛项目材料时，新增一条创新项目档案（同一队伍可多条，管理员列表逐条可见）。
     */
    private void insertInnovationProjectFromStudentUpload(Team team, Long captainUserId, String attachmentRelative,
                                                          String projectName, String projectDescription) {
        InnovationProject p = new InnovationProject();
        p.setName(projectName);
        p.setDescription(projectDescription);
        p.setCompetitionId(team.getCompetitionId());
        p.setTeamId(team.getId());
        p.setAttachmentPath(attachmentRelative);
        p.setStatus("ongoing");
        p.setCreatedBy(captainUserId);
        User captain = userMapper.selectById(captainUserId);
        if (captain != null) {
            p.setLeaderName(captain.getName());
            p.setLeaderStudentId(captain.getStudentId());
        }
        innovationProjectMapper.insert(p);
    }

    @GetMapping("/project-download")
    public void downloadProject(@RequestParam Long competitionId, HttpServletResponse response) throws IOException {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        Registration reg = findLatestRegistration(uid, competitionId);
        if (reg == null) {
            throw new BusinessException("暂无报名记录");
        }
        if (!uid.equals(reg.getUserId())) {
            throw new BusinessException("无权下载");
        }
        Team team = findCaptainTeam(uid, competitionId, reg.getTeamName());
        if (team == null) {
            throw new BusinessException("未找到队伍记录");
        }
        if (!StringUtils.hasText(team.getProjectFile())) {
            throw new BusinessException("暂无项目材料");
        }
        projectMaterialService.writeToResponse(team.getProjectFile(), response, null);
    }

    /** 与报名审核关联的队伍：队长 + 竞赛 + 队名（与 registration 一致） */
    private Team findCaptainTeam(Long captainUserId, Long competitionId, String teamName) {
        LambdaQueryWrapper<Team> q = new LambdaQueryWrapper<Team>()
                .eq(Team::getCompetitionId, competitionId)
                .eq(Team::getCaptainUserId, captainUserId);
        if (StringUtils.hasText(teamName)) {
            q.eq(Team::getTeamName, teamName);
        }
        q.orderByDesc(Team::getId).last("limit 1");
        return teamMapper.selectOne(q);
    }

    private Registration findLatestRegistration(Long userId, Long competitionId) {
        List<Registration> list = registrationMapper.selectList(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getCompetitionId, competitionId)
                .orderByDesc(Registration::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    @GetMapping("/list")
    public R<?> list(@RequestParam Long competition) {
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getCompetitionId, competition)
                .orderByDesc(Team::getCreateTime));
        if (teams.isEmpty()) {
            return R.ok(Map.of("total", 0, "list", Collections.emptyList()));
        }

        List<Long> teamIds = teams.stream().map(Team::getId).collect(Collectors.toList());
        Map<Long, Team> teamMap = teams.stream().collect(Collectors.toMap(Team::getId, t -> t));

        List<TeamMember> members = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, teamIds)
                .orderByDesc(TeamMember::getJoinTime));
        if (members.isEmpty()) {
            return R.ok(Map.of("total", 0, "list", Collections.emptyList()));
        }

        Set<Long> userIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (TeamMember member : members) {
            Team team = teamMap.get(member.getTeamId());
            User user = userMap.get(member.getUserId());
            if (team == null || user == null) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teamId", team.getId());
            row.put("teamName", team.getTeamName());
            row.put("studentName", user.getName());
            row.put("username", user.getUsername());
            row.put("college", user.getCollege());
            row.put("role", member.getRole());
            row.put("status", team.getStatus());
            row.put("enrollTime", team.getCreateTime() == null ? null : team.getCreateTime().format(TIME_FORMATTER));
            resultList.add(row);
        }

        return R.ok(Map.of("total", resultList.size(), "list", resultList));
    }

    private String defaultTeamName(Long captainUserId) {
        return "队伍-" + captainUserId + "-" + System.currentTimeMillis();
    }

    @Data
    static class EnrollRequest {
        @NotNull(message = "竞赛ID不能为空")
        private Long competitionId;
        private String teamName;
        private String captainName;
        private String captainUsername;
        private List<MemberRequest> members;
    }

    @Data
    static class MemberRequest {
        private String name;
        private String username;
    }
}
