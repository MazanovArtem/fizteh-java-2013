package ru.fizteh.fivt.students.nadezhdakaratsapova.proxy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

public class LoggingInvocationHandler implements InvocationHandler {
    private ThreadLocal<Writer> writer = new ThreadLocal<Writer>();
    private ThreadLocal<Object> implementation = new ThreadLocal<Object>();
    private ThreadLocal<Map<Object, Boolean>> prevArgs = new ThreadLocal<Map<Object, Boolean>>() {
        @Override
        protected Map<Object, Boolean> initialValue() {
            return new IdentityHashMap<Object, Boolean>();
        }
    };

    public LoggingInvocationHandler(Writer writer, Object impl) {
        this.writer.set(writer);
        implementation.set(impl);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result;
        if (method.getDeclaringClass().equals(Object.class)) {
            result = method.invoke(implementation.get(), args);
        } else {
            JSONObject jsonLog = new JSONObject();
            jsonLog.put("timestamp", System.currentTimeMillis());
            jsonLog.put("class", implementation.get().getClass());
            jsonLog.put("method", method.getName());
            JSONArray methodArgs = new JSONArray();
            if (args != null) {
                if (args.getClass().isArray()) {
                    for (Object arg : args) {
                        writeArgument(methodArgs, args);
                    }
                } else {
                    writeArgument(methodArgs, args);
                }

            }
            jsonLog.put("arguments", methodArgs);
            try {
                result = method.invoke(implementation.get(), args);
                if (!method.getReturnType().isAssignableFrom(void.class)) {
                    if (result == null) {
                        jsonLog.put("returnValue", result);
                    } else {
                        JSONArray array = new JSONArray();
                        writeArgument(array, result);
                        jsonLog.put("returnValue", array);

                    }
                } else {
                    jsonLog.put("returnValue", JSONObject.NULL);
                }
                writer.get().write(jsonLog.toString(2));
                writer.get().write(System.lineSeparator());
            } catch (Exception e) {
                jsonLog.put("thrown", e.getClass() + ": " + e.getMessage());
                throw new InvocationTargetException(e);
            }
        }
        return result;
    }

    public void writeArgument(JSONArray cmdArgs, Object arg) {
        if (arg == null) {
            cmdArgs.put(arg);
        } else {
            if (arg instanceof Iterable) {
                for (Object inArg : (Iterable) arg) {
                    if (prevArgs.get().containsKey(inArg)) {
                        cmdArgs.put("cyclic");
                    } else {
                        prevArgs.get().put(inArg, true);
                        writeArgument(new JSONArray(), inArg);
                    }
                }
            } else {
                if (arg.getClass().isArray()) {
                    cmdArgs.put(arg.toString());
                } else {
                    cmdArgs.put(arg);
                }
            }
        }
    }
}
