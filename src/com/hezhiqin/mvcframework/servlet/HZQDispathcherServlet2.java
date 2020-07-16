package com.hezhiqin.mvcframework.servlet;

import com.hezhiqin.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

import static java.lang.Character.isUpperCase;

public class HZQDispathcherServlet2 extends HttpServlet {

    /**
     *新建一个容器
     */
    private Map<String,Object> ioc = new HashMap<>();

    /**
     *保存application.properties配置文件中的内容
     */
    private Properties contextConfig = new Properties();

    /**
     *保存扫描的所有类
     */
    private List<String> classNames = new ArrayList<>();

    /**
     *保存URL和Method的对应关系
     */
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        /**
         * 加载配置文件
         */
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        /**
         * 扫描相关类
         */
        doScanner(contextConfig.getProperty("scanPackage"));

        /**
         * 初始化扫描的类，并且放置到IOC中
         */
        doInstance();

        /**
         * 完成依赖注入
         */
        doAutowired();

        /**
         * 初始化
         */
        initHandlerMapping();
    }

    public void doDispatch(HttpServletRequest request,HttpServletResponse response){
        response.setContentType("text/html");
        try {
            request.setCharacterEncoding("utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        response.setCharacterEncoding("utf-8");
        String url = request.getRequestURI();//请求的相对路径
        String contextPath = request.getContextPath();//项目根路径
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if(!handlerMapping.containsKey(url)){
            try {
                response.getWriter().write("404,不存在该地址");
                return ;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Method method = this.handlerMapping.get(url);
        /**
         * 从request中拿到url传过来的参数
         */
        Map<String,String[]> params = request.getParameterMap();
        /**
         * 获取方法的形参列表
         */
        Parameter[] parameters = method.getParameters();
        /**
         * 保存赋值参数的位置
         */
        Object [] paramValues = new Object[parameters.length];
        /**
         * 按根据参数位置动态赋值
         */
        for (int i = 0; i < parameters.length; i ++) {
            Parameter parameter = parameters[i];

            if(parameter.getType() == HttpServletRequest.class ){
                paramValues[i] = request;
                continue;

            }else if(parameter.getType()  == HttpServletResponse.class){
                paramValues[i] = response;
                continue;
            }else if(parameter.getType()  == String.class) {
                if (parameter.isAnnotationPresent(HZQRequestParam.class)) {
                    HZQRequestParam requestParam = parameter.getAnnotation(HZQRequestParam.class);
                    if (params.containsKey(requestParam.value())) {
                        for (Map.Entry<String, String[]> param : params.entrySet()) {
                            String value = Arrays.toString(param.getValue())
                                    .replaceAll("\\[|\\]", "")
                                    .replaceAll("\\s", ",");
                            paramValues[i] = value;

                        }

                    }
                }
            }
        }
        String beanName= toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        System.out.println(ioc.get(beanName));
        try {
            method.invoke(ioc.get(beanName),paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化
     */
    private void initHandlerMapping() {

        for(Map.Entry<String,Object>entry:ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(HZQController.class)){continue;}
            String baseUrl ="";
            if(clazz.isAnnotationPresent(HZQRequestMapping.class)){
                HZQRequestMapping hzqRequestMapping = clazz.getAnnotation(HZQRequestMapping.class);
                baseUrl = hzqRequestMapping.value();
            }

            for(Method method:clazz.getMethods()){
                if(!method.isAnnotationPresent(HZQRequestMapping.class)){continue;}
                HZQRequestMapping hzqRequestMapping = method.getAnnotation(HZQRequestMapping.class);
                baseUrl = ("/"+ baseUrl+"/"+hzqRequestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(baseUrl,method);
            }
        }
    }

    /**
     * 完成依赖注入
     */
    private void doAutowired() {

        if(ioc.isEmpty()){
            return;
        }
        //Declared所有的，特定的字段，包括private/protected/defalut
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
                Field[] fields =entry.getValue().getClass().getDeclaredFields();
                for(Field field:fields){
                    if(!field.isAnnotationPresent(HZQAutowired.class)){
                       continue;
                    }
                    HZQAutowired hzqAutowired = field.getAnnotation(HZQAutowired.class);
                    String beanName = hzqAutowired.value().trim();
                    //如果没有自定义name，默认类型注入
                    if("".equals(beanName.trim())){
                        beanName = toLowerFirstCase(field.getType().getSimpleName());
                    }
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(),ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
        }
    }

    /**
     * 初始化扫描的类，并且放置到IOC中
     */
    private void doInstance() {
        if(classNames.isEmpty()){
            return;
        }
        try {
        for(String className:classNames){
            //从List中存的类地址获取内存对应的Class
            Class<?> clazz = Class.forName(className);
            /**
             * 什么样的类需要初始化？
             * 加了注解的类，才初始化，怎么判断？
             * 为了简化代码逻辑，主要体会设计思想，只举例@Controller和@Service,@Componment...就不一一举例
             */
            if(clazz.isAnnotationPresent(HZQController.class)){//如果是@HZQController
                Object instance = clazz.newInstance();
                //spring默认类名首字母小写
                String beanName = toLowerFirstCase(clazz.getSimpleName());
                ioc.put(beanName,instance);
            }else if(clazz.isAnnotationPresent(HZQService.class)){
                //1.自定义的beanName
                HZQService hzqService = clazz.getAnnotation(HZQService.class);
                String beanName = hzqService.value();
                if("".equals(beanName.trim())){
                    beanName = toLowerFirstCase(clazz.getSimpleName());
                }
                Object instance = clazz.newInstance();
                ioc.put(beanName,instance);//跟下面是否存在重叠？
                //3.根据类型自动赋值
                for(Class<?>i:clazz.getInterfaces()){
                    if(ioc.containsKey(i.getSimpleName())){
                        throw  new Exception("The “" + i.getName() + "” is exists!!");
                    }
                    ioc.put(toLowerFirstCase(i.getSimpleName()),instance);
                }
            }else{
                continue;
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 类名首字母小写
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        if(isUpperCase(chars[0])){
            chars[0] +=32;
        }
        return String.valueOf(chars);
    }

    /**
     * 扫描相关类
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {

        //scanPackage = com.hezhiqin存储的是包路径
        //转化成文件路径，实际就是把.转化成/即可
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file:classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else {
                if(!file.getName().contains(".class")){
                    continue;
                }
                String clazzName = scanPackage+"."+file.getName().replace(".class","");
                classNames.add(clazzName);
            }
        }

    }

    /**
     * 加载配置文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        //从类路径下获取spring的配置文件（在src下面）
        //读取配置文件放到Properties对象中
        //相对于scanPackage=com.hezhiqin从文件保存到了内存中去
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(contextConfigLocation.replace("classpath:",""));
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
