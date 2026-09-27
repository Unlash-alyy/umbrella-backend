package com.umbrella.umbrellabackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("伞位状态表")
public class Slot {
    @TableId(value = "伞位ID", type = IdType.AUTO)
    private Integer slotId;

    @TableField("站点ID")
    private Integer siteId;

    @TableField("伞位编号")
    private Integer slotNo;

    @TableField("伞标签")
    private String umbrellaLabel;

    @TableField("占用状态")
    private Integer status;

    @TableField("剩余数量")
    private Integer remainCount;

    @TableField("更新时间")
    private Date updateTime;
}