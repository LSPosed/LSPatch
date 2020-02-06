package com.storm.wind.xpatch.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by xiawanli on 2018/8/25
 */
public class ReflectUtils {

    //获取类的实例的变量的值
    public static Object getField(Object receiver, String fieldName) {
        return getField(null, receiver, fieldName);
    }

    //获取类的静态变量的值
    public static Object getField(String className, String fieldName) {
        return getField(className, null, fieldName);
    }

    public static Object getField(Class<?> clazz, String className, String fieldName, Object receiver) {
        try {
            if (clazz == null) {
                clazz = Class.forName(className);
            }
            Field field = clazz.getDeclaredField(fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            return field.get(receiver);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Object getField(String className, Object receiver, String fieldName) {
        Class<?> clazz = null;
        Field field;
        if (className != null && className.length() > 0) {
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            if (receiver != null) {
                clazz = receiver.getClass();
            }
        }
        if (clazz == null) {
            return null;
        }

        try {
            field = findField(clazz, fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            return field.get(receiver);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object setField(Object receiver, String fieldName, Object value) {
        try {
            Field field;
            field = findField(receiver.getClass(), fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object old = field.get(receiver);
            field.set(receiver, value);
            return old;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object setField(Class<?> clazz, Object receiver, String fieldName, Object value) {
        try {
            Field field;
            field = findField(clazz, fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object old = field.get(receiver);
            field.set(receiver, value);
            return old;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object callMethod(Object receiver, String methodName, Object... params) {
        return callMethod(null, receiver, methodName, params);
    }

    public static Object setField(String clazzName, Object receiver, String fieldName, Object value) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            Field field;
            field = findField(clazz, fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object old = field.get(receiver);
            field.set(receiver, value);
            return old;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static Object callMethod(String className, String methodName, Object... params) {
        return callMethod(className, null, methodName, params);
    }

    public static Object callMethod(Class<?> clazz, String className, String methodName, Object receiver,
                                    Class[] types, Object... params) {
        try {
            if (clazz == null) {
                clazz = Class.forName(className);
            }
            Method method = clazz.getDeclaredMethod(methodName, types);
            method.setAccessible(true);
            return method.invoke(receiver, params);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

    private static Object callMethod(String className, Object receiver, String methodName, Object... params) {
        Class<?> clazz = null;
        if (className != null && className.length() > 0) {
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            if (receiver != null) {
                clazz = receiver.getClass();
            }
        }
        if (clazz == null) {
            return null;
        }
        try {
            Method method = findMethod(clazz, methodName, params);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(receiver, params);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Object... arg) {
        Method[] methods = clazz.getMethods();
        Method method = null;
        for (Method m : methods) {
            if (methodFitParam(m, name, arg)) {
                method = m;
                break;
            }
        }

        if (method == null) {
            method = findDeclaredMethod(clazz, name, arg);
        }
        return method;
    }

    private static Method findDeclaredMethod(Class<?> clazz, String name, Object... arg) {
        Method[] methods = clazz.getDeclaredMethods();
        Method method = null;
        for (Method m : methods) {
            if (methodFitParam(m, name, arg)) {
                method = m;
                break;
            }
        }

        if (method == null) {
            if (clazz.equals(Object.class)) {
                return null;
            }
            return findDeclaredMethod(clazz.getSuperclass(), name, arg);
        }
        return method;
    }

    private static boolean methodFitParam(Method method, String methodName, Object... arg) {
        if (!methodName.equals(method.getName())) {
            return false;
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        if (arg == null || arg.length == 0) {
            return paramTypes == null || paramTypes.length == 0;
        }
        if (paramTypes.length != arg.length) {
            return false;
        }

        for (int i = 0; i < arg.length; ++i) {
            Object ar = arg[i];
            Class<?> paramT = paramTypes[i];
            if (ar == null) {
                continue;
            }

            //TODO for primitive type
            if (paramT.isPrimitive()) {
                continue;
            }

            if (!paramT.isInstance(ar)) {
                return false;
            }
        }
        return true;
    }

    private static Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.equals(Object.class)) {
                e.printStackTrace();
                return null;
            }
            Class<?> base = clazz.getSuperclass();
            return findField(base, name);
        }
    }

    //表示Field或者Class是编译器自动生成的
    private static final int SYNTHETIC = 0x00001000;
    //表示Field是final的
    private static final int FINAL = 0x00000010;
    //内部类持有的外部类对象一定有这两个属性
    private static final int SYNTHETIC_AND_FINAL = SYNTHETIC | FINAL;

    private static boolean checkModifier(int mod) {
        return (mod & SYNTHETIC_AND_FINAL) == SYNTHETIC_AND_FINAL;
    }

    //获取内部类实例持有的外部类对象
    public static <T> T getExternalField(Object innerObj) {
        return getExternalField(innerObj, null);
    }

    /**
     * 内部类持有的外部类对象的形式为：
     * final Outer this$0;
     * flags: ACC_FINAL, ACC_SYNTHETIC
     * 参考：https://www.jianshu.com/p/9335c15c43cf
     * And：https://www.2cto.com/kf/201402/281879.html
     *
     * @param innerObj 内部类对象
     * @param name     内部类持有的外部类名称，默认是"this$0"
     * @return 内部类持有的外部类对象
     */
    private static <T> T getExternalField(Object innerObj, String name) {
        Class clazz = innerObj.getClass();
        if (name == null || name.isEmpty()) {
            name = "this$0";
        }
        Field field;
        try {
            field = clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }
        field.setAccessible(true);
        if (checkModifier(field.getModifiers())) {
            try {
                return (T) field.get(innerObj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return getExternalField(innerObj, name + "$");
    }

    //获取当前对象的泛型类  added by xia wanli
    public static Class<?> getParameterizedClassType(Object object) {
        Class<?> clazz;
        //getGenericSuperclass()获得带有泛型的父类
        //Type是 Java 中所有类型的公共高级接口。包括原始类型、参数化类型、数组类型、类型变量和基本类型。
        Type genericSuperclass = object.getClass().getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            //ParameterizedType参数化类型，即泛型
            ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            //getActualTypeArguments获取参数化类型的数组，泛型可能有多个
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            clazz = (Class<?>) actualTypeArguments[0];
        } else {
            clazz = (Class<?>) genericSuperclass;
        }
        return clazz;
    }

    //获取当前对象的泛型类  added by xia wanli
    public static Type getObjectParameterizedType(Object object) {
        //getGenericSuperclass()获得带有泛型的父类
        //Type是 Java 中所有类型的公共高级接口。包括原始类型、参数化类型、数组类型、类型变量和基本类型。
        Type genericSuperclass = object.getClass().getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            //ParameterizedType参数化类型，即泛型
            ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            //getActualTypeArguments获取参数化类型的数组，泛型可能有多个
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            return actualTypeArguments[0];
        } else {
            throw new RuntimeException("Missing type parameter.");
        }
    }
}
