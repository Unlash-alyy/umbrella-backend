package com.umbrella.umbrellabackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("借还记录表")
public class Order {
    @TableId(value = "记录ID", type = IdType.AUTO)
    private Integer orderId;
    @TableField("学号")
    private String stuNo;

    @TableField("应还时间")
    private Date dueTime;

    @TableField("是否超时")
    private Integer isOverdue;

    @TableField("是否丢失")
    private Integer isLost;
    @TableField("订单号")   private String orderNo;
    @TableField("校园卡号") private String cardId;
    @TableField("站点编号") private String siteId;
    @TableField("伞位编号") private Integer slotNo;
    @TableField("借出时间") private Date borrowTime;
    @TableField("归还时间") private Date returnTime;
    @TableField("借用时长") private Integer durationHours;
    @TableField("信用变动") private Integer creditChange;
    @TableField("记录状态") private Integer status;
    @TableField("创建时间") private Date createTime;
}