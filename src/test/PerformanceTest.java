package test;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.log4j.Logger;

import java.util.LinkedList;

/**
 * Created by rick on 3/30/17.
 */
public class PerformanceTest {
    final static Logger logger = Logger.getLogger(PerformanceTest.class);

    private static class TestThread implements Runnable {

        public TestThread() {

        }

        @Override
        public void run() {

        }
    }

    private LinkedList<TestThread> testThreads;


    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("ConcurrentBag Performance")
                .defaultHelp(true);

        parser.addArgument("-t", "--type")
                .choices("SHA-256", "SHA-512", "SHA1").setDefault("SHA-256")
                .help("Specify hash function to use");

        parser.addArgument("-n", "--nthreads");
        parser.addArgument("-a").help("Add ratio out of 1.0");
        parser.addArgument("-o").help("Number of operations");

        Namespace ns = null;

        try {
            ns = parser.parseArgs(args);

            String c = ns.getString("-t");
            logger.debug(c);

            int nthreads = Integer.parseInt(ns.getString("nthreads"));
            int nOperations = Integer.parseInt(ns.getString("o"));
            double addRatio = Double.parseDouble(ns.getString("a"));

            logger.debug("Beginning test:");

        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
