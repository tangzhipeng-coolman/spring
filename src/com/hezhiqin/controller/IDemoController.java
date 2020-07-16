package com.hezhiqin.controller;

import com.hezhiqin.mvcframework.annotation.HZQAutowired;
import com.hezhiqin.mvcframework.annotation.HZQController;
import com.hezhiqin.mvcframework.annotation.HZQRequestMapping;
import com.hezhiqin.mvcframework.annotation.HZQRequestParam;
import com.hezhiqin.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@HZQController
@HZQRequestMapping("/demo")
public class IDemoController {

    @HZQAutowired
    private IDemoService  iDemoService;

    @HZQRequestMapping("/query")
    public void queryName(HttpServletRequest req, HttpServletResponse resp,
                      @HZQRequestParam("name") String name) {
        String result = iDemoService.gwt(name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
