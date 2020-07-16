package com.hezhiqin.service.impl;

import com.hezhiqin.mvcframework.annotation.HZQService;
import com.hezhiqin.service.IDemoService;

@HZQService
public class IDemoServiceImpl implements IDemoService {
    @Override
    public String gwt(String Name) {
        return "我的名字是："+Name;
    }
}
