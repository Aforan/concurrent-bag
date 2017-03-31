package test;

import bag.ConcurrentBag;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by rick on 3/30/17.
 */
public class PerformanceTest {
    final static Logger logger = Logger.getLogger(PerformanceTest.class);

    private static class TestThread extends Thread {
        private static AtomicInteger addCount = new AtomicInteger(0);
        private static AtomicInteger registrationCount = new AtomicInteger(0);

        private static ConcurrentBag<Integer> bag = new ConcurrentBag<>();
        private int threadindex, nthreads, totaloperations;
        private double addratio;

        public TestThread(int threadindex, int nthreads, int totaloperations, double addratio) {
            this.threadindex = threadindex;
            this.nthreads = nthreads;
            this.totaloperations = totaloperations;
            this.addratio = addratio;
        }

        @Override
        public void run() {
            int operations = totaloperations/nthreads;
            Random random = new Random(System.currentTimeMillis());
            boolean isadding = random.nextDouble() < addratio;

            //  Synchronize thread registration
            bag.registerThread();
            registrationCount.getAndIncrement();
            while(registrationCount.get() != nthreads);

            logger.debug(threadindex + " registered");

            //  All threads add some items to start
            for(int i=0; i<1024; i++) {
                try {
                    bag.add(i);
                } catch (ConcurrentBag.NotRegisteredException e) {
                    e.printStackTrace();
                }
            }

            //  Synchronize addition
            addCount.getAndIncrement();
            while(addCount.get() != nthreads);

            logger.debug("Thread " + threadindex + " started");
            startTimes[threadindex] = System.currentTimeMillis();

            for(int i=1024; i<operations; i++) {
                try {
                    if(isadding) {
                        bag.add(i);
                    } else {
                        bag.remove();
                    }

                    if(i != 0 && i/100==0) isadding = !isadding;
                } catch (ConcurrentBag.NotRegisteredException e) {
                    e.printStackTrace();
                    return;
                } catch (ConcurrentBag.CannotStealException e) {
                    logger.debug("Thread " + threadindex + " Cannot Steal Exception");
                    e.printStackTrace();
                }
            }

            endTimes[threadindex] = System.currentTimeMillis();
            logger.debug("Thread " + threadindex + " finished in " + (endTimes[threadindex] - startTimes[threadindex]) + "ms");

            //  Make sure there are enough elements for removers to remove
            if(isadding) {
                for(int i=0; i<1024; i++) {
                    try {
                        bag.add(i);
                    } catch (ConcurrentBag.NotRegisteredException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    private static long executionTime, trueExecutionTime;
    private static long[] startTimes, endTimes;

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers
                .newArgumentParser("ConcurrentBag Performance")
                .defaultHelp(true);

        parser.addArgument("-n").help("Number of threads");
        parser.addArgument("-a").help("Add ratio out of 1.0");
        parser.addArgument("-o").help("Number of operations");

        Namespace ns = null;

        try {
            ns = parser.parseArgs(args);

            int nthreads = Integer.parseInt(ns.getString("n"));
            int nOperations = Integer.parseInt(ns.getString("o"));
            double addRatio = Double.parseDouble(ns.getString("a"));

            if(addRatio > 1.0 || addRatio < 0.0) {
                logger.error("Add ratio out of bounds");
                return;
            } else if(addRatio <= 0.5) {
                logger.warn("Add ratio <= 0.5 " + " not recommended");
            }

            logger.debug("Beginning test: n=" + nthreads + " o=" + nOperations + " a=" + addRatio);
            LinkedList<TestThread> testThreads = new LinkedList<>();

            for(int i=0;i<nthreads;i++) {
                testThreads.add(new TestThread(i, nthreads, nOperations, addRatio));
            }

            startTimes = new long[nthreads];
            endTimes = new long[nthreads];
            executionTime = System.currentTimeMillis();

            for(int i=0;i<nthreads;i++) {
                testThreads.get(i).start();
            }

            long trueStart = -1, trueEnd = -1;
            for(int i=0; i<nthreads; i++) {
                try {
                    testThreads.get(i).join();

                    if(trueStart == -1 || startTimes[i] < trueStart) trueStart = startTimes[i];
                    if(trueEnd == -1 || endTimes[i] > trueEnd) trueEnd = endTimes[i];

                } catch (InterruptedException e) {
                    logger.debug("Outer Thread Exception in thread join");
                    e.printStackTrace();
                }
            }

            trueExecutionTime = trueEnd - trueStart;
            executionTime = System.currentTimeMillis() - executionTime;

            logger.debug("Test Complete");
            logger.debug("Overall Execution Time: " + executionTime + "ms");
            logger.debug("True Execution Time: " + trueExecutionTime + "ms");

        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
