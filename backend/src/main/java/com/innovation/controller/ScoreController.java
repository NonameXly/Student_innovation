package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.BusinessException;
import com.innovation.common.OptimisticLockHelper;
import com.innovation.common.R;
import com.innovation.entity.Competition;
import com.innovation.entity.Score;
import com.innovation.entity.User;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.mapper.ScoreMapper;
import com.innovation.mapper.UserMapper;
import com.innovation.security.SecurityUtil;
import com.innovation.service.NoticeService;
import javax.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/score")
@RequiredArgsConstructor
public class ScoreController {

    /** 与导出一致；导入时首行须逐列完全匹配 */
    private static final String[] SCORE_SHEET_HEADERS = {"排名", "学生姓名", "学号", "学院", "竞赛", "总分", "奖项"};
    private static final Map<String, String> AWARD_CN_TO_CODE = Map.of(
            "一等奖", "first",
            "二等奖", "second",
            "三等奖", "third",
            "优秀奖", "excellent"
    );

    private final ScoreMapper scoreMapper;
    private final UserMapper userMapper;
    private final CompetitionMapper competitionMapper;
    private final SecurityUtil securityUtil;
    private final NoticeService noticeService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long competition,
                     @RequestParam(required = false) String award,
                     @RequestParam(required = false) String competitionCategory,
                     @RequestParam(required = false) String competitionName,
                     @RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "20") Integer size) {
        var wrapper = new LambdaQueryWrapper<Score>()
                .eq(StringUtils.hasText(award), Score::getAward, award)
                .orderByDesc(Score::getTotalScore);

        if (competition != null) {
            wrapper.eq(Score::getCompetitionId, competition);
        } else if (StringUtils.hasText(competitionCategory) || StringUtils.hasText(competitionName)) {
            var compWrapper = new LambdaQueryWrapper<Competition>();
            if (StringUtils.hasText(competitionCategory)) {
                compWrapper.eq(Competition::getCategory, competitionCategory);
            }
            if (StringUtils.hasText(competitionName)) {
                compWrapper.like(Competition::getName, competitionName.trim());
            }
            List<Long> compIds = competitionMapper.selectList(compWrapper).stream()
                    .map(Competition::getId)
                    .collect(Collectors.toList());
            if (compIds.isEmpty()) {
                return R.ok(Map.of("total", 0L, "list", Collections.emptyList()));
            }
            wrapper.in(Score::getCompetitionId, compIds);
        }

        // 学生仅可查本人且已公示的成绩；教师/管理员可查全部
        if (!securityUtil.isStaff()) {
            Long uid = securityUtil.getCurrentUserId();
            if (uid == null) {
                throw new BusinessException("未登录");
            }
            wrapper.eq(Score::getUserId, uid);
            wrapper.eq(Score::getShow, 1);
        }
        var pageResult = scoreMapper.selectPage(new Page<>(page, size), wrapper);

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Score s : pageResult.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("totalScore", s.getTotalScore());
            map.put("award", s.getAward());
            if (securityUtil.isStaff()) {
                map.put("show", s.getShow());
            }

            User user = userMapper.selectById(s.getUserId());
            if (user != null) {
                map.put("studentName", user.getName());
                map.put("studentId", user.getStudentId());
                map.put("college", user.getCollege());
            }
            Competition comp = competitionMapper.selectById(s.getCompetitionId());
            if (comp != null) {
                map.put("competition", comp.getName());
            }
            map.put("teamName", null); // 可从报名表关联
            resultList.add(map);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", resultList));
    }

    /**
     * 将该竞赛下所有成绩记录设为向学生公示（show=1）。
     */
    @PostMapping("/publish-by-competition")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> publishScoresByCompetition(@RequestParam Long competitionId) {
        Competition comp = competitionMapper.selectById(competitionId);
        if (comp == null) {
            throw new BusinessException("竞赛不存在");
        }
        int rows = scoreMapper.update(null, new LambdaUpdateWrapper<Score>()
                .eq(Score::getCompetitionId, competitionId)
                .set(Score::getShow, 1));
        int noticesSent = noticeService.notifyStudentsCompetitionScoresPublished(competitionId, comp.getName());
        return R.ok(Map.of("updated", rows, "noticesSent", noticesSent));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> statistics() {
        long total = scoreMapper.selectCount(null);
        long awarded = scoreMapper.selectCount(new LambdaQueryWrapper<Score>().isNotNull(Score::getAward));
        String awardRate = total > 0 ? String.format("%.1f%%", awarded * 100.0 / total) : "0%";

        // 通过数据库聚合查询平均分和已评竞赛数，避免加载全表
        List<Map<String, Object>> aggResult = scoreMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Score>()
                        .select("IFNULL(AVG(total_score), 0) as avg_score", "COUNT(DISTINCT competition_id) as comp_count"));
        double avg = 0;
        long compCount = 0;
        if (aggResult != null && !aggResult.isEmpty()) {
            Map<String, Object> row = aggResult.get(0);
            avg = row.get("avg_score") != null ? ((Number) row.get("avg_score")).doubleValue() : 0;
            compCount = row.get("comp_count") != null ? ((Number) row.get("comp_count")).longValue() : 0;
        }

        return R.ok(Map.of(
                "evaluatedCompetitions", compCount,
                "totalParticipants", total,
                "awardRate", awardRate,
                "averageScore", Math.round(avg * 10) / 10.0
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> create(@RequestBody ScoreRequest req) {
        Score score = new Score();
        // 通过学号查找用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentId, req.getStudentId()));
        if (user == null) throw new BusinessException("学号不存在: " + req.getStudentId());
        score.setUserId(user.getId());
        // 通过竞赛名查找竞赛
        Competition comp = competitionMapper.selectOne(new LambdaQueryWrapper<Competition>().eq(Competition::getName, req.getCompetition()));
        if (comp == null) throw new BusinessException("竞赛不存在: " + req.getCompetition());
        score.setCompetitionId(comp.getId());
        score.setTotalScore(req.getTotalScore());
        score.setAward(req.getAward());
        score.setRemark(req.getRemark());
        score.setShow(toShowFlag(req.getShow()));
        score.setCreatedBy(securityUtil.getCurrentUserId());
        scoreMapper.insert(score);
        return R.ok("成绩保存成功", null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> update(@PathVariable Long id, @RequestBody ScoreRequest req) {
        Score score = scoreMapper.selectById(id);
        if (score == null) throw new BusinessException("成绩记录不存在");
        score.setTotalScore(req.getTotalScore());
        score.setAward(req.getAward());
        score.setRemark(req.getRemark());
        if (req.getShow() != null) {
            score.setShow(toShowFlag(req.getShow()));
        }
        OptimisticLockHelper.assertUpdated(scoreMapper.updateById(score));
        return R.ok("成绩更新成功", null);
    }

    @GetMapping("/{id}/analysis")
    public R<?> analysis(@PathVariable Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) throw new BusinessException("成绩记录不存在");
        if (!securityUtil.isStaff()) {
            Long uid = securityUtil.getCurrentUserId();
            if (uid == null || score.getUserId() == null || !uid.equals(score.getUserId())) {
                throw new BusinessException("无权查看该成绩分析");
            }
            if (!Integer.valueOf(1).equals(score.getShow())) {
                throw new BusinessException("成绩尚未公布");
            }
        }
        User user = userMapper.selectById(score.getUserId());
        // 计算排名
        long ranking = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getCompetitionId, score.getCompetitionId())
                .gt(Score::getTotalScore, score.getTotalScore())) + 1;
        long total = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getCompetitionId, score.getCompetitionId()));
        double percentile;
        if (total <= 1) {
            percentile = 100.0;
        } else {
            percentile = (double) (total - ranking) / (total - 1) * 100;
        }

        Map<String, Object> awardNames = Map.of(
                "first", "一等奖", "second", "二等奖", "third", "三等奖", "excellent", "优秀奖");

        return R.ok(Map.of(
                "studentName", user != null ? user.getName() : "",
                "ranking", ranking,
                "scorePercentile", Math.round(percentile * 10) / 10.0,
                "awardInfo", score.getAward() != null ? awardNames.getOrDefault(score.getAward(), "") : "未获奖"
        ));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void importTemplate(@RequestParam(required = false) String token, HttpServletResponse response) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("成绩表");
            Row header = sheet.createRow(0);
            for (int i = 0; i < SCORE_SHEET_HEADERS.length; i++) {
                header.createCell(i).setCellValue(SCORE_SHEET_HEADERS[i]);
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=score_import_template.xlsx");
            wb.write(response.getOutputStream());
        }
    }

    /**
     * 批量导入成绩：Excel 首行必须为「排名、学生姓名、学号、学院、竞赛、总分、奖项」，与导出模板一致。
     * 按学号、竞赛名称解析并写入数据库；同一学生同一竞赛已存在则更新总分与奖项。
     *
     * @param useGrading 为 true 时启用赋分制：同一竞赛内按表格「总分」从高到低排名，再按名次占比线性映射到百分制（0～100）后入库；同分名次取平均位置。同一学生同一竞赛在表中出现多行时以最后一行为准参与排名。
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> importExcel(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "useGrading", defaultValue = "false") boolean useGrading) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式（与导出模板一致）");
        }
        DataFormatter formatter = new DataFormatter();
        int inserted = 0;
        int updated = 0;
        List<String> failed = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("工作表为空");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException("缺少表头行");
            }
            validateImportHeader(headerRow, formatter);

            int lastRow = sheet.getLastRowNum();
            Long operatorId = securityUtil.getCurrentUserId();
            List<ImportLine> pending = new ArrayList<>();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String rankCol = cellString(row, 0, formatter);
                String studentName = cellString(row, 1, formatter);
                String studentId = cellString(row, 2, formatter);
                String college = cellString(row, 3, formatter);
                String competitionName = cellString(row, 4, formatter);
                String scoreStr = cellString(row, 5, formatter);
                String awardRaw = cellString(row, 6, formatter);

                boolean allKeyEmpty = !StringUtils.hasText(studentId)
                        && !StringUtils.hasText(studentName)
                        && !StringUtils.hasText(competitionName)
                        && !StringUtils.hasText(scoreStr)
                        && !StringUtils.hasText(awardRaw);
                if (allKeyEmpty && !StringUtils.hasText(college) && !StringUtils.hasText(rankCol)) {
                    continue;
                }
                int excelRowNum = r + 1;
                try {
                    ImportLine line = buildImportLine(
                            excelRowNum, studentName, studentId, college, competitionName, scoreStr, awardRaw, useGrading);
                    pending.add(line);
                } catch (IllegalArgumentException ex) {
                    failed.add("第" + excelRowNum + "行: " + ex.getMessage());
                }
            }

            if (useGrading) {
                Map<String, ImportLine> dedup = new LinkedHashMap<>();
                for (ImportLine line : pending) {
                    dedup.put(line.getUserId() + "_" + line.getCompetitionId(), line);
                }
                pending = new ArrayList<>(dedup.values());
                Map<Long, List<ImportLine>> byCompetition = pending.stream()
                        .collect(Collectors.groupingBy(ImportLine::getCompetitionId));
                for (List<ImportLine> group : byCompetition.values()) {
                    assignRankBasedPercentScores(group);
                }
            }

            for (ImportLine line : pending) {
                BigDecimal totalScore = useGrading ? line.getGradedScore() : line.getRawScore();
                Score existing = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                        .eq(Score::getUserId, line.getUserId())
                        .eq(Score::getCompetitionId, line.getCompetitionId()));
                if (existing != null) {
                    existing.setTotalScore(totalScore);
                    existing.setAward(line.getAwardCode());
                    existing.setShow(0);
                    try {
                        OptimisticLockHelper.assertUpdated(scoreMapper.updateById(existing));
                        updated++;
                    } catch (BusinessException ex) {
                        if (ex.getCode() == OptimisticLockHelper.CONFLICT_CODE) {
                            failed.add("第" + line.getExcelRowNum() + "行: " + ex.getMessage());
                        } else {
                            throw ex;
                        }
                    }
                } else {
                    Score score = new Score();
                    score.setUserId(line.getUserId());
                    score.setCompetitionId(line.getCompetitionId());
                    score.setTotalScore(totalScore);
                    score.setAward(line.getAwardCode());
                    score.setRemark(null);
                    score.setShow(0);
                    score.setCreatedBy(operatorId);
                    scoreMapper.insert(score);
                    inserted++;
                }
            }
        }

        if (inserted == 0 && updated == 0 && failed.isEmpty()) {
            throw new BusinessException("没有可导入的数据行（已跳过空行）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inserted", inserted);
        data.put("updated", updated);
        data.put("failed", failed);
        return R.ok(data);
    }

    private void validateImportHeader(Row headerRow, DataFormatter formatter) {
        for (int i = 0; i < SCORE_SHEET_HEADERS.length; i++) {
            String actual = cellString(headerRow, i, formatter);
            if (!SCORE_SHEET_HEADERS[i].equals(actual)) {
                throw new BusinessException(
                        "表头第" + (i + 1) + "列须为「" + SCORE_SHEET_HEADERS[i] + "」，当前为「" + actual
                                + "」。请使用与导出一致的列名：排名、学生姓名、学号、学院、竞赛、总分、奖项");
            }
        }
    }

    private static String cellString(Row row, int colIndex, DataFormatter formatter) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    /** @param raw 中文奖项名或 first/second/third/excellent；空表示未获奖 */
    private String parseAwardCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (AWARD_CN_TO_CODE.containsKey(t)) {
            return AWARD_CN_TO_CODE.get(t);
        }
        String low = t.toLowerCase(Locale.ROOT);
        if (Set.of("first", "second", "third", "excellent").contains(low)) {
            return low;
        }
        throw new IllegalArgumentException("无法识别奖项「" + t + "」，请填写：一等奖/二等奖/三等奖/优秀奖或留空");
    }

    /**
     * 解析一行导入数据；赋分制下「总分」列仅用于排序，允许大于 100 的非负数值。
     */
    private ImportLine buildImportLine(int excelRowNum, String studentName, String studentId, String college,
                                       String competitionName, String scoreStr, String awardRaw, boolean useGrading) {
        if (!StringUtils.hasText(studentId)) {
            throw new IllegalArgumentException("学号不能为空");
        }
        if (!StringUtils.hasText(studentName)) {
            throw new IllegalArgumentException("学生姓名不能为空");
        }
        if (!StringUtils.hasText(competitionName)) {
            throw new IllegalArgumentException("竞赛不能为空");
        }
        if (!StringUtils.hasText(scoreStr)) {
            throw new IllegalArgumentException("总分不能为空");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentId, studentId));
        if (user == null) {
            throw new IllegalArgumentException("学号不存在: " + studentId);
        }
        String dbName = user.getName() != null ? user.getName().trim() : "";
        if (!studentName.trim().equals(dbName)) {
            throw new IllegalArgumentException("学生姓名与档案不一致（学号对应姓名应为「" + dbName + "」）");
        }
        if (StringUtils.hasText(college)) {
            String dbCollege = user.getCollege() != null ? user.getCollege().trim() : "";
            if (!StringUtils.hasText(dbCollege)) {
                throw new IllegalArgumentException("档案中学院为空，与表格中的学院不一致");
            }
            if (!college.trim().equals(dbCollege)) {
                throw new IllegalArgumentException("学院与档案不一致（应为「" + dbCollege + "」）");
            }
        }

        Competition comp = competitionMapper.selectOne(
                new LambdaQueryWrapper<Competition>().eq(Competition::getName, competitionName.trim()));
        if (comp == null) {
            throw new IllegalArgumentException("竞赛不存在: " + competitionName);
        }

        BigDecimal rawScore;
        try {
            rawScore = new BigDecimal(scoreStr.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("总分格式不正确");
        }
        if (rawScore.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("总分不能为负数");
        }
        if (!useGrading) {
            if (rawScore.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("总分须在 0～100 之间");
            }
        } else {
            if (rawScore.compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException("赋分制下排序用总分过大（上限 1000000）");
            }
        }
        rawScore = rawScore.setScale(4, RoundingMode.HALF_UP);
        if (!useGrading) {
            rawScore = rawScore.setScale(1, RoundingMode.HALF_UP);
        }

        String awardCode = parseAwardCode(awardRaw);
        return new ImportLine(excelRowNum, user.getId(), comp.getId(), rawScore, awardCode);
    }

    /**
     * 同一竞赛内按 rawScore 降序；名次按占比线性映射到 [0,100]（第一名 100，最后一名 0，同分取平均名次）。
     */
    private static void assignRankBasedPercentScores(List<ImportLine> group) {
        if (group.isEmpty()) {
            return;
        }
        group.sort(Comparator.comparing(ImportLine::getRawScore).reversed()
                .thenComparing(ImportLine::getUserId));
        int n = group.size();
        if (n == 1) {
            group.get(0).setGradedScore(BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP));
            return;
        }
        int i = 0;
        while (i < n) {
            int j = i + 1;
            BigDecimal s = group.get(i).getRawScore();
            while (j < n && group.get(j).getRawScore().compareTo(s) == 0) {
                j++;
            }
            double avgIndex = (i + (double) (j - 1)) / 2.0;
            BigDecimal graded = BigDecimal.valueOf(100.0 * (n - 1 - avgIndex) / (n - 1))
                    .setScale(1, RoundingMode.HALF_UP);
            for (int k = i; k < j; k++) {
                group.get(k).setGradedScore(graded);
            }
            i = j;
        }
    }

    @Data
    private static class ImportLine {
        /** Excel 行号（用于错误提示；去重后以最后一次出现行为准） */
        private final int excelRowNum;
        private final Long userId;
        private final Long competitionId;
        /** 表格中的分数，赋分制下仅用于排序 */
        private final BigDecimal rawScore;
        private final String awardCode;
        /** 赋分制换算后的百分制分数 */
        private BigDecimal gradedScore;
    }

    /** 导出成绩时可选择的已结束竞赛列表（含成绩条数） */
    @GetMapping("/export/competitions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<?> exportCompetitionOptions() {
        List<Competition> competitions = competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                .eq(Competition::getIsDraft, false)
                .eq(Competition::getStatus, "finished")
                .orderByDesc(Competition::getCreatedAt));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Competition c : competitions) {
            long scoreCount = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                    .eq(Score::getCompetitionId, c.getId()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getName());
            item.put("category", c.getCategory());
            item.put("scoreCount", scoreCount);
            list.add(item);
        }
        return R.ok(list);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void export(@RequestParam Long competitionId,
                       @RequestParam(required = false) String token,
                       HttpServletResponse response) throws IOException {
        if (competitionId == null) {
            throw new BusinessException("请选择要导出的竞赛");
        }
        Competition competition = competitionMapper.selectById(competitionId);
        if (competition == null) {
            throw new BusinessException("竞赛不存在");
        }
        if (Boolean.TRUE.equals(competition.getIsDraft())) {
            throw new BusinessException("草稿竞赛不可导出成绩");
        }
        if (!"finished".equals(competition.getStatus())) {
            throw new BusinessException("仅支持导出已结束竞赛的成绩");
        }

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getCompetitionId, competitionId)
                .orderByDesc(Score::getTotalScore));
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("成绩表");
            Row header = sheet.createRow(0);
            for (int i = 0; i < SCORE_SHEET_HEADERS.length; i++) {
                header.createCell(i).setCellValue(SCORE_SHEET_HEADERS[i]);
            }
            Map<String, String> awardNames = Map.of(
                    "first", "一等奖", "second", "二等奖", "third", "三等奖", "excellent", "优秀奖");
            int rowIdx = 1;
            for (Score s : scores) {
                Row row = sheet.createRow(rowIdx);
                row.createCell(0).setCellValue(rowIdx);
                User user = userMapper.selectById(s.getUserId());
                row.createCell(1).setCellValue(user != null ? user.getName() : "");
                row.createCell(2).setCellValue(user != null ? user.getStudentId() : "");
                row.createCell(3).setCellValue(user != null ? user.getCollege() : "");
                row.createCell(4).setCellValue(competition.getName() != null ? competition.getName() : "");
                row.createCell(5).setCellValue(s.getTotalScore().doubleValue());
                row.createCell(6).setCellValue(s.getAward() != null ? awardNames.getOrDefault(s.getAward(), "") : "");
                rowIdx++;
            }
            String safeName = (competition.getName() != null ? competition.getName() : "competition")
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            String filename = "成绩-" + safeName + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
            wb.write(response.getOutputStream());
        }
    }

    @Data
    static class ScoreRequest {
        private String competition;
        private String studentName;
        private String studentId;
        private BigDecimal totalScore;
        private String award;
        private String remark;
        /** 是否向学生公示：1 公示，0 隐藏；新建时缺省按 0 */
        private Integer show;
    }

    private static int toShowFlag(Integer v) {
        return Integer.valueOf(1).equals(v) ? 1 : 0;
    }
}
