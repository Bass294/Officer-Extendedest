package data.scripts.ui;

import com.fs.starfarer.api.Global;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal reflection for reaching vanilla UI internals. The game's mod classloader blocks java.lang.reflect by class
 * name, so the reflection classes are loaded through the bootstrap loader and only ever held as Object, operated on
 * through MethodHandles (the technique of the ReflectionUtils most mods ship). Methods are found by readable name
 * and parameter count, never by an obfuscated name or type.
 */
public class oee_Reflection {

    private static final MethodHandle GET_METHODS;
    private static final MethodHandle METHOD_NAME;
    private static final MethodHandle METHOD_PARAMETER_COUNT;
    private static final MethodHandle METHOD_INVOKE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, null);
            Class<?> methodArrayClass = Class.forName("[Ljava.lang.reflect.Method;", false, null);
            GET_METHODS = lookup.findVirtual(Class.class, "getMethods", MethodType.methodType(methodArrayClass));
            METHOD_NAME = lookup.findVirtual(methodClass, "getName", MethodType.methodType(String.class));
            METHOD_PARAMETER_COUNT = lookup.findVirtual(methodClass, "getParameterCount", MethodType.methodType(int.class));
            METHOD_INVOKE = lookup.findVirtual(methodClass, "invoke",
                    MethodType.methodType(Object.class, Object.class, Object[].class));
        } catch (Exception e) {
            throw new RuntimeException("Officer Extendedest: couldn't set up reflection", e);
        }
    }

    private static final Map<String, Object> cache = new HashMap<String, Object>();

    public static Class<?> gameClass(String name) {
        try {
            return Class.forName(name, false, Global.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Officer Extendedest: game class " + name + " not found", e);
        }
    }

    /** A public method (own or inherited) by name and parameter count; the first match. */
    public static Object findMethod(Class<?> cls, String name, int parameterCount) {
        String key = cls.getName() + "#" + name + "/" + parameterCount;
        Object method = cache.get(key);
        if (method != null) return method;
        try {
            for (Object m : (Object[]) GET_METHODS.invoke(cls)) {
                if (name.equals(METHOD_NAME.invoke(m)) && (int) METHOD_PARAMETER_COUNT.invoke(m) == parameterCount) {
                    cache.put(key, m);
                    return m;
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Officer Extendedest: couldn't list methods of " + cls.getName(), t);
        }
        throw new RuntimeException("Officer Extendedest: no method " + name + " with " + parameterCount
                + " parameters on " + cls.getName());
    }

    public static Object invoke(String name, Object instance, Object... args) {
        return call(findMethod(instance.getClass(), name, args.length), instance, args);
    }

    public static Object invokeStatic(Class<?> cls, String name, Object... args) {
        return call(findMethod(cls, name, args.length), null, args);
    }

    private static Object call(Object method, Object instance, Object[] args) {
        try {
            return METHOD_INVOKE.invoke(method, instance, args);
        } catch (Throwable t) {
            throw new RuntimeException("Officer Extendedest: reflective call failed", t);
        }
    }
}
