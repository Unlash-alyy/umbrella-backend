package com.umbrella.umbrellabackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("校园卡用户表")
public class User {
    @TableId(value = "用户ID", type = IdType.AUTO)
    private Integer userId;

    @TableField("用户类型")
    private String userType;    // student / teacher

    @TableField("openid")
    private String openid;

    @TableField("校园卡号")
    private String cardId;

    @TableField("学号")
    private String stuNo;

    @TableField("姓名")
    private String name;

    @TableField("手机号")
    private String phone;

    @TableField("登录密码")
    private String loginPwd;

    @TableField("信用分")
    private Integer creditScore;

    @TableField("是否黑名单")
    private Integer isBlacklisted;

    @TableField("冻结结束时间")
    private Date freezeEndTime;

    @TableField("创建时间")
    private Date createTime;
}