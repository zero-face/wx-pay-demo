package com.zero.paymentdemo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.zero.paymentdemo.entity.OrderInfo;
import com.zero.paymentdemo.entity.RefundInfo;
import com.zero.paymentdemo.enums.wxpay.WxRefundStatus;
import com.zero.paymentdemo.mapper.RefundInfoMapper;
import com.zero.paymentdemo.service.OrderInfoService;
import com.zero.paymentdemo.service.RefundInfoService;
import com.zero.paymentdemo.util.OrderNoUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefundInfoServiceImpl extends ServiceImpl<RefundInfoMapper, RefundInfo> implements RefundInfoService {

    @Resource
    private OrderInfoService orderInfoService;

    @Override
    public RefundInfo createRefundByOrderNo(String orderNo, String reason) {
        //根据订单编号获取订单信息
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);

        //根据订单号生成退款订单
        final RefundInfo refundInfo = new RefundInfo();
        refundInfo.setOrderNo(orderNo);
        refundInfo.setRefundNo(OrderNoUtils.getRefundNo());
        //订单原来的金额
        refundInfo.setTotalFee(orderInfo.getTotalFee());
        //退款金额
        refundInfo.setRefund(orderInfo.getTotalFee());
        refundInfo.setReason(reason);

        //保存退款订单
        baseMapper.insert(refundInfo);
        return refundInfo;
    }

    @Override
    public void updateRefund(String content) {
        final Gson gson = new Gson();

        final Map<String, String> resultMap = gson.fromJson(content, HashMap.class);

        //根据退款单编号修改退款单
        final QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<RefundInfo>().eq("refund_no", resultMap.get("out_refund_no"));
        final RefundInfo refundInfo = new RefundInfo();
        refundInfo.setRefundId(resultMap.get("refund_id"));
        //查询退款和申请退款中的返回参数
        if(resultMap.get("status") != null) {
            refundInfo.setRefundStatus(resultMap.get("status"));
        }
        //退款回调中的回调参数
        if(resultMap.get("refund_status") != null) {
            refundInfo.setRefundStatus(resultMap.get("refund_status"));
        }
        refundInfo.setContentNotify(content);
        //更新退款单
        baseMapper.update(refundInfo,queryWrapper);
    }

    /**
     * 找出申请退款超过minutes分钟并且未成功的退款单
     * @param minutes
     * @return
     */
    @Override
    public List<RefundInfo> getNoRefundOrderByDuration(int minutes) {

        //minutes分钟之前的时间
        Instant instant = Instant.now().minus(Duration.ofMinutes(minutes));

        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_status", WxRefundStatus.PROCESSING.getType());
        queryWrapper.le("create_time", instant);
        List<RefundInfo> refundInfoList = baseMapper.selectList(queryWrapper);
        return refundInfoList;
    }
}
