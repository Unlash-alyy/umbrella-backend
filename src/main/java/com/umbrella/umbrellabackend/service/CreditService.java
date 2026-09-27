package com.umbrella.umbrellabackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.umbrella.umbrellabackend.entity.CreditLog;
import com.umbrella.umbrellabackend.entity.Order;
import com.umbrella.umbrellabackend.entity.Slot;
import com.umbrella.umbrellabackend.entity.User;
import com.umbrella.umbrellabackend.mapper.CreditLogMapper;
import com.umbrella.umbrellabackend.mapper.OrderMapper;
import com.umbrella.umbrellabackend.mapper.SlotMapper;
import com.umbrella.umbrellabackend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class CreditService {

    @Autowired private UserMapper userMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private SlotMapper slotMapper;
    @Autowired private CreditLogMapper creditLogMapper;

    public int calculateCreditChange(long hours) {
        if (hours <= 24) return 3;
        else if (hours <= 48) return 1;
        else if (hours <= 72) return 0;
        else if (hours <= 96) return -10;
        else if (hours <= 120) return -20;
        else return -30;
    }

    public boolean hasUnreturnedOrder(String cardId) {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.eq("`校园卡号`", cardId).eq("`记录状态`", 0);
        return orderMapper.selectCount(qw) > 0;
    }

    public int getUnreturnedSlot(String cardId) {
        Order o = findUnreturnedOrder(cardId);
        return o != null ? o.getSlotNo() : -1;
    }

    private Order findUnreturnedOrder(String cardId) {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.eq("`校园卡号`", cardId).eq("`记录状态`", 0);
        qw.orderByAsc("`记录ID`").last("LIMIT 1");
        return orderMapper.selectOne(qw);
    }

    /** 返回伞位剩余伞数 */
    public int getSlotStatus(int slotNo) {
        QueryWrapper<Slot> qw = new QueryWrapper<>();
        qw.eq("`站点ID`", 1).eq("`伞位编号`", slotNo);
        Slot slot = slotMapper.selectOne(qw);
        if (slot == null || slot.getRemainCount() == null) return 0;
        return slot.getRemainCount();
    }

    public User findByStuNo(String stuNo) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("`学号`", stuNo);
        return userMapper.selectOne(qw);
    }

    public User findByCardId(String cardId) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("`校园卡号`", cardId).last("LIMIT 1");
        return userMapper.selectOne(qw);
    }

    public java.util.List<Order> getRecentOrders(int limit) {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.orderByDesc("`记录ID`").last("LIMIT " + limit);
        return orderMapper.selectList(qw);
    }

    public java.util.List<User> getAllUsers() {
        return userMapper.selectList(null);
    }

    public int getFreeCount() {
        return getSlotStatus(1) + getSlotStatus(2);
    }

    /** 管理员开锁：从该位拿 1 把 */
    @Transactional
    public String adminOpenSlot(int slotNo) {
        QueryWrapper<Slot> qw = new QueryWrapper<>();
        qw.eq("`站点ID`", 1).eq("`伞位编号`", slotNo);
        Slot s = slotMapper.selectOne(qw);
        if (s == null) return "伞位不存在";
        int rc = (s.getRemainCount() != null ? s.getRemainCount() : 0);
        if (rc <= 0) return "该伞位已借完";
        s.setRemainCount(rc - 1);
        s.setStatus((rc - 1) == 0 ? 1 : 0);
        s.setUpdateTime(new Date());
        slotMapper.updateById(s);
        return "开锁成功";
    }

    @Transactional
    public String borrowUmbrella(String cardId, String siteId, int slotNo) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("`校园卡号`", cardId);
        User user = userMapper.selectOne(qw);

        if (user == null) {
            user = new User();
            user.setCardId(cardId);
            user.setName("用户" + cardId.substring(0, Math.min(4, cardId.length())));
            user.setCreditScore(100);
            user.setIsBlacklisted(0);
            user.setCreateTime(new Date());
            userMapper.insert(user);
        }

        if (user.getIsBlacklisted() != null && user.getIsBlacklisted() == 1) {
            return "您已被拉黑，请联系管理员";
        }

        if (findUnreturnedOrder(cardId) != null) {
            return "您已有未归还的雨伞";
        }

        // ★ 用剩余数量判断，不用占用状态
        QueryWrapper<Slot> chkQw = new QueryWrapper<>();
        chkQw.eq("`站点ID`", 1).eq("`伞位编号`", slotNo);
        Slot chkSlot = slotMapper.selectOne(chkQw);
        if (chkSlot == null) return "伞位不存在";

        int remain = (chkSlot.getRemainCount() != null ? chkSlot.getRemainCount() : 0);
        if (remain <= 0) return "该伞位已借完";

        // 生成订单
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis());
        order.setCardId(cardId);
        order.setSiteId(siteId);
        order.setSlotNo(slotNo);
        order.setBorrowTime(new Date());
        order.setStatus(0);
        order.setCreateTime(new Date());
        orderMapper.insert(order);

        // 剩余 -1
        int newRemain = remain - 1;
        chkSlot.setRemainCount(newRemain);
        chkSlot.setStatus(newRemain == 0 ? 1 : 0);
        chkSlot.setUpdateTime(new Date());
        slotMapper.updateById(chkSlot);

        System.out.println("【借伞成功】卡号=" + cardId + " 伞位=" + slotNo + " 剩余=" + newRemain);
        return "借伞成功";
    }

    @Transactional
    public String returnUmbrella(String cardId, String siteId, int slotNo) {
        Order order = findUnreturnedOrder(cardId);
        if (order == null) return "未找到借伞记录";

        long hours = (System.currentTimeMillis() - order.getBorrowTime().getTime()) / (1000 * 60 * 60);
        int change = calculateCreditChange(hours);

        order.setReturnTime(new Date());
        order.setDurationHours((int) hours);
        order.setCreditChange(change);
        order.setStatus(1);
        orderMapper.updateById(order);

        QueryWrapper<User> userQw = new QueryWrapper<>();
        userQw.eq("`校园卡号`", cardId);
        User user = userMapper.selectOne(userQw);
        if (user != null) {
            int newScore = user.getCreditScore() + change;
            user.setCreditScore(newScore);
            if (newScore < 60) user.setIsBlacklisted(1);
            userMapper.updateById(user);
        }

        CreditLog log = new CreditLog();
        log.setCardId(cardId);
        log.setOrderId(order.getOrderId());
        log.setChangeAmount(change);
        log.setReason(hours <= 24 ? "按时归还" : "超时归还(" + hours + "小时)");
        log.setCreateTime(new Date());
        creditLogMapper.insert(log);

        // 剩余 +1
        QueryWrapper<Slot> slotQw = new QueryWrapper<>();
        slotQw.eq("`站点ID`", 1).eq("`伞位编号`", slotNo);
        Slot slot = slotMapper.selectOne(slotQw);
        if (slot != null) {
            int rc = (slot.getRemainCount() != null ? slot.getRemainCount() : 0) + 1;
            slot.setRemainCount(rc);
            slot.setStatus(0);
            slot.setUpdateTime(new Date());
            slotMapper.updateById(slot);
        }

        System.out.println("【还伞成功】卡号=" + cardId + " 时长=" + hours + "h 信用变动=" + change);
        return change >= 0 ? "还伞成功，信用分+" + change : "还伞成功，信用分" + change;
    }
}