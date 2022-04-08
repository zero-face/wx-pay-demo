package com.zero.paymentdemo.controller;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.zero.paymentdemo.core.R;
import com.zero.paymentdemo.service.WxPayService;
import com.zero.paymentdemo.util.HttpUtils;
import com.zero.paymentdemo.util.WechatPay2ValidatorForRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Zero
 * @date 2022/4/5 19:43
 * @description
 * @since 1.8
 **/
@Slf4j
@RestController
@RequestMapping("/api/wx-pay")
@Api(tags = "网站微信支付api")
public class WxPayController {

    @Resource
    private WxPayService wxPayService;

    @Resource
    private Verifier verifier;


    @ApiOperation("调用统一下单接口，生成支付二维码")
    @PostMapping("/native/{productId}")
    public R nativePay(@PathVariable Long productId) throws IOException {
        log.info("发起支付请求");

        //返回支付二维码链接和订单号
        Map<String, Object> map = wxPayService.nativePay(productId);

        //需要设置R为链式
        return R.ok().setData(map);
    }

    /**
     * 微信支付通知接口
     * @param request
     * @param response
     * @return
     */
    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response) {
        final Gson gson = new Gson();
        Map<String, Object> map = new HashMap<>();
        try {
            //处理通知参数
            String body = HttpUtils.readData(request);

            final Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            log.info("通知的id === {}", bodyMap.get("id"));
            log.info("支付通知的完整数据 === {}", body);

            // TODO: 2022/4/6 签名验证
            //异步回调签名认证
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest = new WechatPay2ValidatorForRequest(verifier, body, (String)bodyMap.get("id"));
            if(!wechatPay2ValidatorForRequest.validate(request)) {
                log.error("验签失败");
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "失败");
                return gson.toJson(map);
            }
            log.info("验签成功");
            // TODO: 2022/4/6 处理订单
            wxPayService.processOrder(bodyMap);

            //回调应答
            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);
        } catch (Exception e) {

            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "失败");
            e.printStackTrace();
            return gson.toJson(map);

        }
    }

    @PostMapping("/cancel/{orderNo}")
    public R cancelOrder(@PathVariable String orderNo) throws IOException {

        log.info("取消订单");

        wxPayService.cancelOrder(orderNo);
        return R.ok().setMessage("取消成功");
    }

    /**
     * 两种方式可以查询，一种是orderNo,一种是事务ID
     * @param orderNo
     * @return
     */
    @GetMapping("/query/{orderNo}")
    public R queryOrder(String orderNo) throws IOException {
        log.info("查询订单");
        String result = wxPayService.queryOrder(orderNo);
        return R.ok().setMessage("查询成功").data("result", result);
    }

    @ApiOperation("申请退款")
    @PostMapping("/refunds/{orderNo}/{reason}")
    public R refunds(@PathVariable String orderNo, @PathVariable String reason) throws IOException {
        log.info("申请退款");
        wxPayService.refund(orderNo, reason);
        return R.ok();
    }

    /**
     * 查询退款
     * @param refundNo
     * @return
     * @throws Exception
     */
    @ApiOperation("查询退款：测试用")
    @GetMapping("/query-refund/{refundNo}")
    public R queryRefund(@PathVariable String refundNo) throws Exception {

        log.info("查询退款");

        String result = wxPayService.queryRefund(refundNo);
        return R.ok().setMessage("查询成功").data("result", result);
    }

    /**
     * 退款结果通知
     * 退款状态改变后，微信会把相关退款结果发送给商户。
     */
    @ApiOperation("退款结果通知")
    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response){

        log.info("退款通知执行");
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();//应答对象

        try {
            //处理通知参数
            String body = HttpUtils.readData(request);
            Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String)bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);

            //签名的验证
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, body, requestId);
            if(!wechatPay2ValidatorForRequest.validate(request)){

                log.error("通知验签失败");
                //失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "通知验签失败");
                return gson.toJson(map);
            }
            log.info("通知验签成功");

            //处理退款单
            wxPayService.processRefund(bodyMap);

            //成功应答
            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);

        } catch (Exception e) {
            e.printStackTrace();
            //失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "失败");
            return gson.toJson(map);
        }
    }

    @ApiOperation("获取账单url：测试用")
    @GetMapping("/querybill/{billDate}/{type}")
    public R queryTradeBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {

        log.info("获取账单url");

        String downloadUrl = wxPayService.queryBill(billDate, type);
        return R.ok().setMessage("获取账单url成功").data("downloadUrl", downloadUrl);
    }

    @ApiOperation("下载账单")
    @GetMapping("/downloadbill/{billDate}/{type}")
    public R downloadBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {

        log.info("下载账单");
        String result = wxPayService.downloadBill(billDate, type);

        return R.ok().data("result", result);
    }
}
