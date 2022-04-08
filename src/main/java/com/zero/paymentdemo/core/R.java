package com.zero.paymentdemo.core;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
//每次对对象的操作都会返回对象本身，让其能够进行链式操作
@Accessors(chain = true)
public class R {

    private Integer code; //响应码
    private String message; //响应消息
    private Map<String, Object> data = new HashMap<>();

    public static R ok(){
        R r = new R();
        r.setCode(0);
        r.setMessage("成功");
        return r;
    }

    public static R error(){
        R r = new R();
        r.setCode(-1);
        r.setMessage("失败");
        return r;
    }

    public R data(String key, Object value){
        this.data.put(key, value);
        return this;
    }

}
