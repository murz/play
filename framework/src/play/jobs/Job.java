package play.jobs;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import play.Play;
import play.Invoker;
import play.Logger;
import play.exceptions.JavaExecutionException;
import play.exceptions.PlayException;
import play.libs.Time;

/**
 * A job is an asynchronously executed unit of work
 * @param <V> The job result type (if any)
 */
public class Job<V> extends Invoker.Invocation implements Callable<V> {

    protected ExecutorService executor;
    protected long lastRun = 0;
    protected boolean wasError = false;
    protected Throwable lastException = null;

    /**
     * Here you do the job
     */
    public void doJob() throws Exception {
    }

    /**
     * Here you do the job and return a result
     */
    public V doJobWithResult() throws Exception {
        doJob();
        return null;
    }

    @Override
    public void execute() throws Exception {
    }

    /**
     * Start this job now (well ASAP)
     * @return the job completion
     */
    public Future<V> now() {
        return JobsPlugin.executor.submit((Callable<V>) this);
    }

    /**
     * Start this job in several seconds
     * @return the job completion
     */
    public Future<V> in(String delay) {
        return in(Time.parseDuration(delay));
    }

    /**
     * Start this job in several seconds
     * @return the job completion
     */
    public Future<V> in(int seconds) {
        return JobsPlugin.executor.schedule((Callable<V>) this, seconds, TimeUnit.SECONDS);
    }

    /**
     * Run this job every n seconds
     */
    public void every(String delay) {
        every(Time.parseDuration(delay));
    }

    /**
     * Run this job every n seconds
     */
    public void every(int seconds) {
        JobsPlugin.executor.scheduleWithFixedDelay(this, seconds, seconds, TimeUnit.SECONDS);
    }
    
    // Customize Invocation
    @Override
    public void onException(Throwable e) {
        wasError = true;
        lastException = e;
        try {
            super.onException(e);
        } catch(Throwable ex) {
            Logger.error(ex, "Error during job execution (%s)", this);
        }
    }

    @Override
    public void run() {
        call();
    }

    public V call() {
        Monitor monitor = null;
        try {
            if (init()) {
                before();
                V result = null;
                try {
                    lastException = null;
                    lastRun = System.currentTimeMillis();
                    monitor = MonitorFactory.start(getClass().getName()+".doJob()");
                    result = doJobWithResult();
                    monitor.stop();
                    monitor = null;
                    wasError = false;
                } catch (PlayException e) {
                    throw e;
                } catch (Exception e) {
                    StackTraceElement element = PlayException.getInterestingStrackTraceElement(e);
                    if (element != null) {
                        throw new JavaExecutionException(Play.classes.getApplicationClass(element.getClassName()), element.getLineNumber(), e);
                    }
                    throw e;
                }
                after();
                return result;
            }
        } catch (Throwable e) {
            onException(e);
        } finally {
            if(monitor != null) {
                monitor.stop();
            }
            _finally();
        }
        return null;
    }

    @Override
    public void _finally() {
        super._finally();
        if (executor == JobsPlugin.executor) {
            JobsPlugin.scheduleForCRON(this);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }
}
