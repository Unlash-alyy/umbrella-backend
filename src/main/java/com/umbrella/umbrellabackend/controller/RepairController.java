package com.umbrella.umbrellabackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/repair")
public class RepairController {

    @Autowired private JdbcTemplate jdbcTemplate;

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            String path = "D:/uploads/" + fileName;
            File dest = new File(path);
            dest.getParentFile().mkdirs();
            file.transferTo(dest);
            res.put("code", 200);
            res.put("url", "/uploads/" + fileName);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    // 查所有待审核任务
    @GetMapping("/pending")
    public Map<String, Object> pending() {
        Map<String, Object> res = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT t.`任务ID`, t.`校园卡号`, t.`加分值`, t.`提交照片`, t.`提交时间`, t.`审核备注`, " +
                            "       ty.`任务名称` " +
                            "FROM `信用修复任务表` t " +
                            "LEFT JOIN `任务类型表` ty ON t.`任务类型` = ty.`类型ID` " +
                            "WHERE t.`审核状态` = '待审核' ORDER BY t.`任务ID` ASC");
            res.put("code", 200);
            res.put("data", list);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    // 审核任务
    @PostMapping("/audit")
    public Map<String, Object> audit(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            int taskId = ((Number) body.get("taskId")).intValue();
            String result = (String) body.get("result");
            String remark = (String) body.getOrDefault("remark", "");

            // 查任务
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT `校园卡号`, `加分值` FROM `信用修复任务表` WHERE `任务ID` = ?", taskId);
            if (list.isEmpty()) {
                res.put("code", 404);
                res.put("msg", "任务不存在");
                return res;
            }
            String cardId = (String) list.get(0).get("校园卡号");
            Integer addScore = (Integer) list.get(0).get("加分值");

            // 更新任务状态
            jdbcTemplate.update(
                    "UPDATE `信用修复任务表` SET `审核状态` = ?, `审核备注` = ?, `审核时间` = NOW() WHERE `任务ID` = ?",
                    result, remark, taskId);

            // 通过则加信用分
            if ("通过".equals(result)) {
                jdbcTemplate.update(
                        "UPDATE `校园卡用户表` SET `信用分` = LEAST(`信用分` + ?, 100) WHERE `校园卡号` = ?",
                        addScore, cardId);

                // 记一条信用变动
                Integer newScore = jdbcTemplate.queryForObject(
                        "SELECT `信用分` FROM `校园卡用户表` WHERE `校园卡号` = ?", Integer.class, cardId);
                String stuNo = jdbcTemplate.queryForObject(
                        "SELECT `学号` FROM `校园卡用户表` WHERE `校园卡号` = ?", String.class, cardId);
                jdbcTemplate.update(
                        "INSERT INTO `信用积分记录表` (`学号`, `变动分数`, `变动原因`, `变动后分数`) " +
                                "VALUES (?, ?, '信用修复任务通过', ?)",
                        stuNo, addScore, newScore);
            }

            res.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    @GetMapping("/my-tasks")
    public Map<String, Object> myTasks(@RequestParam String cardId) {
        Map<String, Object> res = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT t.`任务ID`, t.`加分值`, t.`提交照片`, t.`提交时间`, t.`审核状态`, t.`审核备注`, " +
                            "       ty.`任务名称` " +
                            "FROM `信用修复任务表` t " +
                            "LEFT JOIN `任务类型表` ty ON t.`任务类型` = ty.`类型ID` " +
                            "WHERE t.`校园卡号` = ? ORDER BY t.`任务ID` DESC LIMIT 50",
                    cardId);
            res.put("code", 200);
            res.put("data", list);
        } catch (Exception e) {
            e.printStackTrace();
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
            int taskTypeId = ((Number) body.get("taskTypeId")).intValue();
            String photoUrl = (String) body.getOrDefault("photoUrl", "");
            Object lat = body.getOrDefault("gpsLat", 0);
            Object lng = body.getOrDefault("gpsLng", 0);
            String remark = (String) body.getOrDefault("remark", "");

            Integer addScore = jdbcTemplate.queryForObject(
                    "SELECT `加分值` FROM `任务类型表` WHERE `类型ID` = ?", Integer.class, taskTypeId);

            jdbcTemplate.update(
                    "INSERT INTO `信用修复任务表` (`校园卡号`, `任务类型`, `加分值`, `提交照片`, `GPS经度`, `GPS纬度`, `审核状态`, `审核备注`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, '待审核', ?)",
                    cardId, taskTypeId, addScore, photoUrl, lng, lat, remark);

            res.put("code", 200);
            res.put("msg", "提交成功");
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
}