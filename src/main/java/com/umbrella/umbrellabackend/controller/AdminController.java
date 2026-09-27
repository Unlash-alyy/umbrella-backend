package com.umbrella.umbrellabackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private JdbcTemplate jdbcTemplate;

    // ==================== 登录 ====================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        String username = body.get("username");
        String password = body.get("password");
        if ("admin".equals(username) && "admin123".equals(password)) {
            res.put("code", 200);
            res.put("token", "admin-token-2026");
            res.put("msg", "登录成功");
        } else {
            res.put("code", 401);
            res.put("msg", "账号或密码错误");
        }
        return res;
    }

    // ==================== 数据统计 ====================
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> res = new HashMap<>();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("userCount", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `校园卡用户表`", Integer.class));
            data.put("todayBorrow", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `借还记录表` WHERE DATE(`借出时间`) = CURDATE()", Integer.class));
            data.put("activeBorrow", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `借还记录表` WHERE `记录状态` = 0", Integer.class));
            data.put("pendingTask", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `信用修复任务表` WHERE `审核状态` = '待审核'", Integer.class));
            data.put("pendingAppeal", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `申诉记录表` WHERE `申诉状态` = '待处理'", Integer.class));
            data.put("blacklistCount", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `校园卡用户表` WHERE `是否黑名单` = 1", Integer.class));
            res.put("code", 200);
            res.put("data", data);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    // ==================== 用户列表 ====================
    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(required = false) String keyword) {
        Map<String, Object> res = new HashMap<>();
        try {
            String sql = "SELECT `用户ID`, `校园卡号`, `学号`, `姓名`, `手机号`, `信用分`, `是否黑名单`, `用户类型`, `创建时间` " +
                    "FROM `校园卡用户表`";
            List<Map<String, Object>> list;
            if (keyword != null && !keyword.isEmpty()) {
                sql += " WHERE `学号` LIKE ? OR `姓名` LIKE ? OR `校园卡号` LIKE ? ORDER BY `用户ID` DESC LIMIT 100";
                list = jdbcTemplate.queryForList(sql,
                        "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
            } else {
                sql += " ORDER BY `用户ID` DESC LIMIT 100";
                list = jdbcTemplate.queryForList(sql);
            }
            res.put("code", 200);
            res.put("data", list);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    // ==================== 拉黑/解封 ====================
    @PostMapping("/user/blacklist")
    public Map<String, Object> toggleBlacklist(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            String cardId = (String) body.get("cardId");
            int blacklisted = ((Number) body.get("blacklisted")).intValue();
            jdbcTemplate.update(
                    "UPDATE `校园卡用户表` SET `是否黑名单` = ? WHERE `校园卡号` = ?",
                    blacklisted, cardId);
            res.put("code", 200);
            res.put("msg", blacklisted == 1 ? "已拉黑" : "已解封");
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    // ==================== 近7天借出趋势 ====================
    @GetMapping("/trend")
    public Map<String, Object> trend() {
        Map<String, Object> res = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT DATE(`借出时间`) AS d, COUNT(*) AS c " +
                            "FROM `借还记录表` " +
                            "WHERE `借出时间` >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
                            "GROUP BY DATE(`借出时间`) ORDER BY d");
            res.put("code", 200);
            res.put("data", list);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    // ==================== 借还记录 ====================
    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> res = new HashMap<>();
        try {
            int offset = (page - 1) * size;
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT o.`记录ID`, o.`学号`, o.`校园卡号`, o.`伞位编号`, " +
                            "       o.`借出时间`, o.`归还时间`, o.`记录状态`, o.`信用变动`, u.`姓名` " +
                            "FROM `借还记录表` o LEFT JOIN `校园卡用户表` u ON o.`校园卡号` = u.`校园卡号` " +
                            "ORDER BY o.`记录ID` DESC LIMIT ? OFFSET ?",
                    size, offset);
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `借还记录表`", Integer.class);
            res.put("code", 200);
            res.put("data", list);
            res.put("total", total);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("msg", e.getMessage());
        }
        return res;
    }
}