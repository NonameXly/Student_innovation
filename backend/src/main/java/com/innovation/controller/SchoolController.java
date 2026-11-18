package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innovation.common.R;
import com.innovation.entity.School;
import com.innovation.mapper.SchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学校字典：注册页等场景需匿名拉取下拉列表。
 */
@RestController
@RequestMapping("/school")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolMapper schoolMapper;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") Integer page,
                     @RequestParam(defaultValue = "200") Integer size) {
        var pageResult = schoolMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<School>().orderByAsc(School::getSchoolCode));
        List<Map<String, Object>> list = new ArrayList<>();
        for (School s : pageResult.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("schoolCode", s.getSchoolCode());
            row.put("schoolName", s.getSchoolName());
            row.put("name", s.getSchoolName());
            list.add(row);
        }
        return R.ok(Map.of("total", pageResult.getTotal(), "list", list));
    }
}
