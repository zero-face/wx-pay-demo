package com.zero.paymentdemo.service.impl;

import com.github.wxpay.sdk.WXPayUtil;
import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import com.zero.paymentdemo.config.WxPayConfig;
import com.zero.paymentdemo.entity.OrderInfo;
import com.zero.paymentdemo.entity.RefundInfo;
import com.zero.paymentdemo.enums.OrderStatus;
import com.zero.paymentdemo.enums.wxpay.WxApiType;
import com.zero.paymentdemo.enums.wxpay.WxNotifyType;
import com.zero.paymentdemo.enums.wxpay.WxRefundStatus;
import com.zero.paymentdemo.enums.wxpay.WxTradeState;
import com.zero.paymentdemo.service.OrderInfoService;
import com.zero.paymentdemo.service.PaymentInfoService;
import com.zero.paymentdemo.service.RefundInfoService;
import com.zero.paymentdemo.service.WxPayService;
import com.zero.paymentdemo.util.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Zero
 * @date 2022/4/5 19:45
 * @description
 * @since 1.8
 **/
@Slf4j
@Service
public class WxPayServiceImpl implements WxPayService {

    @Resource
    private WxPayConfig wxPayConfig;

    @Resource
    @Qualifier("wxPayClient")
    private CloseableHttpClient httpClient;

    @Resource
    private PaymentInfoService paymentInfoService;

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private RefundInfoService refundInfoService;

    @Resource
    private RefundInfoService refundsInfoService;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建订单，调用native支付接口
     * @param productId
     * @return code_url 和订单号
     * @throws IOException
     */
    @Override
    public Map<String, Object> nativePay(Long productId) throws IOException {

        log.info("生成订单");
        //生成订单并存入数据库
        OrderInfo orderByProductId = orderInfoService.createOrderByProductId(productId);
        String codeUrl = orderByProductId.getCodeUrl();
        if(orderByProductId != null && !StringUtils.isEmpty(codeUrl)) {
            //返回二维码
            log.info("订单已存在，二维码已经保存");
            HashMap<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderByProductId.getOrderNo());
            return map;
        }

        /*
         * 调用统一下单接口
         */
        log.info("调用统一下单接口");
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));
        // 请求body参数
        Gson gson = new Gson();
        HashMap<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("appid", wxPayConfig.getAppid());
        paramsMap.put("mchid", wxPayConfig.getMchId());
        paramsMap.put("description", orderByProductId.getTitle());
        paramsMap.put("out_trade_no", orderByProductId.getOrderNo());
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));
        Map amountMap = new HashMap();
        amountMap.put("total", orderByProductId.getTotalFee());
        amountMap.put("currency", "CNY");
        paramsMap.put("amount", amountMap);
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数" + jsonParams);
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        //完成签名并执行请求
        CloseableHttpResponse response = httpClient.execute(httpPost);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            //处理成功
            if (200 == statusCode) {
                log.info("success,return body = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("success");
            } else {
                log.info("failed,resp code = " + statusCode+ ",return body = " + bodyAsString);
                throw new IOException("request failed");
            }
            //响应结果
            HashMap<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            //二维码地址
            codeUrl = resultMap.get("code_url");
            //保存二维码
            orderInfoService.saveCodeUrl(orderByProductId.getOrderNo(), codeUrl);
            //返回二维码
            HashMap<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderByProductId.getOrderNo());
            return map;
        } finally {
            response.close();
        }
    }

    /**
     * 处理订单：1. 将密文解密获取相关支付信息
     *         2. 处理掉重复通知（已支付则不处理）
     *         3. 更新订单状态（更新为已支付）
     *         4. 记录支付日志
     * @param bodyMap
     * @throws GeneralSecurityException
     */
    @Override
    public void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("处理订单");

        //解密resource
        final String plainText = decryptFromResource(bodyMap);
        //将明文转换为map
        final Gson gson = new Gson();
        final HashMap plainTextMap = gson.fromJson(plainText, HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");
        //在对业务数据进行状态检查和处理之前，要采用数据锁进行并发控制，避免函数重入造成的数据混乱
        //成功获取则立即返回true，获取失败则立即返回false，不必等待锁的释放
        if(lock.tryLock()) {
            try {
                //处理重复的通知（此处有多线程问题，当多个请求同时到达的时候，就可能造成记录多次日志，多次更新订单状态的情况）
                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if(!OrderStatus.NOTPAY.getType().equals(orderStatus)) {
                    return;
                }
                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);
                //记录支付日志
                paymentInfoService.createPaymentInfo(plainText);
            } finally {
                //释放锁
                lock.unlock();
            }
        }


    }

    /**
     * 取消订单
     * @param orderNo
     */
    @Override
    public void cancelOrder(String orderNo) throws IOException {
        //调用微信支付的关单接口
        this.closeOrder(orderNo);
        //更新商户的订单状态
        orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CANCEL);
    }

    /**
     * 根据订单号查询接口订单状态
     * @param orderNo
     * @return
     * @throws IOException
     */
    @Override
    public String queryOrder(String orderNo) throws IOException {
        log.info("查询接口调用 ===》 {}", orderNo);
        String url = String.format(WxApiType.ORDER_QUERY_BY_NO.getType(),orderNo);
        url = wxPayConfig.getDomain().concat(url).concat("?mchid=").concat(wxPayConfig.getMchId());

        final HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        final CloseableHttpResponse response = httpClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            //处理成功
            if (200 == statusCode) {
                log.info("success,return body = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("success");
            } else {
                log.info("failed,resp code = " + statusCode+ ",return body = " + bodyAsString);
                throw new IOException("request failed");
            }
            return bodyAsString;
        } finally {
            response.close();
        }
    }

    /**
     * 根据订单号查询微信支付查单接口，核实订单状态
     * 如果订单已经支付，更新商户端订单状态
     * 如果订单未支付，调用关单接口，并更新商户端订单状态
     * @param orderNo
     */
    @Override
    public void checkOrderStatus(String orderNo) throws IOException {
        log.info("根据订单号合适订单状态 ====》 ", orderNo);
        final String result = this.queryOrder(orderNo);
        final Gson gson = new Gson();
        final HashMap resultMap = gson.fromJson(result, HashMap.class);

        //获取订单状态
        final Object tradeState = resultMap.get("trade_state");
        if(WxTradeState.SUCCESS.getType().equals(tradeState)) {
            log.warn("核实订单已支付 ====》 {}", orderNo);

            //更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);
            //记录支付日志
            //该处查询订单信息返回的数据和支付通知返回的密文的字段内容是一致的
            paymentInfoService.createPaymentInfo(result);
        }
        if(WxTradeState.NOTPAY.getType().equals(tradeState)) {
            log.warn("合适订单未支付 ====》", orderNo);

            //如果订单未支付，则调用关单接口
            this.closeOrder(orderNo);
            //更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CLOSED);
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(String orderNo, String reason) throws IOException {
        log.info("创建退款记录");

        //根据订单编号创建退款单
        RefundInfo refundInfo = refundInfoService.createRefundByOrderNo(orderNo, reason);

        log.info("调用退款api");


        String url = wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType());
        final HttpPost httpPost = new HttpPost(url);

        //请求body参数
        final Gson gson = new Gson();
        final Map paramMap = new HashMap<>();
        paramMap.put("out_trade_no", orderNo);
        paramMap.put("out_refund_no", refundInfo.getRefundNo());
        paramMap.put("reason", reason);
        paramMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));
        final HashMap<Object, Object> amountMap = new HashMap<>();
        amountMap.put("refund", refundInfo.getRefund());//退款金额
        amountMap.put("total", refundInfo.getTotalFee());//原订单金额
        amountMap.put("currency", "CNY");//退款币种
        paramMap.put("amount",amountMap);
        //将参数转化为json字符串
        final String jsonParams = gson.toJson(paramMap);
        log.info("请求参数 ====》 {}", jsonParams);

        final StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");
        final CloseableHttpResponse response = httpClient.execute(httpPost);

        try {
            final String bodyAsString = EntityUtils.toString(response.getEntity());
            final int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode == 200) {
                log.info("success, 退款结果 = " + bodyAsString);
            } else if(statusCode == 204) {
                log.info("success");
            } else {
                throw new RuntimeException("退款异常，响应码 = " + statusCode + ", 退款结果 = " + bodyAsString);
            }

            //更新订单状态

            orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_PROCESSING);

            //更新退款单
            refundInfoService.updateRefund(bodyAsString);
        } finally {
            response.close();
        }

    }

    /**
     * 查询退款接口调用
     * @param refundNo
     * @return
     */
    @Override
    public String queryRefund(String refundNo) throws Exception {

        log.info("查询退款接口调用 ===> {}", refundNo);

        String url =  String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(), refundNo);
        url = wxPayConfig.getDomain().concat(url);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = httpClient.execute(httpGet);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 查询退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("查询退款异常, 响应码 = " + statusCode+ ", 查询退款返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        } finally {
            response.close();
        }
    }

    /**
     * 根据退款单号核实退款单状态
     * @param refundNo
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkRefundStatus(String refundNo) throws Exception {

        log.warn("根据退款单号核实退款单状态 ===> {}", refundNo);

        //调用查询退款单接口
        String result = this.queryRefund(refundNo);

        //组装json请求体字符串
        Gson gson = new Gson();
        Map<String, String> resultMap = gson.fromJson(result, HashMap.class);

        //获取微信支付端退款状态
        String status = resultMap.get("status");

        String orderNo = resultMap.get("out_trade_no");

        if (WxRefundStatus.SUCCESS.getType().equals(status)) {

            log.warn("核实订单已退款成功 ===> {}", refundNo);

            //如果确认退款成功，则更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

            //更新退款单
            refundsInfoService.updateRefund(result);
        }

        if (WxRefundStatus.ABNORMAL.getType().equals(status)) {

            log.warn("核实订单退款异常  ===> {}", refundNo);

            //如果确认退款成功，则更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL);

            //更新退款单
            refundsInfoService.updateRefund(result);
        }
    }

    /**
     * 处理退款单
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processRefund(Map<String, Object> bodyMap) throws Exception {

        log.info("退款单");

        //解密报文
        String plainText = decryptFromResource(bodyMap);

        //将明文转换成map
        Gson gson = new Gson();
        HashMap plainTextMap = gson.fromJson(plainText, HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");

        if(lock.tryLock()){
            try {

                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus)) {
                    return;
                }

                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

                //更新退款单
                refundsInfoService.updateRefund(plainText);

            } finally {
                //要主动释放锁
                lock.unlock();
            }
        }
    }

    /**
     * 申请账单
     * @param billDate
     * @param type
     * @return
     * @throws Exception
     */
    @Override
    public String queryBill(String billDate, String type) throws Exception {
        log.warn("申请账单接口调用 {}", billDate);

        String url = "";
        if("tradebill".equals(type)){
            url =  WxApiType.TRADE_BILLS.getType();
        }else if("fundflowbill".equals(type)){
            url =  WxApiType.FUND_FLOW_BILLS.getType();
        }else{
            throw new RuntimeException("不支持的账单类型");
        }

        url = wxPayConfig.getDomain().concat(url).concat("?bill_date=").concat(billDate);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = httpClient.execute(httpGet);

        try {

            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 申请账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("申请账单异常, 响应码 = " + statusCode+ ", 申请账单返回结果 = " + bodyAsString);
            }

            //获取账单下载地址
            Gson gson = new Gson();
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            return resultMap.get("download_url");

        } finally {
            response.close();
        }
    }

    /**
     * 下载账单
     * @param billDate
     * @param type
     * @return
     * @throws Exception
     */
    @Override
    public String downloadBill(String billDate, String type) throws Exception {
        log.warn("下载账单接口调用 {}, {}", billDate, type);

        //获取账单url地址
        String downloadUrl = this.queryBill(billDate, type);
        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(downloadUrl);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = httpClient.execute(httpGet);

        try {

            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 下载账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("下载账单异常, 响应码 = " + statusCode+ ", 下载账单返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        } finally {
            response.close();
        }
    }

    @Override
    public Map<String, Object> nativePayV2(Long productId, String remoteAddr) throws Exception {

        log.info("生成订单");

        //生成订单
        OrderInfo orderInfo = orderInfoService.createOrderByProductId(productId);
        String codeUrl = orderInfo.getCodeUrl();
        if(orderInfo != null && !StringUtils.isEmpty(codeUrl)){
            log.info("订单已存在，二维码已保存");
            //返回二维码
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        }

        log.info("调用统一下单API");

        HttpClientUtils client = new HttpClientUtils(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY_V2.getType()));

        //组装接口参数
        Map<String, String> params = new HashMap<>();
        params.put("appid", wxPayConfig.getAppid());//关联的公众号的appid
        params.put("mch_id", wxPayConfig.getMchId());//商户号
        params.put("nonce_str", WXPayUtil.generateNonceStr());//生成随机字符串
        params.put("body", orderInfo.getTitle());
        params.put("out_trade_no", orderInfo.getOrderNo());

        //注意，这里必须使用字符串类型的参数（总金额：分）
        String totalFee = orderInfo.getTotalFee() + "";
        params.put("total_fee", totalFee);

        params.put("spbill_create_ip", remoteAddr);
        params.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY_V2.getType()));
        params.put("trade_type", "NATIVE");

        //将参数转换成xml字符串格式：生成带有签名的xml格式字符串
        String xmlParams = WXPayUtil.generateSignedXml(params, wxPayConfig.getPartnerKey());
        log.info("\n xmlParams：\n" + xmlParams);

        client.setXmlParam(xmlParams);//将参数放入请求对象的方法体
        client.setHttps(true);//使用https形式发送
        client.post();//发送请求
        String resultXml = client.getContent();//得到响应结果
        log.info("\n resultXml：\n" + resultXml);
        //将xml响应结果转成map对象
        Map<String, String> resultMap = WXPayUtil.xmlToMap(resultXml);

        //错误处理
        if("FAIL".equals(resultMap.get("return_code")) || "FAIL".equals(resultMap.get("result_code"))){
            log.error("微信支付统一下单错误 ===> {} ", resultXml);
            throw new RuntimeException("微信支付统一下单错误");
        }

        //二维码
        codeUrl = resultMap.get("code_url");

        //保存二维码
        String orderNo = orderInfo.getOrderNo();
        orderInfoService.saveCodeUrl(orderNo, codeUrl);

        //返回二维码
        Map<String, Object> map = new HashMap<>();
        map.put("codeUrl", codeUrl);
        map.put("orderNo", orderInfo.getOrderNo());

        return map;
    }


    /**
     * 微信关单接口的调用
     * @param orderNo
     */
    private void closeOrder(String orderNo) throws IOException {
        log.info("关闭订单调用， 订单号 ====》 {}", orderNo);
        String url = String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(), orderNo);
        url = wxPayConfig.getDomain().concat(url);
        final HttpPost httpPost = new HttpPost(url);
        //组装json请求体
        final Gson gson = new Gson();
        final Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("mchid", wxPayConfig.getMchId());
        final String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数 ===》 {}", jsonParams);

        //将请求参数设置到请求
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        //完成签名并执行请求
        CloseableHttpResponse response = httpClient.execute(httpPost);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            //处理成功
            if (200 == statusCode) {
                log.info("success200");
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("success204");
            } else {
                log.info("failed,resp code = " + statusCode);
                throw new IOException("request failed");
            }
        } finally {
            response.close();
        }
    }

    /**
     * 对称解密
     * @param bodyMap
     */
    private String decryptFromResource(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("密文解密");

        Map<String, String> resourceMap = (Map<String, String>)bodyMap.get("resource");
        //数据密文
        final String ciphertext = resourceMap.get("ciphertext");
        //随机串
        final String nonce = resourceMap.get("nonce");
        //附加数据
        final String associated_data = resourceMap.get("associated_data");

        log.info("密文 ===》 {}", ciphertext);
        AesUtil aesUtil = new AesUtil(wxPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        final String plainText = aesUtil.decryptToString(associated_data.getBytes(StandardCharsets.UTF_8), nonce.getBytes(StandardCharsets.UTF_8), ciphertext);
        log.info("明文 ===》 {}", plainText);
        return plainText;
    }
}
