package com.hezhiqin.mvcframework.servlet;

import com.hezhiqin.mvcframework.annotation.HZQAutowired;
import com.hezhiqin.mvcframework.annotation.HZQController;
import com.hezhiqin.mvcframework.annotation.HZQRequestMapping;
import com.hezhiqin.mvcframework.annotation.HZQService;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 1.init()初始化时根据配置的需要扫描的包进行扫描，将包下所有的类放入容器中
 *
 * 2.先识别@HZQController，将其获取Controller上的RequestMapping地址作为baseUrl
 *
 * 3.获取@HZQController里面所有方法,获取上的RequestMapping地址作为和baseUrl进行拼接，
 *
 * 4.将url地址和方法作为K,V放入容器中；
 *
 * 5.如果是@HZQService，将@HZQService标注的类实例化，并将其父类接口类和实例K,V放入容器中
 *
 * 6.解析容器，获取Controller的参数，如果没有HZQAutowired修饰的属性则跳过，如果有的话从容器中获取并注入
 *
 * 7.请求时调用doDispatch方法，根据url和参数（name）来反射执行方法；
 *
 */
public class HZQDispathcherServlet extends HttpServlet {

    //新建一个“容器”
    private Map<String,Object> mapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // TODO Auto-generated method stub
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }

    }



    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("text/html");
        req.setCharacterEncoding("utf-8");
        resp.setCharacterEncoding("utf-8");
        String url = req.getRequestURI();//请求的相对路径
        System.out.println("url:"+url);
        String contextPath = req.getContextPath();//项目根路径
        System.out.println("contextPath:"+contextPath);
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        System.out.println("url:"+url);
        if (!this.mapping.containsKey(url)) {//如果不存在这个路径则返回404
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = (Method) this.mapping.get(url);
        Map<String,String[]> params = req.getParameterMap();
        Object o =method.getDeclaringClass().getName();
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()),new Object[]{req,resp,params.get("name")[0]});
    }



    @Override
    public void init(ServletConfig config)  {//初始化
        Properties configContext = new Properties();
        String r = config.getInitParameter("contextConfigLocation");
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(r.replace("classpath:",""));
        try {
            //获取配置信息，定位到需要扫描的包下面
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            //扫描配置的路径，将所有相关的类放入容器中
            doScanner(scanPackage);

            System.out.println("classNameLIST" +mapping.keySet());
            String[] StringList =new String[0];
            //循环容器中的类
            String[] classNameList = mapping.keySet().toArray(StringList);

            for (String className : classNameList) {

                System.out.println("className" +className);
                if(!className.contains(".")){continue;}
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(HZQController.class)) {//如果是@HZQController
                    mapping.put(className,clazz.newInstance());
                    String baseUrl = "";
                    if (clazz.isAnnotationPresent(HZQRequestMapping.class)) {//获取Controller上的RequestMapping地址
                        HZQRequestMapping requestMapping =clazz.getAnnotation(HZQRequestMapping.class);
                        baseUrl = requestMapping.value();
                    }
                    Method[] methods = clazz.getMethods();//获取Controller里面所有方法
                    for (Method method : methods) {
                        if (!method.isAnnotationPresent(HZQRequestMapping.class)) {continue; }//如果没有@HZQRequestMapping则跳过
                        HZQRequestMapping requestMapping =
                                method.getAnnotation(HZQRequestMapping.class);
                        String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                        mapping.put(url, method);
                        System.out.println("Mapped " + url + "," + method);

                    }
                }else if(clazz.isAnnotationPresent(HZQService.class)) {//如果是@HZQService
                    HZQService service = clazz.getAnnotation(HZQService.class);
                    String beanName = service.value();
                    if("".equals(beanName)){beanName = clazz.getName();}
                    Object instance = clazz.newInstance();
                    mapping.put(beanName,instance);
                    for (Class<?> i : clazz.getInterfaces()) {//获取接口类
                        mapping.put(i.getName(),instance);
                    }

                }else {
                    continue;
                }
            }

            System.out.println("MAP"+mapping);
            for (Object object : mapping.values()) {//获取容器内所有的实例

                if(object == null){continue;}
                Class clazz = object.getClass();
                if(clazz.isAnnotationPresent(HZQController.class)){
                    Field[] fields = clazz.getDeclaredFields();//获取Controller的参数
                    for (Field field : fields) {
                        if(!field.isAnnotationPresent(HZQAutowired.class)){continue; }//如果没有HZQAutowired修饰的属性则跳过
                        HZQAutowired autowired = field.getAnnotation(HZQAutowired.class);
                        String beanName = autowired.value();
                        if("".equals(beanName)){beanName = field.getType().getName();}
                        field.setAccessible(true);
                        try {
                            field.set(mapping.get(clazz.getName()),mapping.get(beanName));//注入
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                    }

                }

            }


        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if(is != null){
                try {is.close();} catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }


    }



    /**
     * @param scanPackage
     * 扫描配置的路径，将所有相关的类放入容器中
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" +
                scanPackage.replaceAll("\\.","/"));//将扫描的包的.换成/
        System.out.println("url"+url.toString());//
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {//如果是一个文件
                doScanner(scanPackage + "." +file.getName());
            }else {
                if(!file.getName().endsWith(".class")){continue;}//不是一个class文件则跳过
                String clazzName = (scanPackage + "." + file.getName().replace(".class",""));//获取classpath
                mapping.put(clazzName,null);//写入Key
                System.out.println("mapping"+mapping);
            }
        }


    }
}
