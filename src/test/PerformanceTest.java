package test;

import bag.ConcurrentBag;
import bag.LeakyConcurrentBag;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by rick on 3/30/17.
 */
public class PerformanceTest {
    final static Logger logger = Logger.getLogger(PerformanceTest.class);

    private static class TransferQueueTestThread extends Thread {
        private static LinkedTransferQueue<Integer> linkedQueue = new LinkedTransferQueue<>();

        private int threadindex, nthreads, totaloperations;
        private double addratio;

        public TransferQueueTestThread(int threadindex, int nthreads, int totaloperations, double addratio) {
            this.threadindex = threadindex;
            this.nthreads = nthreads;
            this.totaloperations = totaloperations;
            this.addratio = addratio;

            if(this.threadindex == 0) {
                startTimes = new long[nthreads];
            }
        }

        @Override
        public void run() {
            int noperations = totaloperations/nthreads;
            boolean isadding = threadindex < addratio * nthreads;

            startTimes[threadindex] = System.currentTimeMillis();

            for(int i=0; i<noperations; i++) {
                if(isadding) {
                    linkedQueue.put(i);
                } else {
                    linkedQueue.poll();
                }
            }
            endTimes[threadindex] = System.currentTimeMillis();
        }
    }


    private static class ConcurrentQueueTestThread extends Thread {
        private static ConcurrentLinkedQueue<Integer> linkedQueue = new ConcurrentLinkedQueue<>();

        private int threadindex, nthreads, totaloperations;
        private double addratio;

        public ConcurrentQueueTestThread(int threadindex, int nthreads, int totaloperations, double addratio) {
            this.threadindex = threadindex;
            this.nthreads = nthreads;
            this.totaloperations = totaloperations;
            this.addratio = addratio;

            if(this.threadindex == 0) {
                startTimes = new long[nthreads];
            }
        }

        @Override
        public void run() {
            int noperations = totaloperations/nthreads;
            boolean isadding = threadindex < addratio * nthreads;

            startTimes[threadindex] = System.currentTimeMillis();

            for(int i=0; i<noperations; i++) {
                int counter = 0;
                int trigger = isadding ? (int) (1024.0 * addratio) : (int) (1024.0 * (1.0 - addratio));

                if(isadding) {
                    linkedQueue.add(i);
                } else {
                    linkedQueue.remove();
                }
                if(i != 0 && (counter == trigger || counter == 1024)) {
                    isadding = !isadding;

                    if(counter == 1024) counter = 0;
                    else counter++;

                } else {
                    counter++;
                }
            }
            endTimes[threadindex] = System.currentTimeMillis();
        }
    }

    private static class ConcurrentBagTestThread extends Thread {
        private static AtomicInteger addCount = new AtomicInteger(0);
        private static AtomicInteger registrationCount = new AtomicInteger(0);

        private static ConcurrentBag<Integer> bag = new ConcurrentBag<>();
        private int threadindex, nthreads, totaloperations;
        private double addratio;

        public ConcurrentBagTestThread(int threadindex, int nthreads, int totaloperations, double addratio) {
            this.threadindex = threadindex;
            this.nthreads = nthreads;
            this.totaloperations = totaloperations;
            this.addratio = addratio;
        }

        @Override
        public void run() {
            int operations = totaloperations/nthreads;
            boolean isadding = threadindex < addratio * nthreads;

            //  Synchronize thread registration
            bag.registerThread();
            registrationCount.getAndIncrement();
            while(registrationCount.get() != nthreads);

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

            startTimes[threadindex] = System.currentTimeMillis();

            for(int i=1024; i<operations; i++) {
                int counter = 0;
                int trigger = isadding ? (int) (1024.0 * addratio) : (int) (1024.0 * (1.0 - addratio));

                try {
                    if(isadding) {
                        bag.add(i);
                    } else {
                        bag.remove();
                    }

                    if(i != 0 && (counter == trigger || counter == 1024)) {
                        isadding = !isadding;

                        if(counter == 1024) counter = 0;
                        else counter++;

                    } else {
                        counter++;
                    }

                } catch (ConcurrentBag.NotRegisteredException e) {
                    e.printStackTrace();
                    return;
                } catch (ConcurrentBag.CannotStealException e) {
                    logger.debug("Thread " + threadindex + " Cannot Steal Exception");
                    e.printStackTrace();
                }
            }

            endTimes[threadindex] = System.currentTimeMillis();
            //logger.debug("Thread " + threadindex + " finished in " + (endTimes[threadindex] - startTimes[threadindex]) + "ms");

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

    private static class LeakyConcurrentBagTestThread extends Thread {
        private static AtomicInteger addCount = new AtomicInteger(0);
        private static AtomicInteger registrationCount = new AtomicInteger(0);

        private static LeakyConcurrentBag<Integer> bag = new LeakyConcurrentBag<>();
        private int threadindex, nthreads, totaloperations;
        private double addratio;

        public LeakyConcurrentBagTestThread(int threadindex, int nthreads, int totaloperations, double addratio) {
            this.threadindex = threadindex;
            this.nthreads = nthreads;
            this.totaloperations = totaloperations;
            this.addratio = addratio;
        }

        @Override
        public void run() {
            int operations = totaloperations/nthreads;
            boolean isadding = threadindex < addratio * nthreads;

            //  Synchronize thread registration
            bag.registerThread();
            registrationCount.getAndIncrement();
            while(registrationCount.get() != nthreads);

            //  All threads add some items to start
            for(int i=0; i<1024; i++) {
                try {
                    bag.add(i);
                } catch (LeakyConcurrentBag.NotRegisteredException e) {
                    e.printStackTrace();
                }
            }

            //  Synchronize addition
            addCount.getAndIncrement();
            while(addCount.get() != nthreads);

            startTimes[threadindex] = System.currentTimeMillis();

            for(int i=1024; i<operations; i++) {
                int counter = 0;
                int trigger = isadding ? (int) (1024.0 * addratio) : (int) (1024.0 * (1.0 - addratio));

                try {
                    if(isadding) {
                        bag.add(i);
                    } else {
                        bag.remove();
                    }

                    if(i != 0 && (counter == trigger || counter == 1024)) {
                        isadding = !isadding;

                        if(counter == 1024) counter = 0;
                        else counter++;

                    } else {
                        counter++;
                    }

                } catch (LeakyConcurrentBag.NotRegisteredException e) {
                    e.printStackTrace();
                    return;
                } catch (LeakyConcurrentBag.CannotStealException e) {
                    logger.debug("Thread " + threadindex + " Cannot Steal Exception");
                    e.printStackTrace();
                }
            }

            endTimes[threadindex] = System.currentTimeMillis();
            //logger.debug("Thread " + threadindex + " finished in " + (endTimes[threadindex] - startTimes[threadindex]) + "ms");

            //  Make sure there are enough elements for removers to remove
            if(isadding) {
                for(int i=0; i<1024; i++) {
                    try {
                        bag.add(i);
                    } catch (LeakyConcurrentBag.NotRegisteredException e) {
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
        parser.addArgument("-d").help("Data structure [bag|queue]");

        Namespace ns = null;

        try {
            ns = parser.parseArgs(args);

            int nthreads = Integer.parseInt(ns.getString("n"));
            int nOperations = Integer.parseInt(ns.getString("o"));
            double addRatio = Double.parseDouble(ns.getString("a"));
            String dataStructure = ns.getString("d");

            if(addRatio > 1.0 || addRatio < 0.0) {
                logger.error("Add ratio out of bounds");
                return;
            } else if(addRatio <= 0.5) {
                logger.warn("Add ratio <= 0.5 " + " not recommended");
            }

            logger.debug("Beginning test: n=" + nthreads + " o=" + nOperations + " a=" + addRatio + " d=" + dataStructure);

            if(dataStructure.equals("bag")) {
                LinkedList<ConcurrentBagTestThread> testThreads = new LinkedList<>();

                for(int i=0;i<nthreads;i++) {
                    testThreads.add(new ConcurrentBagTestThread(i, nthreads, nOperations, addRatio));
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
            } else if(dataStructure.equals("leaky")) {
                LinkedList<LeakyConcurrentBagTestThread> testThreads = new LinkedList<>();

                for(int i=0;i<nthreads;i++) {
                    testThreads.add(new LeakyConcurrentBagTestThread(i, nthreads, nOperations, addRatio));
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
            } else if(dataStructure.equals("queue")) {
                LinkedList<ConcurrentQueueTestThread> testThreads = new LinkedList<>();

                for(int i=0;i<nthreads;i++) {
                    testThreads.add(new ConcurrentQueueTestThread(i, nthreads, nOperations, addRatio));
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
            } else if(dataStructure.equals("transfer")) {
                LinkedList<TransferQueueTestThread> testThreads = new LinkedList<>();

                for(int i=0;i<nthreads;i++) {
                    testThreads.add(new TransferQueueTestThread(i, nthreads, nOperations, addRatio));
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
            } else {
                logger.error("Invalid datastructure " + dataStructure);
            }

        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
