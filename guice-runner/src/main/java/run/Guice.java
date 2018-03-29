package run;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.lang.reflect.Method;
import com.google.inject.Stage;
import com.google.inject.Module;
import com.google.inject.Injector;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.lang.reflect.InvocationTargetException;

public class Guice {
    public static void help() {
        System.out.println("java -jar run.Guice -Mpkg.MyModule1=stringArg -Mpkg.MyModule2 pkg.InjectClass.method=stringArg");
    }

    // -Mcom.distelli.MyModule=stringConstructorArg com.distelli.InstanceToInject.method=stringArg
    public static void main(String[] argArray) throws Throwable {
        List<String> args = Arrays.asList(argArray);
        if ( args.size() < 1 ) {
            help();
            return;
        }
        List<Module> modules = new ArrayList<>();
        int argIdx = 0;
        for (; argIdx < args.size(); argIdx++ ) {
            String arg = args.get(argIdx);
            if ( ! arg.startsWith("-M") ) break;
            String[] pair = arg.substring(2).split("=", 2);
            Class<?> clazz = Class.forName(pair[0]);
            if ( pair.length < 2 ) {
                try {
                    modules.add((Module)clazz.newInstance());
                } catch ( Exception ex ) {
                    throw new Exception("new "+clazz+"() failed: "+ex.getMessage(), ex);
                }
            } else {
                try {
                    modules.add((Module)clazz.getConstructor(String.class).newInstance(pair[1]));
                } catch ( Exception ex ) {
                    throw new Exception("new "+clazz+"(String) failed: "+ex.getMessage(), ex);
                }
            }
        }


        Injector injector = com.google.inject.Guice.createInjector(
            Stage.PRODUCTION,
            modules.toArray(new Module[modules.size()]));

        Map<String, Callable<?>> runs = new LinkedHashMap<>();
        for ( int i=0; argIdx+i < args.size(); i++ ) {
            String[] pair = args.get(argIdx+i).split("=", 2);
            int dot = pair[0].lastIndexOf('.');
            if ( dot < 0 ) {
                throw new IllegalArgumentException("Expected <class>.<method>=value");
            }
            String className = pair[0].substring(0, dot);
            String methodName = pair[0].substring(dot+1);

            Object instance = injector.getInstance(Class.forName(className));
            Method method;
            if ( pair.length > 1 ) {
                method = instance.getClass().getMethod(methodName, String.class);
            } else {
                method = instance.getClass().getMethod(methodName);
            }
            String threadName = toThreadName(runs::containsKey, className, methodName, i);
            Method finalMethod = method;
            runs.put(threadName, () -> {
                    try {
                        if ( pair.length > 1 ) {
                            return method.invoke(instance, pair[1]);
                        } else {
                            return method.invoke(instance);
                        }
                    } catch ( InvocationTargetException ex ) {
                        if ( ex.getCause() instanceof Exception ) {
                            throw (Exception)ex.getCause();
                        }
                        throw ex;
                    }
                });
        }

        int exitCode = 0;
        Map<String, Object> results = run(injector, runs);
        for ( String threadName : runs.keySet() ) {
            Object result = results.get(threadName);
            if ( result instanceof Throwable ) {
                exitCode = -1;
                System.err.println("FATAL["+threadName+"]");
                ((Throwable)result).printStackTrace(System.err);
            } else if ( null != result ) {
                System.out.println(result.toString());
            }
        }
        System.exit(exitCode);
    }


    private static Map<String, Object> run(Injector injector, Map<String, Callable<?>> runs) throws InterruptedException {
        ArrayBlockingQueue<Map.Entry<String, Object>> queue = new ArrayBlockingQueue<>(runs.size());
        Map<String, Thread> allThreads = new HashMap<>();
        for ( Map.Entry<String, Callable<?>> runEntry : runs.entrySet() ) {
            Thread thread = new Thread(
                () -> {
                    Object result = null;
                    try {
                        try {
                            Thread.currentThread().setName(runEntry.getKey());
                            result = runEntry.getValue().call();
                        } catch ( Throwable ex ) {
                            if ( ! ( ex instanceof InterruptedException ) ) {
                                result = ex;
                            }
                        }
                    } finally {
                        try {
                            Map.Entry entry = new SimpleImmutableEntry(
                                runEntry.getKey(),
                                result);
                            for ( int retry=0;; retry++ ) {
                                try {
                                    queue.put(entry);
                                    break;
                                } catch ( InterruptedException ex ) {
                                    if ( retry > 3 ) throw ex;
                                }
                                // simply retry on interruptions...
                            }
                        } catch ( Throwable ex ) {
                            System.err.println("FATAL["+runEntry.getKey()+"]");
                            ex.printStackTrace(System.err);
                            System.exit(-1); // force shutdown of process...
                        }
                    }
                });
            allThreads.put(runEntry.getKey(), thread);
            thread.start();
        }
        Map<String, Object> results = new HashMap<>();
        while ( ! allThreads.isEmpty() ) {
            Map.Entry<String, Object> resultEntry = queue.take();
            results.put(resultEntry.getKey(), resultEntry.getValue());
            allThreads.remove(resultEntry.getKey());
            if ( resultEntry.getValue() instanceof Throwable ) {
                if ( ! stopThreads(allThreads.values()) ) {
                    break;
                }
            }
        }
        return results;
    }

    private static String toThreadName(Predicate<Object> exists, String className, String methodName, int index) {
        int dot = className.lastIndexOf('.');
        String threadName = ( dot >= 0 ) ? className.substring(dot+1) : className;
        if ( ! exists.test(threadName) ) return threadName;
        threadName = threadName + "." + methodName;
        if ( ! exists.test(threadName) ) return threadName;
        threadName = threadName + "." + index;
        if ( exists.test(threadName) ) {
            throw new IllegalStateException("Unable to come up with unique threadName!");
        }
        return threadName;
    }

    private static long timeMillis() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    private static final long MAX_WAIT_MILLIS = 10000;

    private static boolean stopThreads(Collection<Thread> allThreads) throws InterruptedException {
        for ( Thread thread : allThreads ) {
            thread.interrupt();
        }
        boolean success = true;
        long waitTime = MAX_WAIT_MILLIS;
        for ( Thread thread : allThreads ) {
            long startTime = timeMillis();
            thread.join(waitTime);
            waitTime -= (timeMillis() - startTime);
            if ( waitTime < 1 ) {
                success = false;
                break;
            }
        }
        return success;
    }

}
