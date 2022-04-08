package com.zero.paymentdemo.task;

import com.zero.paymentdemo.entity.OrderInfo;
import com.zero.paymentdemo.entity.RefundInfo;
import com.zero.paymentdemo.service.OrderInfoService;
import com.zero.paymentdemo.service.RefundInfoService;
import com.zero.paymentdemo.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * @author Zero
 * @date 2022/4/6 17:13
 * @description
 * @since 1.8
 **/
@Component
@Slf4j
public class WxPayTask {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private WxPayService wxPayService;
    @Resource
    private RefundInfoService refundInfoService;


    /**
     * 秒 分 时 日 月 周
     * 日和周是互斥指定的，指定一个则另一个未 ？
     * * ： 每秒都执行
     * 0/3： 从第0秒开始，每3秒执行一次
     */
//    @Scheduled(cron = "* * * * * ?")
    public void task1() {
        log.info("test 执行......");
    }

    /**
     * 从第0秒开始，每隔30秒执行一次，查询和创建超过5分钟并且被支付的订单
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void orderConfirm() throws IOException {
        log.info("orderConfirm 执行......");
        //查找minutes前创建的订单
        final List<OrderInfo> noPayOrderByDuration = orderInfoService.getNoPayOrderByDuration(1);
        for(OrderInfo orderInfo : noPayOrderByDuration) {
            final String orderNo = orderInfo.getOrderNo();
            log.error("超时订单 ==== {}", orderNo);

            //核实订单状态： 调用微信查单接口
            wxPayService.checkOrderStatus(orderNo);
        }
    }


    /**
     * 从第0秒开始每隔30秒执行1次，查询创建超过5分钟，并且未成功的退款单
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void refundConfirm() throws Exception {
        log.info("refundConfirm 被执行......");

        //找出申请退款超过5分钟并且未成功的退款单
        List<RefundInfo> refundInfoList = refundInfoService.getNoRefundOrderByDuration(1);

        for (RefundInfo refundInfo : refundInfoList) {
            String refundNo = refundInfo.getRefundNo();
            log.warn("超时未退款的退款单号 ===> {}", refundNo);

            //核实订单状态：调用微信支付查询退款接口
            wxPayService.checkRefundStatus(refundNo);
        }
    }
}
