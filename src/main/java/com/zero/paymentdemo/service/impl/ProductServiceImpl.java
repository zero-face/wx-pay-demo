package com.zero.paymentdemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zero.paymentdemo.entity.Product;
import com.zero.paymentdemo.mapper.ProductMapper;
import com.zero.paymentdemo.service.ProductService;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

}
