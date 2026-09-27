package com.umbrella.umbrellabackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("`雨伞站点表`")
public class Site {
    @TableId(value = "`站点ID`", type = IdType.AUTO)
    private Integer siteId;

    @TableField("`站点编号`")
    private String siteCode;

    @TableField("`站点名称`")
    private String siteName;

    @TableField("`站点位置`")
    private String location;

    @TableField("`伞位数量`")
    private Integer totalSlots;

    @TableField("`站点状态`")
    private Integer status;

    @TableField("`创建时间`")
    private Date createTime;
}