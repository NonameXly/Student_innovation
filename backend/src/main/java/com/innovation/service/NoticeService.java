package com.innovation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.entity.*;
import com.innovation.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final RegistrationMapper registrationMapper;
    private final ScoreMapper scoreMapper;
    private final UserMapper userMapper;

    /**
     * 向参与指定竞赛的学生发送「成绩已公布」通知（每人一条定向通知）。
     * 参与范围：该竞赛下队伍的队长与队员、报名记录中的用户、成绩表中关联的学生用户。
     *
     * @return 成功写入的通知条数
     */
    public int notifyStudentsCompetitionScoresPublished(Long competitionId, String competitionName) {
        String name = Optional.ofNullable(competitionName).filter(StringUtils::hasText).orElse("该竞赛");
        Set<Long> candidateIds = new LinkedHashSet<>();

        for (Team t : teamMapper.selectList(new LambdaQueryWrapper<Team>().eq(Team::getCompetitionId, competitionId))) {
            if (t.getCaptainUserId() != null) {
                candidateIds.add(t.getCaptainUserId());
            }
            for (TeamMember m : teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, t.getId()))) {
                if (m.getUserId() != null) {
                    candidateIds.add(m.getUserId());
                }
            }
        }

        for (Registration r : registrationMapper.selectList(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getCompetitionId, competitionId))) {
            if (r.getUserId() != null) {
                candidateIds.add(r.getUserId());
            }
        }

        for (Score s : scoreMapper.selectList(new LambdaQueryWrapper<Score>().eq(Score::getCompetitionId, competitionId))) {
            if (s.getUserId() != null) {
                candidateIds.add(s.getUserId());
            }
        }

        if (candidateIds.isEmpty()) {
            return 0;
        }

        Set<Long> studentIds = new LinkedHashSet<>();
        for (User u : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, candidateIds))) {
            if (u != null && u.getRole() != null && "student".equalsIgnoreCase(u.getRole())) {
                studentIds.add(u.getId());
            }
        }

        String title = name + "的成绩已公布";
        String content = "「" + name + "」的成绩已公示，您可前往成绩查询查看。";

        int inserted = 0;
        for (Long uid : studentIds) {
            Notice n = new Notice();
            n.setTitle(title);
            n.setContent(content);
            n.setType("success");
            n.setTargetRole(null);
            n.setRecipientUserId(uid);
            n.setLinkUrl("/student/score-query");
            noticeMapper.insert(n);
            inserted++;
        }
        return inserted;
    }
}
