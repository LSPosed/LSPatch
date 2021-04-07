package com.storm.wind.xpatch.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
}
