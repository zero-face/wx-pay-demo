package com.zero.paymentdemo.controller;

import com.zero.paymentdemo.core.R;
import com.zero.paymentdemo.entity.Product;
import com.zero.paymentdemo.service.ProductService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * @author Zero
 * @date 2022/4/5 15:43
 * @description
 * @since 1.8
 **/
@CrossOrigin
@Api(tags = "商品管理")
@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Resource
    private ProductService productService;

    @ApiOperation("测试接口")
    @GetMapping("/test")
    public R test() {
        return R.ok().data("message", "hello").data("now", new Date());
    }

    @ApiOperation("获取商品列表")
    @GetMapping("/list")
    public R list() {
        final List<Product> list = productService.list();
        return R.ok().data("productList", list);
    }
}
