package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.common.R;
import com.innovation.entity.*;
import com.innovation.mapper.*;
import com.innovation.security.SecurityUtil;
import com.innovation.service.CompetitionService;
import com.innovation.service.MessageService;
import com.innovation.service.RegistrationService;
import com.innovation.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CompetitionMapper competitionMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final AnnouncementMapper announcementMapper;
    @Autowired
    private CompetitionService competitionService;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private UserService userService;
    @Autowired
    private MessageService messageService;

    @GetMapping("/overview")
    public R<?> overview() {
        // 报名中、报名已结束、进行中（不含筹备中、已结束）
        long ongoingCompCount = competitionMapper.selectCount(
                new LambdaQueryWrapper<Competition>()
                        .eq(Competition::getIsDraft, false)
                        .in(Competition::getStatus, List.of("enrolling", "enrollend", "ongoing")));
        long pendingRegCount = registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>().eq(Registration::getStatus, "pending"));
        long studentCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "student"));
        long msgCount = messageMapper.selectCount(null);

        List<Map<String, Object>> stats = List.of(
                Map.of("label", "进行中竞赛", "value", String.valueOf(ongoingCompCount), "trend", competitionService.newAdd(), "trendUp", true),
                Map.of("label", "待审核报名", "value", String.valueOf(pendingRegCount), "trend", registrationService.newAdd(), "trendUp", true),
                Map.of("label", "注册学生", "value", String.valueOf(studentCount), "trend", userService.newAdd(), "trendUp", true),
                Map.of("label", "留言互动", "value", String.valueOf(msgCount), "trend", messageService.newAdd(), "trendUp", true)
        );

        // 最近竞赛
        var recentComps = competitionMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 5),
                new LambdaQueryWrapper<Competition>()
                        .eq(Competition::getIsDraft, false)
                        .orderByDesc(Competition::getCreatedAt));

        // 最新公告
        var recentAnn = announcementMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 5),
                new LambdaQueryWrapper<Announcement>().orderByDesc(Announcement::getCreatedAt));

        return R.ok(Map.of(
                "stats", stats,
                "recentCompetitions", recentComps.getRecords(),
                "announcements", recentAnn.getRecords(),
                "categoryDistribution", buildCategoryDistributionLastMonth(),
                "monthlyPublish", buildMonthlyPublishLastSixMonths()
        ));
    }

  /** 近一个月内发布的非草稿竞赛，按类别统计占比 */
    private List<Map<String, Object>> buildCategoryDistributionLastMonth() {
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        List<Competition> list = competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                .eq(Competition::getIsDraft, false)
                .ge(Competition::getCreatedAt, since));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (Competition c : list) {
            String cat = c.getCategory() != null && !c.getCategory().isBlank() ? c.getCategory().trim() : "未分类";
            grouped.merge(cat, 1L, Long::sum);
        }
        long total = list.size();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : grouped.entrySet()) {
            double percent = total > 0 ? Math.round(e.getValue() * 1000.0 / total) / 10.0 : 0;
            result.add(Map.of(
                    "category", e.getKey(),
                    "count", e.getValue(),
                    "percent", percent
            ));
        }
        result.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return result;
    }

    /** 近半年（含当月）每月发布的非草稿竞赛数量 */
    private List<Map<String, Object>> buildMonthlyPublishLastSixMonths() {
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(5);
        LocalDateTime rangeStart = start.atDay(1).atStartOfDay();

        List<Competition> list = competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                .eq(Competition::getIsDraft, false)
                .ge(Competition::getCreatedAt, rangeStart));

        Map<YearMonth, Long> counts = list.stream()
                .filter(c -> c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> YearMonth.from(c.getCreatedAt()),
                        Collectors.counting()));

        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("yyyy年M月");
        List<Map<String, Object>> result = new ArrayList<>();
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            long count = counts.getOrDefault(ym, 0L);
            result.add(Map.of(
                    "month", ym.toString(),
                    "label", ym.format(labelFmt),
                    "count", count
            ));
        }
        return result;
    }
}
