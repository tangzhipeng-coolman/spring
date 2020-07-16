package com.hezhiqin.mvcframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface HZQRequestParam {

    String value() default "";

}
