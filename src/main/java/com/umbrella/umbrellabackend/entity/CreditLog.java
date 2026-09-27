package com.umbrella.umbrellabackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("信用积分记录表")
public class CreditLog {
    @TableId(value = "记录ID", type = IdType.AUTO)
    private Integer id;
    @TableField("校园卡号")   private String cardId;
    @TableField("关联订单ID") private Integer orderId;
    @TableField("积分变动")   private Integer changeAmount;
    @TableField("变动原因")   private String reason;
    @TableField("记录时间")   private Date createTime;
}