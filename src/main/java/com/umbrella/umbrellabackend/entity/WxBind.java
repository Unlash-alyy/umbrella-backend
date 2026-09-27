package com.umbrella.umbrellabackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("`微信绑定表`")
public class WxBind {
    @TableId(value = "`绑定ID`", type = IdType.AUTO)
    private Integer bindId;

    @TableField("`微信openid`")
    private String openid;

    @TableField("`校园卡号`")
    private String cardId;

    @TableField("`绑定时间`")
    private Date bindTime;
}