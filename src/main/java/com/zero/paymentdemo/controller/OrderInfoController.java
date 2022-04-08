package com.zero.paymentdemo.controller;

import com.zero.paymentdemo.core.R;
import com.zero.paymentdemo.entity.OrderInfo;
import com.zero.paymentdemo.enums.OrderStatus;
import com.zero.paymentdemo.service.OrderInfoService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Zero
 * @date 2022/4/6 12:21
 * @description
 * @since 1.8
 **/
@RestController
@RequestMapping("/api/order-info")
@Api(tags = "商品订单管理")
@Slf4j
public class OrderInfoController {

    @Resource
    private OrderInfoService orderInfoService;

    @GetMapping("/list")
    public R list() {
        List<OrderInfo> list = orderInfoService.listOrderInfoByCreateTImeDesc();
        return R.ok().data("list", list);
    }

    /**
     * 定时查询订单状态
     * @param orderNo
     * @return
     */
    @GetMapping("/query-order-status/{orderNo}")
    public R queryOrderStatus(@PathVariable String orderNo) {
        final String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if(OrderStatus.SUCCESS.getType().equals(orderStatus)) {
            return R.ok().setMessage("支付成功");
        }
        return R.ok().setCode(101).setMessage("支付中....");
    }
}
