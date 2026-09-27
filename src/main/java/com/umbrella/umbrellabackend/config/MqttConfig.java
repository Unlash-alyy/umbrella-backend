package com.umbrella.umbrellabackend.config;

import com.umbrella.umbrellabackend.entity.User;
import com.umbrella.umbrellabackend.service.CreditService;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PreDestroy;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}") private String broker;
    @Value("${mqtt.client-id}") private String clientId;
    @Value("${mqtt.topic-event}") private String topicEvent;

    @Autowired private CreditService creditService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MqttClient mqttClient;

    @Bean
    public MqttClient mqttClient() {
        try {
            mqttClient = new MqttClient(broker, clientId + "-" + (System.currentTimeMillis() % 1000000), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);
            mqttClient.connect(options);

            mqttClient.subscribe(topicEvent, (topic, msg) -> {
                handleMessage(new String(msg.getPayload(), "GBK"));
            });

            System.out.println("MQTT连接成功: " + broker);
        } catch (Exception e) {
            System.err.println("===== MQTT连接失败 =====");
            System.err.println("错误: " + e.getMessage());
        }
        return mqttClient;
    }

    private void handleMessage(String payload) {
        try {
            System.out.println("\n【MQTT收到】" + payload);

            if (payload.startsWith("card=")) handleCard(payload);
            else if (payload.startsWith("stu_no=")) handleLogin(payload);
            else if (payload.startsWith("login_confirmed=")) handleLoginConfirmed(payload);
            else if (payload.startsWith("do_borrow=")) handleDoBorrow(payload);
            else if (payload.startsWith("do_return=")) handleDoReturn(payload);
            else if (payload.startsWith("admin=")) handleAdmin(payload);
            else if (payload.startsWith("system=reset")) handleReset();
            else System.out.println("【未知消息类型】" + payload);
        } catch (Exception e) {
            System.err.println("解析MQTT失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleReset() {
        System.out.println("【系统重置】收到 STM32 上电通知，清库");
        try {
            jdbcTemplate.update("DELETE FROM `借还记录表`");
            jdbcTemplate.update("DELETE FROM `信用积分记录表`");
            jdbcTemplate.update("UPDATE `伞位状态表` SET `占用状态`=0, `剩余数量`=2, `更新时间`=NOW() WHERE `站点ID`=1");
            jdbcTemplate.update("UPDATE `校园卡用户表` SET `信用分`=100, `是否黑名单`=0");
            jdbcTemplate.update("DELETE FROM `微信绑定表`");
            System.out.println("【系统重置】完成");
        } catch (Exception e) {
            System.err.println("【系统重置】失败: " + e.getMessage());
        }
    }

    private void handleCard(String payload) {
        String[] parts = payload.split("&");
        String card = parts[0].split("=")[1];
        int slot = Integer.parseInt(parts[2].split("=")[1]);

        String realAction, result;
        if (creditService.hasUnreturnedOrder(card)) {
            int realSlot = creditService.getUnreturnedSlot(card);
            if (realSlot <= 0) realSlot = slot;
            realAction = "return";
            result = creditService.returnUmbrella(card, "DEV_001", realSlot);
            slot = realSlot;
        } else {
            realAction = "borrow";
            result = creditService.borrowUmbrella(card, "DEV_001", slot);
        }

        User u = creditService.findByCardId(card);
        String stuNo = (u != null && u.getStuNo() != null) ? u.getStuNo() : card;
        publishResult(stuNo, realAction, result, slot);
    }

    private void handleLogin(String payload) {
        String[] parts = payload.split("&");
        String stuNo = parts[0].split("=")[1];
        String pwd = parts[1].split("=")[1];

        User u = creditService.findByStuNo(stuNo);
        if (u == null) { publishFail2(stuNo, "login", "学号不存在"); return; }
        if (u.getLoginPwd() == null || !u.getLoginPwd().equals(pwd)) {
            publishFail2(stuNo, "login", "密码错误"); return;
        }

        String card = u.getCardId();
        String action = creditService.hasUnreturnedOrder(card) ? "return" : "borrow";
        String name = u.getName() == null ? "用户" : u.getName();
        String credit = String.valueOf(u.getCreditScore());

        String response = "stu_no=" + stuNo
                + "&name=" + name
                + "&credit=" + credit
                + "&action=" + action
                + "&status=OK&stage=identity";
        publish("device/001/command", response);
        System.out.println("【下发STM32】" + response);
    }

    private void handleLoginConfirmed(String payload) {
        String[] parts = payload.split("&");
        String stuNo = parts[0].split("=")[1];
        String action = parts[1].split("=")[1];

        int s1 = creditService.getSlotStatus(1);
        int s2 = creditService.getSlotStatus(2);

        String response = "stu_no=" + stuNo
                + "&action=" + action
                + "&slot1=" + s1
                + "&slot2=" + s2
                + "&status=OK&stage=slots";
        publish("device/001/command", response);
        System.out.println("【下发STM32】" + response);
    }

    private void handleDoBorrow(String payload) {
        String[] parts = payload.split("&");
        String stuNo = parts[0].split("=")[1];
        int slot = Integer.parseInt(parts[1].split("=")[1]);

        User u = creditService.findByStuNo(stuNo);
        if (u == null) { publishFail2(stuNo, "borrow", "学号不存在"); return; }

        String result = creditService.borrowUmbrella(u.getCardId(), "DEV_001", slot);
        publishResult(stuNo, "borrow", result, slot);
    }

    private void handleDoReturn(String payload) {
        String[] parts = payload.split("&");
        String stuNo = parts[0].split("=")[1];
        int slot = Integer.parseInt(parts[1].split("=")[1]);

        User u = creditService.findByStuNo(stuNo);
        if (u == null) { publishFail2(stuNo, "return", "学号不存在"); return; }

        int realSlot = creditService.getUnreturnedSlot(u.getCardId());
        if (realSlot <= 0) realSlot = slot;

        String result = creditService.returnUmbrella(u.getCardId(), "DEV_001", realSlot);
        publishResult(stuNo, "return", result, realSlot);
    }

    private void handleAdmin(String payload) {
        String[] parts = payload.split("&");
        String op = parts[0].split("=")[1];
        System.out.println("【管理员操作】" + payload);

        if ("query".equals(op)) {
            int s1 = creditService.getSlotStatus(1);
            int s2 = creditService.getSlotStatus(2);
            String response = "slot1=" + s1 + "&slot2=" + s2
                    + "&status=OK&stage=slots_admin";
            publish("device/001/command", response);
            System.out.println("【下发STM32】" + response);
            return;
        }

        // ★ 订单：3 条
        if ("orders".equals(op)) {
            java.util.List<com.umbrella.umbrellabackend.entity.Order> list =
                    creditService.getRecentOrders(3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                com.umbrella.umbrellabackend.entity.Order o = list.get(i);
                String stat = (o.getStatus() == 0) ? "借用中" : "已归还";
                sb.append("&line").append(i + 1).append("=")
                        .append(o.getCardId()).append(" ")
                        .append(o.getSlotNo()).append("号位 ")
                        .append(stat);
            }
            String response = "stage=orders&status=OK&count=" + list.size() + sb.toString();
            publish("device/001/command", response);
            System.out.println("【下发STM32】" + response);
            return;
        }

        // ★ 用户：3 条，去掉"信用分"
        if ("users".equals(op)) {
            java.util.List<com.umbrella.umbrellabackend.entity.User> list =
                    creditService.getAllUsers();
            StringBuilder sb = new StringBuilder();
            int cnt = Math.min(list.size(), 3);
            for (int i = 0; i < cnt; i++) {
                com.umbrella.umbrellabackend.entity.User u = list.get(i);
                sb.append("&line").append(i + 1).append("=")
                        .append(u.getStuNo() == null ? "无学号" : u.getStuNo()).append(" ")
                        .append(u.getName() == null ? "无姓名" : u.getName());
            }
            String response = "stage=users&status=OK&count=" + cnt + sb.toString();
            publish("device/001/command", response);
            System.out.println("【下发STM32】" + response);
            return;
        }

        if ("open".equals(op)) {
            int slot = Integer.parseInt(parts[1].split("=")[1]);
            String r = creditService.adminOpenSlot(slot);
            System.out.println("【管理员开锁】slot=" + slot + " 结果=" + r);
        }
    }

    private void publishResult(String stuNo, String action, String result, int slot) {
        User u = creditService.findByStuNo(stuNo);
        if (u == null) u = creditService.findByCardId(stuNo);

        String name = (u != null && u.getName() != null) ? u.getName() : "用户";
        String credit = (u != null) ? String.valueOf(u.getCreditScore()) : "100";

        String status = (result != null && result.contains("成功")) ? "OK" : "FAIL";
        String reason = "OK".equals(status) ? "" : (result != null ? result : "未知错误");

        String response = "stu_no=" + stuNo
                + "&name=" + name
                + "&credit=" + credit
                + "&action=" + action
                + "&status=" + status
                + "&slot=" + slot
                + "&stage=result";
        if ("FAIL".equals(status)) response += "&reason=" + reason;

        publish("device/001/command", response);
        System.out.println("【下发STM32】" + response);
    }

    private void publishFail2(String stuNo, String action, String reason) {
        String response = "stu_no=" + stuNo
                + "&name=&credit="
                + "&action=" + action
                + "&status=FAIL&stage=identity"
                + "&reason=" + reason;
        publish("device/001/command", response);
        System.out.println("【下发STM32】" + response);
    }

    public void publish(String topic, String payload) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage msg = new MqttMessage(payload.getBytes("GBK"));
                msg.setQos(1);
                mqttClient.publish(topic, msg);
            }
        } catch (Exception e) {
            System.err.println("MQTT下发失败: " + e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) mqttClient.disconnect();
        } catch (MqttException ignored) {}
    }
}