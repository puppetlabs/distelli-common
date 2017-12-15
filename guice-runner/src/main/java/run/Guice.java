package run;

import com.google.inject.Stage;
import com.google.inject.Module;
import com.google.inject.Injector;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

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

        int queueSize = args.size() - argIdx;
        ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(queueSize);
        for (; argIdx < args.size(); argIdx++ ) {
            String[] pair = args.get(argIdx).split("=", 2);
            int dot = pair[0].lastIndexOf('.');

            Object instance = injector.getInstance(Class.forName(pair[0].substring(0, dot)));
            String methodName = pair[0].substring(dot+1);

            new Thread(() -> {
                    Thread.currentThread().setName(pair[0]);
                    try {
                        if ( pair.length < 2 ) {
                            queue.put(
                                instance.getClass()
                                .getMethod(methodName)
                                .invoke(instance));
                        } else {
                            queue.put(
                                instance.getClass()
                                .getMethod(methodName, String.class)
                                .invoke(instance, pair[1]));
                        }
                    } catch ( Throwable ex ) {
                        try {
                            queue.put(ex);
                        } catch ( Throwable ignored ) {
                            ex.printStackTrace();
                            System.exit(-1);
                        }
                    }
            }).start();
        }
        while ( queueSize-- > 0 ) {
            Object result = queue.take();
            if ( result instanceof Throwable ) {
                throw (Throwable)result;
            } else if ( null != result ) {
                System.out.println(result.toString());
            }
        }
    }
}
