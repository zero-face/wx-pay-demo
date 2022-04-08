package com.zero.paymentdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

/**
 * @author Zero
 * @date 2022/4/5 16:20
 * @description
 * @since 1.8
 **/
@Data
public class BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    private Date createTime;

    private Date updateTime;
}
