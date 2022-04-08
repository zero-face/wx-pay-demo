package com.zero.paymentdemo.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * @author Zero
 * @date 2022/4/5 19:44
 * @description
 * @since 1.8
 **/
public interface WxPayService {
    /**
     * 生成临时订单，并且发起下订单请求获取预支付链接
     * @param productId
     * @return
     */
    Map<String, Object> nativePay(Long productId) throws IOException;

    void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException;

    void cancelOrder(String orderNo) throws IOException;

    String queryOrder(String orderNo) throws IOException;

    void checkOrderStatus(String orderNo) throws IOException;

    void refund(String orderNo, String reason) throws IOException;

    String queryRefund(String refundNo) throws Exception;

    public void checkRefundStatus(String refundNo) throws Exception;

    void processRefund(Map<String, Object> bodyMap) throws Exception;

    String queryBill(String billDate, String type) throws Exception;

    String downloadBill(String billDate, String type) throws Exception;

    Map<String, Object> nativePayV2(Long productId, String remoteAddr) throws Exception;
}
