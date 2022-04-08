package com.zero.paymentdemo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zero.paymentdemo.entity.OrderInfo;
import com.zero.paymentdemo.enums.OrderStatus;

import java.util.List;

public interface OrderInfoService extends IService<OrderInfo> {

    OrderInfo createOrderByProductId(long productId);

    void saveCodeUrl(String orderNo, String codeUrl);

    List<OrderInfo> listOrderInfoByCreateTImeDesc();

    void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus);

    /**
     * 根据订单号获取支付状态
     * @param orderNo
     * @return
     */
    String getOrderStatus(String orderNo);

    List<OrderInfo> getNoPayOrderByDuration(int minutes);

    OrderInfo getOrderByOrderNo(String orderNo);
}
