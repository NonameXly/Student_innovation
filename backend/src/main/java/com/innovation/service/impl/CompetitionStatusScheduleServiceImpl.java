package com.innovation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innovation.entity.Competition;
import com.innovation.mapper.CompetitionMapper;
import com.innovation.service.CompetitionStatusScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompetitionStatusScheduleServiceImpl implements CompetitionStatusScheduleService {

    private final CompetitionMapper competitionMapper;

    @Scheduled(cron = "0 0 0 * * ?")
    @Override
    public void refreshCompetitionStatusDaily() {
        refreshCompetitionStatusNow();
    }

    @Override
    public void refreshCompetitionStatusNow() {
        List<Competition> competitions = competitionMapper.selectList(
                new LambdaQueryWrapper<Competition>().eq(Competition::getIsDraft, false)
        );
        for (Competition competition : competitions) {
            String status = calculateStatus(
                    competition.getEnrollStart(),
                    competition.getEnrollEnd(),
                    competition.getStartDate(),
                    competition.getEndDate()
            );
            if (!status.equals(competition.getStatus())) {
                competition.setStatus(status);
                competitionMapper.updateById(competition);
            }
        }
    }

    /** 与 CompetitionController：开始日 0:00～结束日 24:00；开赛日须严格晚于报名截止日 */
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
}
