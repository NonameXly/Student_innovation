package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.BusinessException;
import com.innovation.common.R;
import com.innovation.entity.Competition;
import com.innovation.entity.InnovationProject;
import com.innovation.entity.Score;
import com.innovation.entity.Team;
import com.innovation.entity.TeamMember;
import com.innovation.entity.User;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.mapper.InnovationProjectMapper;
import com.innovation.mapper.ScoreMapper;
import com.innovation.mapper.TeamMapper;
import com.innovation.mapper.TeamMemberMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.innovation.service.UserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final CompetitionMapper competitionMapper;
    private final ScoreMapper scoreMapper;
    private final InnovationProjectMapper innovationProjectMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtil securityUtil;
    @Autowired
    private UserService userService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] PROFILE_SCORE_HEADERS = {"序号", "竞赛", "竞赛类别", "总分", "奖项"};
    private static final String[] PROFILE_PROJECT_HEADERS = {
            "序号", "项目名称", "关联竞赛", "项目状态", "负责教师", "项目成绩", "奖项", "教师评价", "创建时间", "备注"
    };
    private static final Map<String, String> AWARD_NAMES = Map.of(
            "first", "一等奖", "second", "二等奖", "third", "三等奖", "excellent", "优秀奖");
    private static final Map<String, String> PROJECT_STATUS_NAMES = Map.of(
            "ongoing", "进行中", "completed", "已结束");

    private static final Map<String, String> AWARD_LABELS = Map.of(
            "first", "一等奖", "second", "二等奖", "third", "三等奖", "excellent", "优秀奖");
    private static final Map<String, String> AWARD_TAG_TYPES = Map.of(
            "first", "danger", "second", "warning", "third", "", "excellent", "info");

    // ========== 用户管理（管理员） ==========

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> list(@RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String role,
                     @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size) {
        var wrapper = new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getName, keyword)
                        .or().like(User::getUsername, keyword)
                        .or().like(User::getEmail, keyword))
                .eq(StringUtils.hasText(role), User::getRole, role)
                .eq("active".equals(status), User::getActive, true)
                .eq("disabled".equals(status), User::getActive, false)
                .orderByDesc(User::getCreatedAt);
        var pageResult = userMapper.selectPage(new Page<>(page, size), wrapper);

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (User u : pageResult.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("username", u.getUsername());
            map.put("email", u.getEmail());
            map.put("college", u.getCollege());
            map.put("studentId", u.getStudentId());
            map.put("role", u.getRole());
            map.put("active", u.getActive());
            map.put("lastLogin", u.getLastLogin() != null ? u.getLastLogin().format(FMT) : null);
            resultList.add(map);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", resultList));
    }

    /**
     * 竞赛「负责老师」多选：分页列出启用中的教师与管理员（教师/管理员可操作）。
     */
    @GetMapping("/staff-for-advisor")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> staffForAdvisor(@RequestParam(required = false) String keyword,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer size) {
        int sz = size != null ? size : 20;
        if (sz > 100) {
            sz = 100;
        }
        int pg = page != null ? page : 1;
        var wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getActive, true)
                .in(User::getRole, Arrays.asList("teacher", "admin"))
                .and(StringUtils.hasText(keyword), w -> {
                    String kw = keyword.trim();
                    w.like(User::getName, kw)
                            .or().like(User::getUsername, kw)
                            .or().like(User::getCollege, kw);
                })
                .orderByAsc(User::getName);
        var pageResult = userMapper.selectPage(new Page<>(pg, sz), wrapper);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (User u : pageResult.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("username", u.getUsername());
            map.put("college", u.getCollege());
            map.put("role", u.getRole());
            resultList.add(map);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", resultList));
    }

    /**
     * 按 ID 回填负责老师（编辑竞赛表单）；仅返回教师/管理员。
     */
    @GetMapping("/staff-by-ids")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> staffByIds(@RequestParam String ids) {
        if (!StringUtils.hasText(ids)) {
            return R.ok(Collections.emptyList());
        }
        List<Long> idList = new ArrayList<>();
        for (String p : ids.split(",")) {
            String s = p.trim();
            if (!StringUtils.hasText(s)) {
                continue;
            }
            try {
                idList.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // skip invalid segment
            }
        }
        idList = idList.stream().distinct().collect(Collectors.toList());
        if (idList.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        List<User> users = userMapper.selectBatchIds(idList);
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users) {
            if (!"teacher".equals(u.getRole()) && !"admin".equals(u.getRole())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("username", u.getUsername());
            m.put("college", u.getCollege());
            m.put("role", u.getRole());
            out.add(m);
        }
        return R.ok(out);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> statistics() {
        long total = userMapper.selectCount(null);
        long students = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "student"));
        long teachers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "teacher"));
        long admins = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "admin"));
        return R.ok(Map.of("totalUsers", total, "students", students, "teachers", teachers, "admins", admins));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> create(@Valid @RequestBody UserRequest req) {
        User user = new User();
        user.setName(req.getName());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setRole(req.getRole());
        user.setCollege(req.getCollege());
        if ("student".equals(req.getRole())) {
            if (!StringUtils.hasText(req.getStudentId())) {
                throw new BusinessException("学生用户须填写学号");
            }
            user.setStudentId(req.getStudentId().trim());
        } else {
            user.setStudentId(null);
        }
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setActive(true);
        userService.createUser(user);
        return R.ok("用户创建成功", Map.of("id", user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> update(@PathVariable Long id, @RequestBody UserRequest req) {
        if (userMapper.selectById(id) == null) throw new BusinessException("用户不存在");
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>().eq(User::getId, id);
        uw.set(User::getName, req.getName());
        uw.set(User::getEmail, req.getEmail());
        uw.set(User::getRole, req.getRole());
        uw.set(User::getCollege, req.getCollege());
        if ("student".equals(req.getRole())) {
            if (!StringUtils.hasText(req.getStudentId())) {
                throw new BusinessException("学生用户须填写学号");
            }
            uw.set(User::getStudentId, req.getStudentId().trim());
        } else {
            uw.set(User::getStudentId, null);
        }
        if (StringUtils.hasText(req.getPassword())) {
            uw.set(User::getPassword, passwordEncoder.encode(req.getPassword()));
        }
        userMapper.update(null, uw);
        return R.ok("用户更新成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> delete(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        userMapper.deleteById(id);
        return R.ok("已删除用户：" + user.getName(), null);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setActive(body.getOrDefault("active", true));
        userMapper.updateById(user);
        return R.ok(user.getName() + "已" + (user.getActive() ? "启用" : "禁用"), null);
    }

    // ========== 个人中心 ==========

    @GetMapping("/profile")
    public R<?> profile() {
        Long userId = securityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", user.getName());
        result.put("role", user.getRole());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhone());
        result.put("college", user.getCollege());
        result.put("studentId", user.getStudentId());
        LocalDate jd = user.getJoinDate();
        result.put("joinDate", jd != null ? jd.format(DATE_FMT) : null);
        if ("student".equalsIgnoreCase(user.getRole())) {
            appendStudentCompetitionProfile(result, userId);
        } else {
            result.put("stats", emptyParticipationStats());
            result.put("history", Collections.emptyList());
        }
        return R.ok(result);
    }

    /** 学生导出本人已公示成绩与创新项目（Excel，含两个工作表） */
    @GetMapping("/profile/export")
    @PreAuthorize("hasRole('STUDENT')")
    public void exportStudentProfile(HttpServletResponse response) throws IOException {
        Long userId = securityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"student".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException("仅学生可导出个人成果");
        }
        String studentId = user.getStudentId();
        if (!StringUtils.hasText(studentId)) {
            throw new BusinessException("请先完善学号后再导出");
        }

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getUserId, userId)
                .eq(Score::getShow, 1)
                .orderByDesc(Score::getTotalScore));
        List<InnovationProject> projects = innovationProjectMapper.selectList(
                new LambdaQueryWrapper<InnovationProject>()
                        .eq(InnovationProject::getLeaderStudentId, studentId.trim())
                        .orderByDesc(InnovationProject::getCreatedAt));

        Set<Long> competitionIds = new HashSet<>();
        scores.forEach(s -> {
            if (s.getCompetitionId() != null) {
                competitionIds.add(s.getCompetitionId());
            }
        });
        projects.forEach(p -> {
            if (p.getCompetitionId() != null) {
                competitionIds.add(p.getCompetitionId());
            }
        });
        Map<Long, Competition> competitionById = competitionIds.isEmpty()
                ? Collections.emptyMap()
                : competitionMapper.selectBatchIds(competitionIds).stream()
                .collect(Collectors.toMap(Competition::getId, Function.identity(), (a, b) -> a));

        Set<Long> teacherIds = projects.stream()
                .map(InnovationProject::getAdvisorTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> teacherById = teacherIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<Long> projectIds = projects.stream().map(InnovationProject::getId).collect(Collectors.toList());
        Map<Long, Score> scoreByProjectId = projectIds.isEmpty()
                ? Collections.emptyMap()
                : scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .in(Score::getInnovationProjectId, projectIds)
                        .eq(Score::getShow, 1))
                .stream()
                .filter(s -> s.getInnovationProjectId() != null)
                .collect(Collectors.toMap(Score::getInnovationProjectId, Function.identity(), (a, b) -> a));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet scoreSheet = wb.createSheet("成绩记录");
            Row scoreHeader = scoreSheet.createRow(0);
            for (int i = 0; i < PROFILE_SCORE_HEADERS.length; i++) {
                scoreHeader.createCell(i).setCellValue(PROFILE_SCORE_HEADERS[i]);
            }
            int scoreRowIdx = 1;
            for (Score s : scores) {
                Row row = scoreSheet.createRow(scoreRowIdx);
                Competition comp = s.getCompetitionId() != null ? competitionById.get(s.getCompetitionId()) : null;
                row.createCell(0).setCellValue(scoreRowIdx);
                row.createCell(1).setCellValue(comp != null && comp.getName() != null ? comp.getName() : "");
                row.createCell(2).setCellValue(comp != null && comp.getCategory() != null ? comp.getCategory() : "");
                row.createCell(3).setCellValue(s.getTotalScore() != null ? s.getTotalScore().doubleValue() : 0);
                row.createCell(4).setCellValue(s.getAward() != null ? AWARD_NAMES.getOrDefault(s.getAward(), "") : "");
                scoreRowIdx++;
            }

            Sheet projectSheet = wb.createSheet("项目记录");
            Row projectHeader = projectSheet.createRow(0);
            for (int i = 0; i < PROFILE_PROJECT_HEADERS.length; i++) {
                projectHeader.createCell(i).setCellValue(PROFILE_PROJECT_HEADERS[i]);
            }
            int projectRowIdx = 1;
            for (InnovationProject p : projects) {
                Row row = projectSheet.createRow(projectRowIdx);
                Competition comp = p.getCompetitionId() != null ? competitionById.get(p.getCompetitionId()) : null;
                User teacher = p.getAdvisorTeacherId() != null ? teacherById.get(p.getAdvisorTeacherId()) : null;
                Score linkedScore = scoreByProjectId.get(p.getId());
                row.createCell(0).setCellValue(projectRowIdx);
                row.createCell(1).setCellValue(p.getName() != null ? p.getName() : "");
                row.createCell(2).setCellValue(comp != null && comp.getName() != null ? comp.getName() : "");
                row.createCell(3).setCellValue(
                        p.getStatus() != null ? PROJECT_STATUS_NAMES.getOrDefault(p.getStatus(), p.getStatus()) : "");
                row.createCell(4).setCellValue(teacher != null && teacher.getName() != null ? teacher.getName() : "");
                if (linkedScore != null && linkedScore.getTotalScore() != null) {
                    row.createCell(5).setCellValue(linkedScore.getTotalScore().doubleValue());
                } else {
                    row.createCell(5).setCellValue("");
                }
                row.createCell(6).setCellValue(
                        linkedScore != null && linkedScore.getAward() != null
                                ? AWARD_NAMES.getOrDefault(linkedScore.getAward(), "") : "");
                row.createCell(7).setCellValue(
                        p.getTeacherEvaluation() != null ? p.getTeacherEvaluation() : "");
                row.createCell(8).setCellValue(
                        p.getCreatedAt() != null ? p.getCreatedAt().format(DATETIME_FMT) : "");
                row.createCell(9).setCellValue(p.getRemark() != null ? p.getRemark() : "");
                projectRowIdx++;
            }

            String safeName = (user.getName() != null ? user.getName() : "学生")
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            String filename = "科创成果-" + safeName + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
            wb.write(response.getOutputStream());
        }
    }

    /** 学生参赛统计与记录：基于队伍成员关系 + 已公示成绩 */
    private void appendStudentCompetitionProfile(Map<String, Object> result, Long userId) {
        List<TeamMember> memberships = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) {
            result.put("stats", emptyParticipationStats());
            result.put("history", Collections.emptyList());
            return;
        }

        Map<Long, TeamMember> memberByTeamId = memberships.stream()
                .collect(Collectors.toMap(TeamMember::getTeamId, Function.identity(), (a, b) -> a));
        Set<Long> teamIds = memberByTeamId.keySet();
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>().in(Team::getId, teamIds));
        if (teams.isEmpty()) {
            result.put("stats", emptyParticipationStats());
            result.put("history", Collections.emptyList());
            return;
        }

        Map<Long, CompParticipation> byCompetition = new LinkedHashMap<>();
        for (Team team : teams) {
            if (team.getCompetitionId() == null) {
                continue;
            }
            TeamMember tm = memberByTeamId.get(team.getId());
            LocalDateTime joinAt = tm != null && tm.getJoinTime() != null ? tm.getJoinTime() : team.getCreateTime();
            if (joinAt == null) {
                joinAt = LocalDateTime.now();
            }
            CompParticipation row = byCompetition.get(team.getCompetitionId());
            if (row == null || joinAt.isAfter(row.joinAt)) {
                byCompetition.put(team.getCompetitionId(),
                        new CompParticipation(team.getCompetitionId(), team.getStatus(), joinAt));
            }
        }

        List<Score> publishedScores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getUserId, userId)
                .eq(Score::getShow, 1));
        Map<Long, Score> bestScoreByComp = new HashMap<>();
        for (Score sc : publishedScores) {
            if (sc.getCompetitionId() == null || sc.getTotalScore() == null) {
                continue;
            }
            Score prev = bestScoreByComp.get(sc.getCompetitionId());
            if (prev == null || sc.getTotalScore().compareTo(prev.getTotalScore()) > 0) {
                bestScoreByComp.put(sc.getCompetitionId(), sc);
            }
        }

        int awardCount = 0;
        BigDecimal maxScore = null;
        BigDecimal sum = BigDecimal.ZERO;
        int scoreCnt = 0;
        for (Score sc : publishedScores) {
            if (sc.getTotalScore() == null) {
                continue;
            }
            if (StringUtils.hasText(sc.getAward())) {
                awardCount++;
            }
            sum = sum.add(sc.getTotalScore());
            scoreCnt++;
            if (maxScore == null || sc.getTotalScore().compareTo(maxScore) > 0) {
                maxScore = sc.getTotalScore();
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("participations", byCompetition.size());
        stats.put("awards", awardCount);
        stats.put("maxScore", maxScore != null ? maxScore.setScale(1, RoundingMode.HALF_UP) : 0);
        stats.put("avgScore", scoreCnt > 0
                ? sum.divide(BigDecimal.valueOf(scoreCnt), 1, RoundingMode.HALF_UP) : 0);
        result.put("stats", stats);

        Set<Long> compIds = byCompetition.keySet();
        Map<Long, Competition> compMap = compIds.isEmpty() ? Collections.emptyMap()
                : competitionMapper.selectBatchIds(compIds).stream()
                .collect(Collectors.toMap(Competition::getId, Function.identity(), (a, b) -> a));

        List<CompParticipation> sorted = new ArrayList<>(byCompetition.values());
        sorted.sort(Comparator.comparing(CompParticipation::getJoinAt).reversed());

        List<Map<String, Object>> history = new ArrayList<>();
        for (CompParticipation p : sorted) {
            Competition comp = compMap.get(p.competitionId);
            Score sc = bestScoreByComp.get(p.competitionId);
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("competition", comp != null && StringUtils.hasText(comp.getName()) ? comp.getName() : "竞赛");
            h.put("date", p.joinAt.format(DATE_FMT));
            if (sc != null) {
                h.put("score", sc.getTotalScore().setScale(1, RoundingMode.HALF_UP));
                if (StringUtils.hasText(sc.getAward())) {
                    String code = sc.getAward().trim();
                    h.put("award", AWARD_LABELS.getOrDefault(code, code));
                    h.put("awardType", AWARD_TAG_TYPES.getOrDefault(code, "info"));
                    h.put("status", "awarded");
                    h.put("statusText", AWARD_LABELS.getOrDefault(code, code));
                } else {
                    h.put("status", "participated");
                    h.put("statusText", "已有成绩");
                }
            } else {
                h.put("status", registrationTimelineStatus(p.teamStatus));
                h.put("statusText", registrationStatusText(p.teamStatus));
            }
            history.add(h);
        }
        result.put("history", history);
    }

    private static Map<String, Object> emptyParticipationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("participations", 0);
        stats.put("awards", 0);
        stats.put("maxScore", 0);
        stats.put("avgScore", 0);
        return stats;
    }

    private static String registrationTimelineStatus(String teamStatus) {
        if ("approved".equals(teamStatus)) {
            return "participated";
        }
        if ("rejected".equals(teamStatus)) {
            return "pending";
        }
        return "pending";
    }

    private static String registrationStatusText(String teamStatus) {
        if ("approved".equals(teamStatus)) {
            return "报名已通过";
        }
        if ("rejected".equals(teamStatus)) {
            return "报名未通过";
        }
        return "待审核";
    }

    private static class CompParticipation {
        final Long competitionId;
        final String teamStatus;
        final LocalDateTime joinAt;

        CompParticipation(Long competitionId, String teamStatus, LocalDateTime joinAt) {
            this.competitionId = competitionId;
            this.teamStatus = teamStatus;
            this.joinAt = joinAt;
        }

        LocalDateTime getJoinAt() {
            return joinAt;
        }
    }

    @PutMapping("/profile")
    public R<?> updateProfile(@RequestBody Map<String, String> body) {
        Long userId = securityUtil.getCurrentUserId();
        User self = userMapper.selectById(userId);
        if (self == null) throw new BusinessException("用户不存在");

        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (body.containsKey("name")) {
            uw.set(User::getName, body.get("name"));
        }
        if (body.containsKey("email")) {
            uw.set(User::getEmail, body.get("email"));
        }
        if (body.containsKey("phone")) {
            uw.set(User::getPhone, body.get("phone"));
        }
        if (body.containsKey("college")) {
            uw.set(User::getCollege, body.get("college"));
        }
        // 管理员不在个人中心维护学号（由「用户管理」维护学生账号学号）
        if (!"admin".equals(self.getRole())) {
            String rawSid = body.get("studentId");
            if (!StringUtils.hasText(rawSid)) {
                throw new BusinessException("学号不能为空");
            }
            uw.set(User::getStudentId, rawSid.trim());
        }
        userMapper.update(null, uw);
        return R.ok("个人资料已更新", null);
    }

    @PutMapping("/password")
    public R<?> changePassword(@RequestBody Map<String, String> body) {
        Long userId = securityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException("密码不能为空");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return R.ok("密码修改成功", null);
    }



    @Data
    static class UserRequest {
        @NotBlank(message = "姓名不能为空")
        private String name;
        @NotBlank(message = "用户名不能为空")
        private String username;
        private String email;
        private String role = "student";
        private String college;
        /** 角色为学生时必填，由接口逻辑校验 */
        private String studentId;
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, message = "密码至少 6 位")
        private String password;
    }
}
