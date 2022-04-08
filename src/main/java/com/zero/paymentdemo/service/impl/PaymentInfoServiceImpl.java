package com.zero.paymentdemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.zero.paymentdemo.entity.PaymentInfo;
import com.zero.paymentdemo.enums.OrderStatus;
import com.zero.paymentdemo.enums.PayType;
import com.zero.paymentdemo.mapper.PaymentInfoMapper;
import com.zero.paymentdemo.service.PaymentInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

    /**
     * 创建支付日志
     * @param plainText
     * @param
     */
    @Override
    public void createPaymentInfo(String plainText) {
        log.info("记录支付日志");
        final Gson gson = new Gson();
        final HashMap plainTextMap = gson.fromJson(plainText, HashMap.class);
        final String orderNo = (String)plainTextMap.get("out_trade_no");
        //业务编号（微信维护了一个支付的事务ID）
        final String transactionId = (String) plainTextMap.get("transaction_id");
        //交易类型
        final String tradeType = (String) plainTextMap.get("trade_type");
        //交易状态
        final String tradeState = (String) plainTextMap.get("trade_state");
        //实际支付金额
        final Map<String, Object> amount = (Map<String, Object>) plainTextMap.get("amount");
        final int payerTotal = ((Double)amount.get("payer_total")).intValue();

        final PaymentInfo paymentInfo = new PaymentInfo();
        //订单编号
        paymentInfo.setOrderNo(orderNo);
        //支付类型（微信支付）
        paymentInfo.setPaymentType(PayType.WXPAY.getType());
        //微信事务ID
        paymentInfo.setTransactionId(transactionId);
        //交易类型
        paymentInfo.setTradeType(tradeType);
        //交易状态
        paymentInfo.setTradeState(tradeState);
        //实际支付金额
        paymentInfo.setPayerTotal(payerTotal);
        paymentInfo.setContent(plainText);
        baseMapper.insert(paymentInfo);
    }
}
