package test;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.LinkedList;

/**
 * Created by rick on 3/30/17.
 */
public class PerformanceTest {
    final static Logger logger = Logger.getLogger(PerformanceTest.class);

    private static class TestThread extends Thread {

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
            executionTimes[threadindex] = System.currentTimeMillis();

            //  do stuff

            executionTimes[threadindex] = System.currentTimeMillis() - executionTimes[threadindex];
            logger.debug("Thread " + threadindex + " finished in " + executionTimes[threadindex] + "ms");
        }
    }

    private static long executionTime, trueExecutionTime;
    private static long[] executionTimes;

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("ConcurrentBag Performance")
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

            logger.debug("Beginning test: n=" + nthreads + " o=" + nOperations + " a=" + addRatio);
            LinkedList<TestThread> testThreads = new LinkedList<>();

            for(int i=0;i<nthreads;i++) {
                testThreads.add(new TestThread(i, nthreads, nOperations, addRatio));
            }

            executionTimes = new long[nthreads];
            executionTime = System.currentTimeMillis();
            trueExecutionTime = 0;

            for(int i=0;i<nthreads;i++) {
                testThreads.get(i).run();
            }

            for(int i=0; i<nthreads; i++) {
                try {
                    testThreads.get(i).join();
                    trueExecutionTime += executionTimes[i];
                } catch (InterruptedException e) {
                    logger.debug("Outer Thread Exception in thread join");
                    e.printStackTrace();
                }
            }

            executionTime = System.currentTimeMillis() - executionTime;

            logger.debug("Test Complete");
            logger.debug("Overall Execution Time: " + executionTime);
            logger.debug("True Execution Time: " + trueExecutionTime);

        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
