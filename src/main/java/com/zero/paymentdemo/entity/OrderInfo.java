package com.zero.paymentdemo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author Zero
 * @date 2022/4/5 16:21
 * @description
 * @since 1.8
 **/
@Data
@TableName("t_order_info")
public class OrderInfo extends BaseEntity{

    /**
     * 订单标题
     */
    private String title;

    /**
     * 商品订单编号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 支付产品ID
     */
    private Long productId;

    /**
     * 订单金额
     */
    private Integer totalFee;

    /**
     * 订单二维码链接
     */
    private String codeUrl;

    /**
     * 订单状态
     */
    private String orderStatus;
}
