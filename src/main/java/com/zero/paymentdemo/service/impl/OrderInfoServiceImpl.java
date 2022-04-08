package com.zero.paymentdemo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zero.paymentdemo.entity.OrderInfo;
import com.zero.paymentdemo.entity.Product;
import com.zero.paymentdemo.enums.OrderStatus;
import com.zero.paymentdemo.mapper.OrderInfoMapper;
import com.zero.paymentdemo.mapper.ProductMapper;
import com.zero.paymentdemo.service.OrderInfoService;
import com.zero.paymentdemo.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private ProductMapper productMapper;

    @Override
    public OrderInfo createOrderByProductId(long productId) {
        //判断是否有未支付订单
        OrderInfo orderInfo = this.getNoPayOrderByProductId(productId);
        if(orderInfo != null) {
            return orderInfo;
        }
        //获取商品信息
        Product product = productMapper.selectById(productId);
        //生成订单
        orderInfo = new OrderInfo();
        orderInfo.setTitle(product.getTitle());
        //生成订单号
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo());
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice());
        orderInfo.setOrderStatus(OrderStatus.NOTPAY.getType());
        //存入数据库
        final int insert = baseMapper.insert(orderInfo);

        return orderInfo;
    }

    /**
     * 存储订单二维码
     * @param orderNo
     * @param codeUrl
     */
    @Override
    public void saveCodeUrl(String orderNo, String codeUrl) {
        QueryWrapper<OrderInfo> orderByNo = new QueryWrapper<OrderInfo>().eq("order_no", orderNo);
        final OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCodeUrl(codeUrl);
        baseMapper.update(orderInfo, orderByNo);
    }

    /**
     * 查询订单列表，并倒序排序
     * @return
     */
    @Override
    public List<OrderInfo> listOrderInfoByCreateTImeDesc() {
        final QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().orderByDesc("create_time");
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 根据订单号更新订单状态
     * @param orderNo
     * @param orderStatus
     */
    @Override
    public void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus) {
        log.info("更新订单状态 ===> {}  ====> {}", orderNo, orderStatus.getType());

        final QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().eq("order_no", orderNo);
        final OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderStatus(orderStatus.getType());
        baseMapper.update(orderInfo, queryWrapper);
    }

    /**
     * 根据订单号查询订单状态是否支付
     * @param orderNo
     * @return
     */
    @Override
    public String getOrderStatus(String orderNo) {
        final QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().eq("order_no", orderNo);
        final OrderInfo orderInfo = baseMapper.selectOne(queryWrapper);
        if(orderInfo == null) {
            return null;
        }
        return orderInfo.getOrderStatus();
    }

    /**
     * 查询创建超过minutes没有支付的订单
     * @param minutes
     */
    @Override
    public List<OrderInfo> getNoPayOrderByDuration(int minutes) {
        final Instant instant = Instant.now().minus(Duration.ofMinutes(minutes));
        final QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().eq("order_status", OrderStatus.NOTPAY.getType()).le("create_time", instant);
        final List<OrderInfo> orderInfos = baseMapper.selectList(queryWrapper);
        return orderInfos;
    }

    /**
     * 根据订单号获取订单信息
     * @param orderNo
     * @return
     */
    @Override
    public OrderInfo getOrderByOrderNo(String orderNo) {
        final QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().eq("order_no", orderNo).eq("order_status", OrderStatus.SUCCESS.getType());
        return baseMapper.selectOne(queryWrapper);
    }

    /**
     * 根据商品ID查询未支付的订单
     * @param productId
     * @return
     */
    private OrderInfo getNoPayOrderByProductId(long productId) {
        QueryWrapper<OrderInfo> notPayOrderInfoQueryWrapper = new QueryWrapper<OrderInfo>().eq("product_id", productId).eq("order_status", OrderStatus.NOTPAY.getType());
        OrderInfo orderInfo = baseMapper.selectOne(notPayOrderInfoQueryWrapper);
        if(orderInfo != null) {
            return orderInfo;
        }
        return null;
    }
}
