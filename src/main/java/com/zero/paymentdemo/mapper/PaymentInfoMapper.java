package com.zero.paymentdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.paymentdemo.entity.PaymentInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentInfoMapper extends BaseMapper<PaymentInfo> {
}
