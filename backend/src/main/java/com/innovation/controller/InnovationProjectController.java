package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.BusinessException;
import com.innovation.common.OptimisticLockHelper;
import com.innovation.common.R;
import com.innovation.entity.Competition;
import com.innovation.entity.InnovationProject;
import com.innovation.entity.Score;
import com.innovation.entity.Team;
import com.innovation.entity.User;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.mapper.InnovationProjectMapper;
import com.innovation.mapper.ScoreMapper;
import com.innovation.mapper.TeamMapper;
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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 创新项目管理：管理员可见全部；教师可见本人为项目/竞赛负责老师的项目；
 * 学生仅可见 leader_student_id 为本人的项目，新建时须指定 advisor_teacher_id。
 * 竞赛队长上传材料不填 advisor_teacher_id，教师通过竞赛 advisor 查看。
 */
@RestController
@RequestMapping("/innovation-project")
@RequiredArgsConstructor
public class InnovationProjectController {

    /** 进行中 / 已结束（录入竞赛成绩后自动变为已结束） */
    private static final Set<String> STATUS_SET = Set.of("ongoing", "completed");

    private final InnovationProjectMapper innovationProjectMapper;
    private final CompetitionMapper competitionMapper;
    private final ScoreMapper scoreMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final SecurityUtil securityUtil;
    private final ProjectMaterialService projectMaterialService;

    private static final BigDecimal SCORE_MIN = BigDecimal.ZERO;
    private static final BigDecimal SCORE_MAX = new BigDecimal("999.9");

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public R<?> list(@RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long competition,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String timeOrder,
                     @RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size) {
        boolean asc = "asc".equalsIgnoreCase(timeOrder);
        var wrapper = new LambdaQueryWrapper<InnovationProject>()
                .eq(StringUtils.hasText(status), InnovationProject::getStatus, status)
                .eq(competition != null, InnovationProject::getCompetitionId, competition);
        if (asc) {
            wrapper.orderByAsc(InnovationProject::getCreatedAt);
        } else {
            wrapper.orderByDesc(InnovationProject::getCreatedAt);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(InnovationProject::getName, kw)
                    .or().like(InnovationProject::getLeaderName, kw)
                    .or().like(InnovationProject::getLeaderStudentId, kw));
        }
        if (isStudentRole()) {
            String sid = requireCurrentStudentId();
            wrapper.eq(InnovationProject::getLeaderStudentId, sid);
        } else if (!securityUtil.isAdmin()) {
            Long uid = securityUtil.getCurrentUserId();
            List<Long> allowedCompetitionIds = competitionIdsForCurrentAdvisorTeacher();
            if (uid == null) {
                return R.ok(Map.of("total", 0L, "list", Collections.emptyList()));
            }
            wrapper.and(w -> {
                w.eq(InnovationProject::getAdvisorTeacherId, uid);
                if (!allowedCompetitionIds.isEmpty()) {
                    w.or().in(InnovationProject::getCompetitionId, allowedCompetitionIds);
                }
            });
        }
        var pageResult = innovationProjectMapper.selectPage(new Page<>(page, size), wrapper);

        List<Long> projectIds = pageResult.getRecords().stream().map(InnovationProject::getId).collect(Collectors.toList());
        Map<Long, Score> scoreByProjectId = loadScoresByInnovationProjectIds(projectIds);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (InnovationProject p : pageResult.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("description", p.getDescription());
            m.put("competitionId", p.getCompetitionId());
            m.put("teamId", p.getTeamId());
            m.put("leaderName", p.getLeaderName());
            m.put("leaderStudentId", p.getLeaderStudentId());
            m.put("advisorTeacherId", p.getAdvisorTeacherId());
            m.put("status", p.getStatus());
            m.put("attachmentPath", p.getAttachmentPath());
            m.put("hasAttachment", StringUtils.hasText(p.getAttachmentPath()));
            m.put("remark", p.getRemark());
            m.put("teacherEvaluation", p.getTeacherEvaluation());
            m.put("teacherEvaluationAt", p.getTeacherEvaluationAt());
            m.put("createdAt", p.getCreatedAt());
            putScoreFields(m, scoreByProjectId.get(p.getId()));
            m.put("scoreBlockedByPeer", peerOtherInnovationProjectAlreadyScored(p.getTeamId(), p.getCompetitionId(), p.getId()));
            if (p.getCompetitionId() != null) {
                Competition c = competitionMapper.selectById(p.getCompetitionId());
                m.put("competitionName", c != null ? c.getName() : null);
            } else {
                m.put("competitionName", null);
            }
            if (p.getAdvisorTeacherId() != null) {
                User t = userMapper.selectById(p.getAdvisorTeacherId());
                m.put("advisorTeacherName", t != null ? t.getName() : null);
            } else {
                m.put("advisorTeacherName", null);
            }
            rows.add(m);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", rows));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public R<?> detail(@PathVariable Long id) {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("competitionId", p.getCompetitionId());
        m.put("teamId", p.getTeamId());
        m.put("leaderName", p.getLeaderName());
        m.put("leaderStudentId", p.getLeaderStudentId());
        m.put("advisorTeacherId", p.getAdvisorTeacherId());
        m.put("status", p.getStatus());
        m.put("attachmentPath", p.getAttachmentPath());
        m.put("remark", p.getRemark());
        m.put("teacherEvaluation", p.getTeacherEvaluation());
        m.put("teacherEvaluationAt", p.getTeacherEvaluationAt());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        if (p.getCompetitionId() != null) {
            Competition c = competitionMapper.selectById(p.getCompetitionId());
            m.put("competitionName", c != null ? c.getName() : null);
        } else {
            m.put("competitionName", null);
        }
        if (p.getAdvisorTeacherId() != null) {
            User t = userMapper.selectById(p.getAdvisorTeacherId());
            m.put("advisorTeacherName", t != null ? t.getName() : null);
        } else {
            m.put("advisorTeacherName", null);
        }
        Score linked = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                .eq(Score::getInnovationProjectId, id));
        putScoreFields(m, linked);
        return R.ok(m);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public R<?> create(@Valid @RequestBody ProjectRequest req) {
        if (isStudentRole()) {
            return createByStudent(req);
        }
        validateCompetitionForWrite(req.getCompetitionId());
        String st = normalizeStatus(req.getStatus());
        InnovationProject p = new InnovationProject();
        p.setName(req.getName().trim());
        p.setDescription(trimToNull(req.getDescription()));
        p.setCompetitionId(req.getCompetitionId());
        p.setLeaderName(trimToNull(req.getLeaderName()));
        p.setLeaderStudentId(trimToNull(req.getLeaderStudentId()));
        p.setAdvisorTeacherId(validateAdvisorTeacherId(req.getAdvisorTeacherId()));
        p.setStatus(st);
        p.setRemark(trimToNull(req.getRemark()));
        p.setCreatedBy(securityUtil.getCurrentUserId());
        innovationProjectMapper.insert(p);
        return R.ok("创建成功", Map.of("id", p.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        validateCompetitionForWrite(req.getCompetitionId());
        p.setName(req.getName().trim());
        p.setDescription(trimToNull(req.getDescription()));
        p.setCompetitionId(req.getCompetitionId());
        p.setLeaderName(trimToNull(req.getLeaderName()));
        p.setLeaderStudentId(trimToNull(req.getLeaderStudentId()));
        if (req.getAdvisorTeacherId() != null) {
            p.setAdvisorTeacherId(validateAdvisorTeacherId(req.getAdvisorTeacherId()));
        }
        p.setStatus(normalizeStatus(req.getStatus()));
        p.setRemark(trimToNull(req.getRemark()));
        innovationProjectMapper.updateById(p);
        return R.ok("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> delete(@PathVariable Long id) {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        deleteAttachmentFile(p.getAttachmentPath());
        scoreMapper.delete(new LambdaQueryWrapper<Score>().eq(Score::getInnovationProjectId, id));
        // 逻辑删除前清空 team_id，避免已删档案仍与队伍强关联（便于统计与再上传批次区分）
        innovationProjectMapper.update(null, new LambdaUpdateWrapper<InnovationProject>()
                .eq(InnovationProject::getId, id)
                .setSql("team_id = NULL"));
        innovationProjectMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    @PostMapping("/{id}/attachment")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public R<?> uploadAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        String relative = projectMaterialService.storeManagedProjectFile(id, file);
        deleteAttachmentFile(p.getAttachmentPath());
        p.setAttachmentPath(relative);
        innovationProjectMapper.updateById(p);
        return R.ok("附件已上传", Map.of("attachmentPath", relative));
    }

    @PutMapping("/{id}/teacher-evaluation")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional(rollbackFor = Exception.class)
    public R<?> saveTeacherEvaluation(@PathVariable Long id, @RequestBody TeacherEvaluationBody body) {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        String raw = body != null && body.getContent() != null ? body.getContent() : "";
        if (raw.length() > 5000) {
            throw new BusinessException("评价内容不超过5000字");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            p.setTeacherEvaluation(null);
            p.setTeacherEvaluationAt(null);
        } else {
            p.setTeacherEvaluation(trimmed);
            p.setTeacherEvaluationAt(LocalDateTime.now());
        }
        OptimisticLockHelper.assertUpdated(innovationProjectMapper.updateById(p));
        return R.ok("评价已保存", null);
    }

    /**
     * 管理员为该项目档案录入竞赛成绩：写入 score 表且默认不向学生公示（show=0）。
     * 与竞赛「展示成绩」批量公示配合使用。
     */
    @PutMapping("/{id}/score")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Transactional(rollbackFor = Exception.class)
    public R<?> saveProjectScore(@PathVariable Long id, @Valid @RequestBody ProjectScoreBody body) {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        if (p.getCompetitionId() == null) {
            throw new BusinessException("未关联竞赛的项目无法录入竞赛成绩");
        }
        Long captainUserId = resolveCaptainUserId(p);
        if (captainUserId == null) {
            throw new BusinessException("无法确定队长学生用户，请填写负责人学号或保留队伍关联后再打分");
        }
        BigDecimal total = body.getTotalScore();
        if (total == null) {
            throw new BusinessException("请输入成绩");
        }
        total = total.setScale(1, RoundingMode.HALF_UP);
        if (total.compareTo(SCORE_MIN) < 0 || total.compareTo(SCORE_MAX) > 0) {
            throw new BusinessException("成绩须在 0～999.9 之间");
        }

        assertTeamCompetitionScoreExclusive(p, id);

        Score existing = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                .eq(Score::getInnovationProjectId, id));
        if (existing != null) {
            throw new BusinessException("该项目已录入竞赛成绩，不可重复打分");
        }

        Long operatorId = securityUtil.getCurrentUserId();

        Score score = new Score();
        score.setUserId(captainUserId);
        score.setCompetitionId(p.getCompetitionId());
        score.setInnovationProjectId(id);
        score.setTotalScore(total);
        score.setAward(null);
        score.setRemark(null);
        score.setShow(0);
        score.setCreatedBy(operatorId);
        scoreMapper.insert(score);
        p.setStatus("completed");
        OptimisticLockHelper.assertUpdated(innovationProjectMapper.updateById(p));
        lockTeamProjectSubmitAfterScoreSaved(p);
        return R.ok("成绩已保存（未公示的成绩需在成绩管理或竞赛中公示后才对学生可见）", Map.of("scoreId", score.getId()));
    }

    @GetMapping("/{id}/attachment")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public void downloadAttachment(@PathVariable Long id, HttpServletResponse response) throws IOException {
        InnovationProject p = innovationProjectMapper.selectById(id);
        assertCanAccessInnovationProject(p);
        if (!StringUtils.hasText(p.getAttachmentPath())) {
            throw new BusinessException("暂无附件");
        }
        projectMaterialService.writeToResponse(p.getAttachmentPath(), response, null);
    }

    /** 项目「负责老师」下拉：启用中的教师与管理员账号 */
    @GetMapping("/teacher-options")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public R<?> teacherOptions() {
        List<User> staff = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getRole, List.of("teacher", "admin"))
                .eq(User::getActive, true)
                .orderByAsc(User::getName)
                .last("LIMIT 500"));
        List<Map<String, Object>> opts = new ArrayList<>();
        for (User u : staff) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("username", u.getUsername());
            m.put("college", u.getCollege());
            m.put("role", u.getRole());
            opts.add(m);
        }
        return R.ok(opts);
    }

    @GetMapping("/competition-options")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public R<?> competitionOptions() {
        List<Competition> list;
        if (securityUtil.isAdmin() || isStudentRole()) {
            list = competitionMapper.selectList(
                    new LambdaQueryWrapper<Competition>()
                            .eq(Competition::getIsDraft, false)
                            .orderByDesc(Competition::getCreatedAt)
                            .last("LIMIT 500"));
        } else {
            List<Long> allowed = competitionIdsForCurrentAdvisorTeacher();
            if (allowed.isEmpty()) {
                return R.ok(Collections.emptyList());
            }
            list = competitionMapper.selectList(
                    new LambdaQueryWrapper<Competition>()
                            .in(Competition::getId, allowed)
                            .orderByDesc(Competition::getCreatedAt)
                            .last("LIMIT 500"));
        }
        List<Map<String, Object>> opts = new ArrayList<>();
        for (Competition c : list) {
            opts.add(Map.of("id", c.getId(), "name", c.getName() != null ? c.getName() : ""));
        }
        return R.ok(opts);
    }

    /**
     * 创建/修改项目时校验竞赛；教师必须关联本人为负责老师之一的竞赛，且不可关联「空竞赛」。
     */
    private R<?> createByStudent(ProjectRequest req) {
        Long advisorId = validateAdvisorTeacherId(req.getAdvisorTeacherId());
        if (advisorId == null) {
            throw new BusinessException("请选择负责老师");
        }
        User me = requireCurrentStudentUser();
        validateCompetitionForStudentWrite(req.getCompetitionId());
        InnovationProject p = new InnovationProject();
        p.setName(req.getName().trim());
        p.setDescription(trimToNull(req.getDescription()));
        p.setCompetitionId(req.getCompetitionId());
        p.setLeaderName(me.getName());
        p.setLeaderStudentId(me.getStudentId().trim());
        p.setAdvisorTeacherId(advisorId);
        p.setStatus("ongoing");
        p.setRemark(trimToNull(req.getRemark()));
        p.setCreatedBy(me.getId());
        innovationProjectMapper.insert(p);
        return R.ok("创建成功", Map.of("id", p.getId()));
    }

    private void validateCompetitionForWrite(Long competitionId) {
        if (isStudentRole()) {
            validateCompetitionForStudentWrite(competitionId);
            return;
        }
        if (competitionId == null) {
            if (!securityUtil.isAdmin()) {
                throw new BusinessException("教师操作须关联您负责的竞赛");
            }
            return;
        }
        Competition c = competitionMapper.selectById(competitionId);
        if (c == null) {
            throw new BusinessException("关联竞赛不存在");
        }
        if (!securityUtil.isAdmin()) {
            Long uid = securityUtil.getCurrentUserId();
            if (!isAdvisorContainingUser(c.getAdvisor(), uid)) {
                throw new BusinessException("只能关联您作为负责老师的竞赛");
            }
        }
    }

    private void validateCompetitionForStudentWrite(Long competitionId) {
        if (competitionId == null) {
            return;
        }
        Competition c = competitionMapper.selectById(competitionId);
        if (c == null || Boolean.TRUE.equals(c.getIsDraft())) {
            throw new BusinessException("关联竞赛不存在或不可选");
        }
    }

    private boolean isStudentRole() {
        return "student".equalsIgnoreCase(securityUtil.getCurrentRole());
    }

    private String requireCurrentStudentId() {
        User me = requireCurrentStudentUser();
        return me.getStudentId().trim();
    }

    private User requireCurrentStudentUser() {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        User me = userMapper.selectById(uid);
        if (me == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(me.getStudentId())) {
            throw new BusinessException("当前账号未绑定学号，无法使用项目管理");
        }
        return me;
    }

    private void assertStudentOwnsProject(InnovationProject p) {
        String sid = requireCurrentStudentId();
        if (!StringUtils.hasText(p.getLeaderStudentId()) || !sid.equals(p.getLeaderStudentId().trim())) {
            throw new BusinessException("无权操作该项目");
        }
    }

    private void assertCanAccessInnovationProject(InnovationProject p) {
        if (p == null) {
            throw new BusinessException("项目不存在");
        }
        if (securityUtil.isAdmin()) {
            return;
        }
        if (isStudentRole()) {
            assertStudentOwnsProject(p);
            return;
        }
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("请先登录");
        }
        if (uid.equals(p.getAdvisorTeacherId())) {
            return;
        }
        if (p.getCompetitionId() == null) {
            throw new BusinessException("无权访问该项目");
        }
        Competition c = competitionMapper.selectById(p.getCompetitionId());
        if (!isAdvisorContainingUser(c != null ? c.getAdvisor() : null, uid)) {
            throw new BusinessException("无权访问该项目");
        }
    }

    /** 校验并返回负责老师用户 ID；null 表示不设置 */
    private Long validateAdvisorTeacherId(Long teacherUserId) {
        if (teacherUserId == null) {
            return null;
        }
        User teacher = userMapper.selectById(teacherUserId);
        if (teacher == null || !Boolean.TRUE.equals(teacher.getActive())) {
            throw new BusinessException("负责老师不存在或已停用");
        }
        if (!"teacher".equalsIgnoreCase(teacher.getRole()) && !"admin".equalsIgnoreCase(teacher.getRole())) {
            throw new BusinessException("负责老师须为教师或管理员账号");
        }
        return teacherUserId;
    }

    /** 当前登录教师作为「负责老师」（advisor 为用户 ID 列表）的正式竞赛 ID 列表 */
    private List<Long> competitionIdsForCurrentAdvisorTeacher() {
        Long uid = securityUtil.getCurrentUserId();
        if (uid == null) {
            return Collections.emptyList();
        }
        List<Competition> comps = competitionMapper.selectList(new LambdaQueryWrapper<Competition>());
        List<Long> ids = new ArrayList<>();
        for (Competition c : comps) {
            if (isAdvisorContainingUser(c.getAdvisor(), uid)) {
                ids.add(c.getId());
            }
        }
        return ids;
    }

    /**
     * 与竞赛表单一致：advisor 为逗号分隔的用户 ID 时，判断是否包含指定用户。
     * 纯文本旧数据无法判定，视为不包含。
     */
    private static boolean isAdvisorContainingUser(String advisorRaw, Long userId) {
        if (!StringUtils.hasText(advisorRaw) || userId == null) {
            return false;
        }
        String t = advisorRaw.trim();
        if (!t.matches("\\d+(\\s*,\\s*\\d+)*")) {
            return false;
        }
        for (String part : t.split("\\s*,\\s*")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                if (userId.equals(Long.parseLong(part.trim()))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return false;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ongoing";
        }
        String s = status.trim();
        if ("planning".equals(s) || "suspended".equals(s)) {
            return "ongoing";
        }
        if (!STATUS_SET.contains(s)) {
            throw new BusinessException("状态无效，应为：ongoing（进行中）或 completed（已结束）");
        }
        return s;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 同一队伍在同一场竞赛下只允许有一条「创新项目档案」录入竞赛成绩；已为其它档案打分时，当前档案不可再打分；已为当前档案打分时也不可重复提交。
     */
    private void assertTeamCompetitionScoreExclusive(InnovationProject p, Long editingInnovationProjectId) {
        if (peerOtherInnovationProjectAlreadyScored(p.getTeamId(), p.getCompetitionId(), editingInnovationProjectId)) {
            throw new BusinessException("该队伍在本竞赛已有其他项目档案评定成绩，不可对多条档案重复打分");
        }
    }

    /** 是否存在「同队、同赛」的其它档案已有关联 score（不含 excludeInnovationProjectId） */
    private boolean peerOtherInnovationProjectAlreadyScored(Long teamId, Long competitionId, Long excludeInnovationProjectId) {
        if (teamId == null || competitionId == null || excludeInnovationProjectId == null) {
            return false;
        }
        List<Long> siblingIds = innovationProjectMapper.selectList(new LambdaQueryWrapper<InnovationProject>()
                        .eq(InnovationProject::getTeamId, teamId)
                        .eq(InnovationProject::getCompetitionId, competitionId))
                .stream()
                .map(InnovationProject::getId)
                .filter(pid -> !pid.equals(excludeInnovationProjectId))
                .collect(Collectors.toList());
        if (siblingIds.isEmpty()) {
            return false;
        }
        Long cnt = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .in(Score::getInnovationProjectId, siblingIds));
        return cnt != null && cnt > 0;
    }

    private Map<Long, Score> loadScoresByInnovationProjectIds(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Score> list = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .in(Score::getInnovationProjectId, projectIds));
        return list.stream()
                .filter(s -> s.getInnovationProjectId() != null)
                .collect(Collectors.toMap(Score::getInnovationProjectId, Function.identity(), (a, b) -> a));
    }

    private static void putScoreFields(Map<String, Object> m, Score sc) {
        if (sc == null) {
            m.put("scoreId", null);
            m.put("scoreTotal", null);
            m.put("scoreShow", null);
            return;
        }
        m.put("scoreId", sc.getId());
        m.put("scoreTotal", sc.getTotalScore());
        m.put("scoreShow", sc.getShow());
    }

    /**
     * 优先队伍 captain；其次负责人学号对应用户；再次创建人为本人的学生账号（学生队长上传场景）。
     */
    private Long resolveCaptainUserId(InnovationProject p) {
        if (p.getTeamId() != null) {
            Team team = teamMapper.selectById(p.getTeamId());
            if (team != null && team.getCaptainUserId() != null) {
                return team.getCaptainUserId();
            }
        }
        if (StringUtils.hasText(p.getLeaderStudentId())) {
            User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getStudentId, p.getLeaderStudentId().trim()));
            if (u != null) {
                return u.getId();
            }
        }
        if (p.getCreatedBy() != null) {
            User creator = userMapper.selectById(p.getCreatedBy());
            if (creator != null && "student".equalsIgnoreCase(creator.getRole())) {
                return p.getCreatedBy();
            }
        }
        return null;
    }

    /** 项目管理打分后，禁止该档案所属队伍在学生端继续上传新材料 */
    private void lockTeamProjectSubmitAfterScoreSaved(InnovationProject p) {
        if (p == null || p.getTeamId() == null) {
            return;
        }
        Team team = teamMapper.selectById(p.getTeamId());
        if (team == null) {
            return;
        }
        team.setProjectSubmitLocked(Boolean.TRUE);
        teamMapper.updateById(team);
    }

    private void deleteAttachmentFile(String relative) {
        if (!StringUtils.hasText(relative)) {
            return;
        }
        try {
            Path path = projectMaterialService.resolveStoredFile(relative);
            if (path != null && Files.exists(path)) {
                Files.delete(path);
            }
        } catch (Exception ignored) {
            // 忽略删除失败
        }
    }

    @Data
    public static class TeacherEvaluationBody {
        private String content;
    }

    @Data
    public static class ProjectScoreBody {
        @NotNull(message = "成绩不能为空")
        private BigDecimal totalScore;
    }

    @Data
    public static class ProjectRequest {
        @NotBlank(message = "项目名称不能为空")
        private String name;
        private String description;
        private Long competitionId;
        private String leaderName;
        private String leaderStudentId;
        /** 负责老师用户 ID（学生新建项目时指定，该教师可查看） */
        private Long advisorTeacherId;
        /** ongoing 进行中 / completed 已结束 */
        private String status;
        private String remark;
    }
}
