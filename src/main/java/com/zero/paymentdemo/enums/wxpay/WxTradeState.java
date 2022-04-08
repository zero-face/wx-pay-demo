package com.zero.paymentdemo.enums.wxpay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商户平台和微信支付平台之间的一个支付交易的状态
 */
@AllArgsConstructor
@Getter
public enum WxTradeState {

    /**
     * 支付成功
     */
    SUCCESS("SUCCESS"),

    /**
     * 未支付
     */
    NOTPAY("NOTPAY"),

    /**
     * 已关闭
     */
    CLOSED("CLOSED"),

    /**
     * 转入退款
     */
    REFUND("REFUND");

    /**
     * 类型
     */
    private final String type;
}
