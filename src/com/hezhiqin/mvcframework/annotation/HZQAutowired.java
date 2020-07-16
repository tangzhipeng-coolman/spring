package com.hezhiqin.mvcframework.annotation;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HZQAutowired {

    String value() default  "";
}
