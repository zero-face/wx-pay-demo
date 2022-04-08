package com.zero.paymentdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.paymentdemo.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

}
