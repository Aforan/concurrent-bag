package bag;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Andrew on 3/26/2017.
 */
public class ConcurrentBag<T> implements Bag {
    final static Logger logger = Logger.getLogger(ConcurrentBag.class);
    private static final int blockSize = 1024;

    //  Map the Thread.currentThread.getId to index in the bagArrayList
    //  Assume that additions to this DS are mutually exclusive
    //  Assume that modifications cannot be made
    private HashMap<Long, Integer> threadToIndexMap;
    private ReentrantLock registeredThreadLock;
    private LinkedList<LinkedList<T[]>> bagArrayList;
    private ThreadLocal<ThreadMetaData> localMetadata;

    //  Assume mutual exclusion
    private Integer nThreads;

    public static class NotRegisteredException extends Exception {
        public NotRegisteredException(Long threadId) {
            super(threadId + " is not a registered thread");
        }
    }

    public class ThreadMetaData {
        public T[] curBlock;

        public int indexInBlock;
        public int indexInList;
        public int indexInBag;

        public ThreadMetaData(int indexInBag) {
            this.indexInBag = indexInBag;
        }
    }

    public ConcurrentBag() {
        threadToIndexMap = new HashMap<>();
        registeredThreadLock = new ReentrantLock();
        bagArrayList = new LinkedList<>();

        nThreads = 0;
    }

    @Override
    public void add(Object item) throws NotRegisteredException {
        if(!isRegistered()) {
            throw new NotRegisteredException(Thread.currentThread().getId());
        }

        ThreadMetaData md = localMetadata.get();
        LinkedList<T[]> subBag = bagArrayList.get(md.indexInBag);

        if(md.curBlock == null || md.indexInBlock == blockSize) {
            if(md.indexInList < subBag.size()) {
                //  Another block exists in the list, just increment to it
                md.curBlock = subBag.get(md.indexInList++);
                md.indexInBlock = 0;
            } else {
                //  No next block, allocate a new one
                T[] newBlock = (T[]) new Object[blockSize];
                md.curBlock = newBlock;
                subBag.add(newBlock);
                md.indexInBlock = 0;
                md.indexInList = subBag.size() - 1;
            }
        }

        //  Insert the item
        md.curBlock[md.indexInBlock++] = (T)item;
    }

    @Override
    public T remove() throws NotRegisteredException {
        if(!isRegistered()) {
            throw new NotRegisteredException(Thread.currentThread().getId());
        }

        return null;
    }

    /**
     * @return true when the thread is already registered
     */
    public boolean isRegistered() {
        Long threadId = Thread.currentThread().getId();
        return threadToIndexMap.containsKey(threadId);
    }

    /**
     * Register a new thread with this Bag.  This method must be
     * called for any thread that uses this DS.  Failure to call
     * this method before add/removes will result in exceptions.
     *
     * Once registered, this method allocates a new list for this
     * thread.
     *
     * @return true when the thread is successfully registered
     */
    public boolean registerThread() {
        try {
            Long threadId = Thread.currentThread().getId();
            registeredThreadLock.lock();

            if(threadToIndexMap.containsKey(threadId)) {
                logger.error("Cannot register thread " + threadId + " thread is already registered");
                return false;
            } else {
                threadToIndexMap.put(threadId, nThreads++);
                bagArrayList.add(new LinkedList<T[]>());

                //  Create the local metadata for this thread
                ThreadMetaData md = new ThreadMetaData(nThreads-1);
                localMetadata = new ThreadLocal<>();
                localMetadata.set(md);

                logger.debug("Thread " + threadId + " registered successfully");
                return true;
            }
        } finally {
            registeredThreadLock.unlock();
        }
    }
}
