package com.umbrella.umbrellabackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.umbrella.umbrellabackend.config.MqttConfig;
import com.umbrella.umbrellabackend.entity.Order;
import com.umbrella.umbrellabackend.entity.User;
import com.umbrella.umbrellabackend.mapper.OrderMapper;
import com.umbrella.umbrellabackend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired private OrderMapper orderMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MqttConfig mqttConfig;

    // ==================== 订单列表 ====================
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String cardId) {
        Map<String, Object> result = new HashMap<>();
        QueryWrapper<Order> qw = new QueryWrapper<>();
        if (cardId != null && !cardId.isEmpty()) {
            qw.eq("`校园卡号`", cardId);
        }
        qw.orderByDesc("`记录ID`");
        List<Order> list = orderMapper.selectList(qw);
        result.put("code", 200);
        result.put("data", list);
        return result;
    }

    // ==================== 查伞位状态 ====================
    @GetMapping("/slots")
    public Map<String, Object> getSlots() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT `伞位编号`, `占用状态`, `剩余数量`, `锁状态`, `传感器状态` " +
                            "FROM `伞位状态表` WHERE `站点编号` = '1' ORDER BY `伞位编号`");
            result.put("code", 200);
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    // ==================== 信用记录 ====================
    @GetMapping("/credit-log")
    public Map<String, Object> creditLog(@RequestParam String cardId,
                                         @RequestParam(required = false) String stuNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (stuNo == null || stuNo.isEmpty()) {
                QueryWrapper<User> uqw = new QueryWrapper<>();
                uqw.eq("`校园卡号`", cardId).last("LIMIT 1");
                User user = userMapper.selectOne(uqw);
                if (user != null) stuNo = user.getStuNo();
            }

            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT `变动分数`, `变动原因`, `变动后分数`, `创建时间` " +
                            "FROM `信用积分记录表` WHERE `学号` = ? ORDER BY `记录ID` DESC LIMIT 50",
                    stuNo);

            result.put("code", 200);
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    // ==================== 借伞 ====================
    @PostMapping("/borrow")
    public Map<String, Object> borrow(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String stuNo = (String) body.get("stuNo");
            Integer slotNo = ((Number) body.get("slotNo")).intValue();

            // 1. 查用户
            QueryWrapper<User> uqw = new QueryWrapper<>();
            uqw.eq("`学号`", stuNo);
            User user = userMapper.selectOne(uqw);
            if (user == null) {
                result.put("code", 404);
                result.put("msg", "用户不存在");
                return result;
            }

            // 2. 检查信用分
            if (user.getCreditScore() == null || user.getCreditScore() < 60) {
                result.put("code", 403);
                result.put("msg", "信用分不足60，暂停借伞");
                return result;
            }

            // 3. 检查黑名单
            if (user.getIsBlacklisted() != null && user.getIsBlacklisted() == 1) {
                result.put("code", 403);
                result.put("msg", "账号已被拉黑");
                return result;
            }

            // 4. 检查是否有未归还订单
            QueryWrapper<Order> oqw = new QueryWrapper<>();
            oqw.eq("`学号`", stuNo).eq("`记录状态`", 0);
            if (orderMapper.selectCount(oqw) > 0) {
                result.put("code", 400);
                result.put("msg", "您有未归还的雨伞");
                return result;
            }

            // 5. 检查伞位是否有伞
            List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                    "SELECT * FROM `伞位状态表` WHERE `伞位编号` = ?", slotNo);
            if (slots.isEmpty()) {
                result.put("code", 404);
                result.put("msg", "伞位不存在");
                return result;
            }
            Map<String, Object> slot = slots.get(0);
            Integer zyzt = (Integer) slot.get("占用状态");
            if (zyzt == null || zyzt == 0) {
                result.put("code", 400);
                result.put("msg", slotNo + "号位无伞可借");
                return result;
            }

            // 6. 创建借出记录
            String orderNo = "ORD" + System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 48);
            Date dueTime = cal.getTime();

            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setStuNo(stuNo);
            order.setCardId(user.getCardId());
            order.setSiteId("1");
            order.setSlotNo(slotNo);
            order.setBorrowTime(new Date());
            order.setDueTime(dueTime);
            order.setStatus(0);
            order.setIsOverdue(0);
            order.setIsLost(0);
            orderMapper.insert(order);

            // 7.1 更新伞位状态（真正要做的事）
            jdbcTemplate.update(
                    "UPDATE `伞位状态表` SET `占用状态` = 0, `剩余数量` = `剩余数量` - 1, `更新时间` = NOW() " +
                            "WHERE `伞位编号` = ?",
                    slotNo);

            // 7.2 记录信用变动（借出，0分）
            jdbcTemplate.update(
                    "INSERT INTO `信用积分记录表` (`学号`, `变动分数`, `变动原因`, `变动后分数`, `关联记录ID`) " +
                            "VALUES (?, 0, '借出雨伞', ?, ?)",
                    stuNo, user.getCreditScore(), order.getOrderId());

            // 8. MQTT 通知 STM32 开锁
            mqttConfig.publish("device/001/command",
                    "cmd=unlock&slot=" + slotNo + "&stu_no=" + stuNo);

            result.put("code", 200);
            result.put("msg", "借伞成功");
            result.put("orderNo", orderNo);
            result.put("slotNo", slotNo);
            result.put("dueTime", dueTime);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "服务器错误：" + e.getMessage());
        }
        return result;
    }

    // ==================== 还伞 ====================
    @PostMapping("/doReturn")
    public Map<String, Object> returnUmbrella(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String stuNo = (String) body.get("stuNo");
            Integer slotNo = ((Number) body.get("slotNo")).intValue();

            // 1. 查未归还订单
            QueryWrapper<Order> qw = new QueryWrapper<>();
            qw.eq("`学号`", stuNo).eq("`记录状态`", 0);
            qw.orderByDesc("`借出时间`").last("LIMIT 1");
            Order order = orderMapper.selectOne(qw);

            if (order == null) {
                result.put("code", 400);
                result.put("msg", "您没有未归还的雨伞");
                return result;
            }

            // 2. 检查伞位是否空（占用状态 0 = 空位，可以还）
            List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                    "SELECT * FROM `伞位状态表` WHERE `伞位编号` = ?", slotNo);
            if (slots.isEmpty()) {
                result.put("code", 404);
                result.put("msg", "伞位不存在");
                return result;
            }
            Integer zyzt = (Integer) slots.get(0).get("占用状态");
            if (zyzt != null && zyzt == 1) {
                result.put("code", 400);
                result.put("msg", slotNo + "号位已有伞");
                return result;
            }

            // 3. 计算时长
            long hours = (System.currentTimeMillis()
                    - order.getBorrowTime().getTime()) / (1000 * 60 * 60);

            // 4. 计算信用分变动
            int creditChange;
            String reason;
            if (hours < 48) {
                creditChange = 1;
                reason = "按时归还";
            } else if (hours < 24 * 7) {
                creditChange = -1;
                reason = "超时归还";
            } else {
                creditChange = -5;
                reason = "严重超时";
            }

            // 5. 更新订单
            order.setReturnTime(new Date());
            order.setDurationHours((int) hours);
            order.setCreditChange(creditChange);
            order.setStatus(1);
            order.setIsOverdue(hours >= 48 ? 1 : 0);
            orderMapper.updateById(order);

            // 6. 更新用户信用分
            QueryWrapper<User> uqw = new QueryWrapper<>();
            uqw.eq("`学号`", stuNo);
            User user = userMapper.selectOne(uqw);
            int newCredit = user.getCreditScore() + creditChange;
            if (newCredit > 100) newCredit = 100;
            if (newCredit < 0) newCredit = 0;
            user.setCreditScore(newCredit);

            if (newCredit < 30) {
                user.setIsBlacklisted(1);
            } else if (newCredit < 60) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, 30);
                user.setFreezeEndTime(cal.getTime());
            }
            userMapper.updateById(user);

            // 7. 记录信用变动
            jdbcTemplate.update(
                    "INSERT INTO `信用积分记录表` (`学号`,`变动分数`,`变动原因`,`变动后分数`,`关联记录ID`) " +
                            "VALUES (?,?,?,?,?)",
                    stuNo, creditChange, reason, newCredit, order.getOrderId());

            // 8. 更新伞位状态（还伞：占用状态变1，剩余数量+1）
            jdbcTemplate.update(
                    "UPDATE `伞位状态表` SET `占用状态` = 1, `剩余数量` = `剩余数量` + 1, `更新时间` = NOW() " +
                            "WHERE `伞位编号` = ?",
                    slotNo);

            result.put("code", 200);
            result.put("msg", "还伞成功");
            result.put("creditChange", creditChange);
            result.put("newCredit", newCredit);
            result.put("hours", hours);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "服务器错误：" + e.getMessage());
        }
        return result;
    }
}