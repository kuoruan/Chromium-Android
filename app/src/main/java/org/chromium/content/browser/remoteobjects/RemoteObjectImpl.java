// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.remoteobjects;

import org.chromium.blink.mojom.RemoteInvocationArgument;
import org.chromium.blink.mojom.RemoteInvocationError;
import org.chromium.blink.mojom.RemoteInvocationResult;
import org.chromium.blink.mojom.RemoteObject;
import org.chromium.blink.mojom.SingletonJavaScriptValue;
import org.chromium.mojo.system.MojoException;
import org.chromium.mojo_base.mojom.String16;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Exposes limited access to a Java object over a Mojo interface.
 *
 * For LiveConnect references, an archived version is available at:
 * http://web.archive.org/web/20141022204935/http://jdk6.java.net/plugin2/liveconnect/
 */
class RemoteObjectImpl implements RemoteObject {
    /**
     * Receives notification about events for auditing.
     *
     * Separated from this class proper to allow for unit testing in content_junit_tests, where the
     * Android framework is not fully initialized.
     *
     * Implementations should take care not to hold a strong reference to anything that might keep
     * the WebView contents alive due to a GC cycle.
     */
    interface Auditor {
        void onObjectGetClassInvocationAttempt();
    }

    /**
     * Method which may not be called.
     */
    private static final Method sGetClassMethod;
    static {
        try {
            sGetClassMethod = Object.class.getMethod("getClass");
        } catch (NoSuchMethodException e) {
            // java.lang.Object#getClass should always exist.
            throw new RuntimeException(e);
        }
    }

    /**
     * The object to which invocations should be directed.
     *
     * The target object cannot be referred to strongly, because it may contain
     * references which form an uncollectable cycle.
     */
    private final WeakReference<Object> mTarget;

    /**
     * Receives notification about events for auditing.
     */
    private final Auditor mAuditor;

    /**
     * Callable methods, indexed by name.
     */
    private final SortedMap<String, List<Method>> mMethods = new TreeMap<>();

    public RemoteObjectImpl(Object target, Class<? extends Annotation> safeAnnotationClass) {
        this(target, safeAnnotationClass, null);
    }

    public RemoteObjectImpl(
            Object target, Class<? extends Annotation> safeAnnotationClass, Auditor auditor) {
        mTarget = new WeakReference<>(target);
        mAuditor = auditor;

        for (Method method : target.getClass().getMethods()) {
            if (safeAnnotationClass != null && !method.isAnnotationPresent(safeAnnotationClass)) {
                continue;
            }

            String methodName = method.getName();
            List<Method> methodsWithName = mMethods.get(methodName);
            if (methodsWithName == null) {
                methodsWithName = new ArrayList<>(1);
                mMethods.put(methodName, methodsWithName);
            }
            methodsWithName.add(method);
        }
    }

    @Override
    public void hasMethod(String name, HasMethodResponse callback) {
        callback.call(mMethods.containsKey(name));
    }

    @Override
    public void getMethods(GetMethodsResponse callback) {
        Set<String> methodNames = mMethods.keySet();
        callback.call(methodNames.toArray(new String[methodNames.size()]));
    }

    @Override
    public void invokeMethod(
            String name, RemoteInvocationArgument[] arguments, InvokeMethodResponse callback) {
        Object target = mTarget.get();
        if (target == null) {
            // TODO(jbroman): Handle this.
            return;
        }

        int numArguments = arguments.length;
        Method method = findMethod(name, numArguments);
        if (method == null) {
            callback.call(makeErrorResult(RemoteInvocationError.METHOD_NOT_FOUND));
            return;
        }
        if (method.equals(sGetClassMethod)) {
            if (mAuditor != null) {
                mAuditor.onObjectGetClassInvocationAttempt();
            }
            callback.call(makeErrorResult(RemoteInvocationError.OBJECT_GET_CLASS_BLOCKED));
            return;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[numArguments];
        for (int i = 0; i < numArguments; i++) {
            args[i] = convertArgument(arguments[i], parameterTypes[i]);
        }

        Object result = null;
        try {
            result = method.invoke(target, args);
        } catch (IllegalAccessException | IllegalArgumentException | NullPointerException e) {
            // These should never happen.
            //
            // IllegalAccessException:
            //   java.lang.Class#getMethods returns only public members, so |mMethods| should never
            //   contain any method for which IllegalAccessException would be thrown.
            //
            // IllegalArgumentException:
            //   Argument coercion logic is responsible for creating objects of a suitable Java
            //   type.
            //   TODO(jbroman): Actually write said coercion logic.
            //
            // NullPointerException:
            //   A user of this class is responsible for ensuring that the target is not collected.
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
            callback.call(makeErrorResult(RemoteInvocationError.EXCEPTION_THROWN));
            return;
        }

        RemoteInvocationResult mojoResult = convertResult(result, method.getReturnType());
        callback.call(mojoResult);
    }

    @Override
    public void close() {
        // TODO(jbroman): Handle this.
    }

    @Override
    public void onConnectionError(MojoException e) {
        close();
    }

    private Method findMethod(String name, int numParameters) {
        List<Method> methods = mMethods.get(name);
        if (methods == null) {
            return null;
        }

        // LIVECONNECT_COMPLIANCE: We just take the first method with the correct
        // number of arguments, while the spec proposes using cost-based algorithm:
        // https://jdk6.java.net/plugin2/liveconnect/#OVERLOADED_METHODS
        for (Method method : methods) {
            if (method.getParameterTypes().length == numParameters) return method;
        }

        return null;
    }

    private Object convertArgument(RemoteInvocationArgument argument, Class<?> parameterType) {
        switch (argument.which()) {
            case RemoteInvocationArgument.Tag.NumberValue:
                // See http://jdk6.java.net/plugin2/liveconnect/#JS_NUMBER_VALUES.
                // For conversion to numeric types, we need to replicate Java's type
                // conversion rules.
                double numberValue = argument.getNumberValue();
                if (parameterType == byte.class) {
                    return (byte) numberValue;
                } else if (parameterType == char.class) {
                    if (isInt32(numberValue)) {
                        return (char) numberValue;
                    } else {
                        // LIVECONNECT_COMPLIANCE: Existing behavior is to convert double to 0.
                        // Spec requires converting doubles similarly to how we convert doubles to
                        // other numeric types.
                        return (char) 0;
                    }
                } else if (parameterType == short.class) {
                    return (short) numberValue;
                } else if (parameterType == int.class) {
                    return (int) numberValue;
                } else if (parameterType == long.class) {
                    return (long) numberValue;
                } else if (parameterType == float.class) {
                    return (float) numberValue;
                } else if (parameterType == double.class) {
                    return numberValue;
                } else if (parameterType == boolean.class) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to false. Spec
                    // requires converting to false for 0 or NaN, true otherwise.
                    return false;
                } else if (parameterType == String.class) {
                    // TODO(jbroman): Don't coerce to string if this is inside the conversion of an
                    // array.
                    return doubleToString(numberValue);
                } else if (parameterType.isArray()) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to null. Spec
                    // requires raising a JavaScript exception.
                    return null;
                } else {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to null. Spec
                    // requires handling object equivalents of primitive types.
                    assert !parameterType.isPrimitive();
                    return null;
                }
            case RemoteInvocationArgument.Tag.BooleanValue:
                // See http://jdk6.java.net/plugin2/liveconnect/#JS_BOOLEAN_VALUES.
                boolean booleanValue = argument.getBooleanValue();
                if (parameterType == boolean.class) {
                    return booleanValue;
                } else if (parameterType.isPrimitive()) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to 0 for all
                    // non-boolean primitive types. Spec requires converting to 0 or 1.
                    return getPrimitiveZero(parameterType);
                } else if (parameterType == String.class) {
                    return Boolean.toString(booleanValue);
                } else if (parameterType.isArray()) {
                    return null;
                } else {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to NULL. Spec
                    // requires handling java.lang.Boolean and java.lang.Object.
                    assert !parameterType.isPrimitive();
                    return null;
                }
            case RemoteInvocationArgument.Tag.StringValue:
                // See http://jdk6.java.net/plugin2/liveconnect/#JS_STRING_VALUES.
                if (parameterType == String.class) {
                    return mojoStringToJavaString(argument.getStringValue());
                } else if (parameterType.isPrimitive()) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to 0. Spec
                    // requires using valueOf() method of corresponding object type, or
                    // converting to boolean based on whether the string is empty.
                    return getPrimitiveZero(parameterType);
                } else if (parameterType.isArray()) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to NULL. Spec
                    // requires raising a JavaScript exception.
                    return null;
                } else {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to NULL. Spec
                    // requires handling java.lang.Object.
                    return null;
                }
            case RemoteInvocationArgument.Tag.SingletonValue:
                int singletonValue = argument.getSingletonValue();
                boolean isUndefined = singletonValue == SingletonJavaScriptValue.UNDEFINED;
                if (parameterType == String.class) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert undefined to
                    // "undefined". Spec requires converting undefined to NULL.
                    // TODO(jbroman): Don't coerce undefined to string if this is inside the
                    // conversion of an array.
                    return argument.getSingletonValue() == SingletonJavaScriptValue.UNDEFINED
                            ? "undefined"
                            : null;
                } else if (parameterType.isPrimitive()) {
                    return getPrimitiveZero(parameterType);
                } else if (parameterType.isArray()) {
                    // LIVECONNECT_COMPLIANCE: Existing behavior is to convert to NULL. Spec
                    // requires raising a JavaScript exception.
                    return null;
                } else {
                    return null;
                }
            default:
                throw new RuntimeException("invalid wire argument type");
        }
    }

    private RemoteInvocationResult convertResult(Object result, Class<?> returnType) {
        // TODO(jbroman): Convert result.
        return new RemoteInvocationResult();
    }

    private static RemoteInvocationResult makeErrorResult(int error) {
        assert error != RemoteInvocationError.OK;
        RemoteInvocationResult result = new RemoteInvocationResult();
        result.error = error;
        return result;
    }

    /**
     * Returns whether the value is an Int32 in the V8 API sense.
     * That is, it has an integer value in [-2^31, 2^31) and is not negative zero.
     */
    private static boolean isInt32(double doubleValue) {
        return doubleValue % 1.0 == 0.0 && doubleValue >= Integer.MIN_VALUE
                && doubleValue <= Integer.MAX_VALUE
                && (doubleValue != 0.0 || (1.0 / doubleValue) > 0.0);
    }

    private static String doubleToString(double doubleValue) {
        // For compatibility, imitate the existing behavior.
        // The previous implementation applied Int64ToString to any integer that fit in 32 bits,
        // except for negative zero, and base::StringPrintf("%.6lg", doubleValue) for all other
        // values.
        if (Double.isNaN(doubleValue)) {
            return "nan";
        }
        if (Double.isInfinite(doubleValue)) {
            return doubleValue > 0 ? "inf" : "-inf";
        }
        // Negative zero is mathematically an integer, but keeps its negative sign.
        if (doubleValue == 0.0 && (1.0 / doubleValue) < 0.0) {
            return "-0";
        }
        // All other 32-bit signed integers are formatted without abbreviation.
        if (doubleValue % 1.0 == 0.0 && doubleValue >= Integer.MIN_VALUE
                && doubleValue <= Integer.MAX_VALUE) {
            return Integer.toString((int) doubleValue);
        }
        // Remove trailing zeroes and, if appropriate, the decimal point.
        // Expression is somewhat complicated, in order to deal with scientific notation. Either
        // group 2 will match (and so the decimal will be stripped along with zeroes), or group 3
        // will match (and the decimal will be left), but not both (since there cannot be more than
        // one decimal point).
        return String.format((Locale) null, "%.6g", doubleValue)
                .replaceFirst("^(-?[0-9]+)(\\.0+)?((\\.[0-9]*[1-9])0*)?(e.*)?$", "$1$4$5");
    }

    private static Object getPrimitiveZero(Class<?> parameterType) {
        assert parameterType.isPrimitive();
        if (parameterType == boolean.class) {
            return false;
        } else if (parameterType == byte.class) {
            return (byte) 0;
        } else if (parameterType == char.class) {
            return (char) 0;
        } else if (parameterType == short.class) {
            return (short) 0;
        } else if (parameterType == int.class) {
            return (int) 0;
        } else if (parameterType == long.class) {
            return (long) 0;
        } else if (parameterType == float.class) {
            return (float) 0;
        } else if (parameterType == double.class) {
            return (double) 0;
        } else {
            throw new RuntimeException("unexpected primitive type " + parameterType);
        }
    }

    private static String mojoStringToJavaString(String16 mojoString) {
        short[] data = mojoString.data;
        char[] chars = new char[data.length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) data[i];
        }
        return String.valueOf(chars);
    }
}
