package com.umbrella.umbrellabackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.umbrella.umbrellabackend.entity.User;
import com.umbrella.umbrellabackend.entity.WxBind;
import com.umbrella.umbrellabackend.mapper.UserMapper;
import com.umbrella.umbrellabackend.mapper.WxBindMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired private UserMapper userMapper;
    @Autowired private WxBindMapper wxBindMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping("/credit/{cardId}")
    public Map<String, Object> getCredit(@PathVariable String cardId) {
        Map<String, Object> result = new HashMap<>();
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("`校园卡号`", cardId);
        User user = userMapper.selectOne(qw);
        if (user == null) {
            result.put("code", 404);
            result.put("msg", "用户不存在");
        } else {
            result.put("code", 200);
            result.put("data", user);
        }
        return result;
    }

    @PostMapping("/bind")
    public Map<String, Object> bindWx(@RequestParam String openid, @RequestParam String cardId) {
        Map<String, Object> result = new HashMap<>();

        WxBind exist = wxBindMapper.findByOpenid(openid);
        if (exist != null) {
            result.put("code", 400);
            result.put("msg", "该微信已绑定校园卡：" + exist.getCardId());
            return result;
        }

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("`校园卡号`", cardId);
        User user = userMapper.selectOne(qw);
        if (user == null) {
            result.put("code", 404);
            result.put("msg", "校园卡不存在");
            return result;
        }

        WxBind bind = new WxBind();
        bind.setOpenid(openid);
        bind.setCardId(cardId);
        bind.setBindTime(new Date());
        wxBindMapper.insert(bind);

        result.put("code", 200);
        result.put("msg", "绑定成功");
        return result;
    }

    @GetMapping("/wxinfo")
    public Map<String, Object> getWxInfo(@RequestParam String openid) {
        Map<String, Object> result = new HashMap<>();
        WxBind bind = wxBindMapper.findByOpenid(openid);
        if (bind == null) {
            result.put("code", 404);
            result.put("msg", "未绑定校园卡");
        } else {
            result.put("code", 200);
            result.put("cardId", bind.getCardId());

            QueryWrapper<User> qw = new QueryWrapper<>();
            qw.eq("`校园卡号`", bind.getCardId());
            User user = userMapper.selectOne(qw);
            result.put("data", user);
        }
        return result;
    }

    // ==================== 学号密码登录 ====================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        String stuNo = body.get("stuNo");
        String pwd = body.get("pwd");

        if (stuNo == null || pwd == null) {
            result.put("code", 400);
            result.put("msg", "学号和密码不能为空");
            return result;
        }

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("`学号`", stuNo);
        User user = userMapper.selectOne(qw);

        if (user == null) {
            result.put("code", 404);
            result.put("msg", "学号不存在");
            return result;
        }

        if (!pwd.equals(user.getLoginPwd())) {
            result.put("code", 401);
            result.put("msg", "密码错误");
            return result;
        }

        if (user.getIsBlacklisted() != null && user.getIsBlacklisted() == 1) {
            result.put("code", 403);
            result.put("msg", "账号已被拉黑");
            return result;
        }

        result.put("code", 200);
        result.put("msg", "登录成功");
        result.put("data", user);
        return result;
    }

    // ==================== 【新增】获取用户完整信息 ====================
    @GetMapping("/info")
    public Map<String, Object> info(@RequestParam String cardId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 查用户
            QueryWrapper<User> uqw = new QueryWrapper<>();
            uqw.eq("`校园卡号`", cardId).last("LIMIT 1");
            User user = userMapper.selectOne(uqw);
            if (user == null) {
                result.put("code", 404);
                result.put("msg", "用户不存在");
                return result;
            }

            // 2. 累计借伞次数
            Integer borrowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `借还记录表` WHERE `校园卡号` = ?", Integer.class, cardId);

            // 3. 累计丢失次数（如果没这个字段，就先返回 0）
            Integer lostCount = 0;
            try {
                lostCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `借还记录表` WHERE `校园卡号` = ? AND `是否丢失` = 1",
                        Integer.class, cardId);
            } catch (Exception ignored) {}

            // 4. 当前借伞（未归还）
            List<Map<String, Object>> currentList = jdbcTemplate.queryForList(
                    "SELECT `伞位编号`, `借出时间`, `应还时间` FROM `借还记录表` " +
                            "WHERE `校园卡号` = ? AND `记录状态` = 0 ORDER BY `记录ID` DESC LIMIT 1", cardId);

            Map<String, Object> data = new HashMap<>();
            data.put("cardId", user.getCardId());
            data.put("stuNo", user.getStuNo());
            data.put("name", user.getName());
            data.put("creditScore", user.getCreditScore());
            data.put("borrowCount", borrowCount == null ? 0 : borrowCount);
            data.put("lostCount", lostCount == null ? 0 : lostCount);

            if (!currentList.isEmpty()) {
                Map<String, Object> c = currentList.get(0);
                Map<String, Object> cb = new HashMap<>();
                cb.put("umbrellaNo", c.get("伞位编号"));
                cb.put("borrowTime", c.get("借出时间"));
                cb.put("dueTime", c.get("应还时间"));
                data.put("currentBorrow", cb);
            } else {
                data.put("currentBorrow", null);
            }

            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }
}