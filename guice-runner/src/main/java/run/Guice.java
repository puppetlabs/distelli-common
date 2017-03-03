package run;

import com.google.inject.Stage;
import com.google.inject.Module;
import com.google.inject.Injector;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class Guice {
    public static void help() {
        System.out.println("java -jar run.Guice -Mpkg.MyModule1=stringArg -Mpkg.MyModule2 pkg.InjectClass.method=stringArg");
    }

    // -Mcom.distelli.MyModule=stringConstructorArg com.distelli.InstanceToInject.method=stringArg
    public static void main(String[] argArray) throws Exception {
        List<String> args = new ArrayList<>(Arrays.asList(argArray));
        if ( args.size() < 1 ) {
            help();
            return;
        }
        String lastArg = args.remove(args.size()-1);
        List<Module> modules = new ArrayList<>();
        for ( String arg : args ) {
            if ( arg.startsWith("-M") ) {
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
            } else {
                throw new IllegalArgumentException(arg);
            }
        }


        Injector injector = com.google.inject.Guice.createInjector(
            Stage.PRODUCTION,
            modules.toArray(new Module[modules.size()]));
 
        String[] pair = lastArg.split("=", 2);
        int dot = pair[0].lastIndexOf('.');

        Object instance = injector.getInstance(Class.forName(pair[0].substring(0, dot)));
        String method = pair[0].substring(dot+1);
        Object result;
        if ( pair.length < 2 ) {
            result = instance.getClass().getMethod(method).invoke(instance);
        } else {
            result = instance.getClass().getMethod(method, String.class).invoke(instance, pair[1]);
        }
        if ( null != result ) System.out.println(result.toString());
    }
}
