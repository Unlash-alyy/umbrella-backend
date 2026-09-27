package com.umbrella.umbrellabackend.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.umbrella.umbrellabackend.entity.Order;
import com.umbrella.umbrellabackend.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CreditScheduler {

    @Autowired private OrderMapper orderMapper;

    @Scheduled(fixedDelay = 600000)
    public void checkOverdue() {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.eq("`记录状态`", 0);
        List<Order> borrowing = orderMapper.selectList(qw);

        long now = System.currentTimeMillis();
        for (Order order : borrowing) {
            long hours = (now - order.getBorrowTime().getTime()) / (1000 * 60 * 60);
            if (hours >= 24) {
                System.out.println("【逾期提醒】卡号=" + order.getCardId()
                        + " 已借用" + hours + "小时");
            }
            if (hours >= 72 && order.getStatus() == 0) {
                order.setStatus(2);
                orderMapper.updateById(order);
            }
        }
    }
}