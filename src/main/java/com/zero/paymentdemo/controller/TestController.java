package com.zero.paymentdemo.controller;

import com.zero.paymentdemo.config.WxPayConfig;
import com.zero.paymentdemo.core.R;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Zero
 * @date 2022/4/5 18:27
 * @description
 * @since 1.8
 **/
@RestController
@Api(tags = "测试控制器")
@RequestMapping("/api/test")
public class TestController {

    @Resource
    private WxPayConfig wxPayConfig;

    @GetMapping
    public R getWxConfig() {
        String mchId = wxPayConfig.getMchId();
        return R.ok().data("machId", mchId);
    }

}
