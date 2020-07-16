package com.hezhiqin.mvcframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public  @interface HZQController {
    String value() default "";
}
