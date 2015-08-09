package org.evosuite.testcase.fm;

import org.mockito.internal.invocation.InvocationImpl;
import org.mockito.invocation.DescribedInvocation;
import org.mockito.listeners.InvocationListener;
import org.mockito.listeners.MethodInvocationReport;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * During the test generation, we need to know which methods have been called,
 * and how often they were called.
 * This is however not needed in the final generated JUnit tests
 *
 * Created by Andrea Arcuri on 27/07/15.
 */
public class EvoInvocationListener implements InvocationListener, Serializable {

    private final Map<String, MethodDescriptor> map = new LinkedHashMap<>();

    /**
     * By default, we should not log events, otherwise we would end up
     * logging also cases like "when(...)" which are set before a mock is used
     */
    private volatile boolean active = false;

    public void activate(){
        active = true;
    }

    /**
     *
     * @return a sorted list
     */
    public List<MethodDescriptor> getCopyOfMethodDescriptors(){
        return map.values().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public void reportInvocation(MethodInvocationReport methodInvocationReport) {

        if(! active){
            return;
        }

        DescribedInvocation di = methodInvocationReport.getInvocation();
        MethodDescriptor md = null;

        if(di instanceof InvocationImpl){
            InvocationImpl impl = (InvocationImpl) di;
            Method method = impl.getMethod();
            md = new MethodDescriptor(method);
        } else {
            //hopefully it should never happen
            md = getMethodDescriptor_old(di);
        }

        synchronized (map){
            MethodDescriptor current = map.get(md.getID());
            if(current == null){
                current = md;
            }
            current.increaseCounter();
            map.put(md.getID(),current);
        }
    }

    @Deprecated
    private MethodDescriptor getMethodDescriptor_old(DescribedInvocation di) {
    /*
        Current Mockito API seems quite limited. Here, to know what
        was called, it looks like the only way is to parse the results
        of toString.
        We can identify primitive types and String, but likely not the
        exact type of input objects. This is a problem if methods are overloaded
        and having same number of input parameters :(
     */
        String description = di.toString();

        int openingP = description.indexOf('(');
        assert openingP >= 0;

        String[] leftTokens = description.substring(0,openingP).split("\\.");
        String className = ""; //TODO
        String methodName = leftTokens[leftTokens.length-1];

        int closingP = description.lastIndexOf(')');
        String[] inputTokens = description.substring(openingP+1, closingP).split(",");

        String mockitoMatchers = "";
        if(inputTokens.length > 0) {
            /*
                TODO: For now it does not seem really feasible to infer the correct types.
                Left a feature request on Mockito mailing list, let's see if it ll be done
             */
            mockitoMatchers += "any()";
            for (int i=1; i<inputTokens.length; i++) {
                mockitoMatchers += " , any()";
            }
        }


        return new MethodDescriptor(className,methodName,mockitoMatchers);
    }
}
