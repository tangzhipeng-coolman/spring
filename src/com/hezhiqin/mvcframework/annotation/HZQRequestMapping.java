package com.hezhiqin.mvcframework.annotation;

import java.lang.annotation.*;

@Documented
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HZQRequestMapping {

    String value() default  "";

}
