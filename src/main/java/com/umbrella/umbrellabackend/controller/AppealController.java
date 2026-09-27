package com.umbrella.umbrellabackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/appeal")
public class AppealController {

    @Autowired private JdbcTemplate jdbcTemplate;
    // 查所有待处理申诉
    @GetMapping("/pending")
    public Map<String, Object> pending() {
        Map<String, Object> res = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT `申诉ID`, `校园卡号`, `关联记录ID`, `申诉内容`, `创建时间` " +
                            "FROM `申诉记录表` WHERE `申诉状态` = '待处理' ORDER BY `申诉ID` ASC");
            res.put("code", 200);
            res.put("data", list);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    // 审核申诉
    @PostMapping("/audit")
    public Map<String, Object> audit(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            int appealId = ((Number) body.get("appealId")).intValue();
            String result = (String) body.get("result");
            String remark = (String) body.getOrDefault("remark", "");

            jdbcTemplate.update(
                    "UPDATE `申诉记录表` SET `申诉状态` = ?, `管理员备注` = ?, `处理时间` = NOW() WHERE `申诉ID` = ?",
                    result, remark, appealId);

            res.put("code", 200);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    @GetMapping("/my-appeals")
    public Map<String, Object> myAppeals(@RequestParam String cardId) {
        Map<String, Object> res = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT `申诉ID`, `关联记录ID`, `申诉内容`, `申诉状态`, `管理员备注`, `创建时间` " +
                            "FROM `申诉记录表` WHERE `校园卡号` = ? ORDER BY `申诉ID` DESC LIMIT 50",
                    cardId);
            res.put("code", 200);
            res.put("data", list);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            String cardId = (String) body.get("cardId");
            Object recordId = body.get("recordId");
            String reason = (String) body.getOrDefault("reason", "");
            String content = (String) body.getOrDefault("content", "");

            jdbcTemplate.update(
                    "INSERT INTO `申诉记录表` (`校园卡号`, `关联记录ID`, `申诉内容`, `申诉状态`) " +
                            "VALUES (?, ?, ?, '待处理')",
                    cardId, recordId, reason + "：" + content);

            res.put("code", 200);
            res.put("msg", "申诉已提交");
        } catch (Exception e) {
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
}