package com.zero.paymentdemo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zero.paymentdemo.entity.RefundInfo;

import java.util.List;

public interface RefundInfoService extends IService<RefundInfo> {

    RefundInfo createRefundByOrderNo(String orderNo, String reason);

    void updateRefund(String bodyAsString);

    List<RefundInfo> getNoRefundOrderByDuration(int minutes);
}
