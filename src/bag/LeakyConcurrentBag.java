package bag;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Andrew on 3/26/2017.
 */
public class LeakyConcurrentBag<T> implements Bag {
    final static Logger logger = Logger.getLogger(LeakyConcurrentBag.class);

    public static class NotRegisteredException extends Exception {
        public NotRegisteredException(Long threadId) {
            super(threadId + " is not a registered thread");
        }
    }

    public static class Block<T> {
        AtomicReferenceArray<T> blockData;
        AtomicBoolean deleted;

        public Block() {
            this.blockData = new AtomicReferenceArray<T>(blockSize);
            this.deleted = new AtomicBoolean();
            this.deleted.set(false);
        }
    }
    public static class CannotStealException extends Exception {
        public CannotStealException(String msg) {
            super(msg);
        }
    }
    public class ThreadMetaData {
        public AtomicReferenceArray<T> curBlock;

        public int indexInBlock;
        public int indexInList;
        public int indexInBag;
        public int stealFromBagIndex;
        public int stealFromListIndex;
        public int stealFromBlockIndex;
        public boolean isStealInit = false;

        public ThreadMetaData(int indexInBag) {
            this.indexInBag = indexInBag;
        }
    }

    private static final int blockSize = 1024;

    //  Map the Thread.currentThread.getId to index in the bagArrayList
    //  Assume that additions to this DS are mutually exclusive
    //  Assume that modifications cannot be made
    private HashMap<Long, Integer> threadToIndexMap;
    private Lock registeredThreadLock;
    private ThreadLocal<ThreadMetaData> localMetadata = new ThreadLocal<>();

    private LinkedList<ArrayList<Block<T>>> bagArrayList;

    //  Assume mutual exclusion
    private Integer nThreads;

    public LeakyConcurrentBag() {
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
        ArrayList<Block<T>> subBag = bagArrayList.get(md.indexInBag);

        //  Get the next block
        if(md.curBlock == null || md.indexInBlock == blockSize) {
            if(md.indexInList < subBag.size() - 1) {
                //  Another block exists in the list, just increment to it
                Block<T> block = subBag.get(md.indexInList++);
                md.curBlock = block.blockData;
                md.indexInBlock = 0;

                //  If this existing block was previously deleted, restore it
                block.deleted.set(false);
            } else {
                //  No next block, allocate a new one
                Block<T> newBlock = new Block<T>();

                md.curBlock = newBlock.blockData;
                subBag.add(newBlock);

                md.indexInBlock = 0;
                md.indexInList = subBag.size() - 1;
            }
        }

        subBag.get(md.indexInList).deleted.set(false);

        //  Insert the item
        md.curBlock.set(md.indexInBlock++, (T) item);
    }

    @Override
    public T remove() throws NotRegisteredException, CannotStealException {
        if(!isRegistered()) {
            throw new NotRegisteredException(Thread.currentThread().getId());
        }

        ThreadMetaData md = localMetadata.get();
        ArrayList<Block<T>> subBag = bagArrayList.get(md.indexInBag);
        
        while (0 != 1) {
            // no more items to remove in this block, so attempt to remove from an earlier block if it exists
            if (md.indexInBlock <= 0) {
                // first block in the list, so there's nothing else to remove
                if (md.indexInList == 0) {
                    return steal();
                } else {
                    md.curBlock = subBag.get(--md.indexInList).blockData;
                    md.indexInBlock = blockSize;

                    //  Delete the previous block
                    subBag.get(md.indexInList+1).deleted.set(true);
                }
            }

            T item = md.curBlock.get(--md.indexInBlock);
            if(item != null) {
                if(md.curBlock.compareAndSet(md.indexInBlock, item, null)) {
                    return item;
                }
            }
        }
    }

    private T steal() throws CannotStealException {
        ThreadMetaData md = localMetadata.get();

        //  Steal
        while(true) {
            //  Raises exception when only one thread, or all baglists are empty
            //  This does not detect when all nodes are null, will continually re-check
            T item = nextStealItem();

            if(item != null) {
                Block<T> stealBlock = bagArrayList.get(md.stealFromBagIndex).get(md.stealFromListIndex);

                if (stealBlock.blockData.compareAndSet(md.stealFromBlockIndex-1, item, null)) {
                    return item;
                }
            }

            md.stealFromBlockIndex++;
        }

    }

    private AtomicReferenceArray<T> nextStealBlock() throws CannotStealException {
        ThreadMetaData md = localMetadata.get();

        //  If we are not at the end of the list, try and find a block in the current list
        if(md.stealFromListIndex < bagArrayList.get(md.stealFromBagIndex).size()-1) {
            while(md.stealFromListIndex < bagArrayList.get(md.stealFromBagIndex).size()-1) {
                Block<T> block = bagArrayList.get(md.stealFromBagIndex).get(++md.stealFromListIndex);
                if(!block.deleted.get()) return block.blockData;
            }
        }

        //  Find the next list to steal from.
        //  We need to find a non-empty list,
        // cannot guarantee that the next list has a block
        boolean found = false;
        while(!found) {
            for(int i=1; i < nThreads; i++) {
                if(!bagArrayList.get((md.stealFromBagIndex + i) % nThreads).isEmpty()) {
                    md.stealFromListIndex = 0;
                    md.stealFromBagIndex = (md.stealFromBagIndex + i) % nThreads;

                    //  Return the first block that is not marked deleted in the list
                    while(md.stealFromListIndex < bagArrayList.get(md.stealFromBagIndex).size()-1) {
                        Block<T> block = bagArrayList.get(md.stealFromBagIndex).get(++md.stealFromListIndex);
                        if(!block.deleted.get()) return block.blockData;
                    }
                }
            }
        }

        return null;
    }

    private T nextStealItem() throws CannotStealException {
        ThreadMetaData md = localMetadata.get();
        AtomicReferenceArray<T> stealBlock = null;

        //  This is our first attempt to steal, we try to steal from
        //  the next list, if we are the only thread, throw exception
        if(!md.isStealInit) {
            int nThreads = getNThreads();
            if(nThreads <= 1)
                throw new CannotStealException("Cannot steal, only one thread");

            //  Start at our own index, nextStealBlock will look starting at next slot
            md.stealFromBagIndex = md.indexInBag;

            md.stealFromListIndex = 0;
            md.stealFromBlockIndex = 0;

            stealBlock = nextStealBlock();
            md.isStealInit = true;
        } else {
            //  End of block
            if(md.stealFromBlockIndex >= blockSize) {
                //  Mark as deleted
                bagArrayList.get(md.stealFromBagIndex).get(md.stealFromListIndex).deleted.set(true);

                //  Find the next block which has not been marked as deleted
                stealBlock = nextStealBlock();
                md.stealFromBlockIndex = 0;
            } else {
                //  Get the current steal block
                stealBlock = bagArrayList.get(md.stealFromBagIndex).get(md.stealFromListIndex).blockData;
            }
        }

        T item = stealBlock.get(md.stealFromBlockIndex++);
        return item;
    }

    /*
    private void deleteBlock(){

        if(stealPrev != null){
            if (stealPrev.next.compareAndSet(stealBlock, stealBlock + mark2)){
                stealBlock.next(mark1) // set mark1 on stealBlock.next using CAS
                if (stealBlock.next.mark2) { // if stealBlock.next has mark2
                    stealBlock.next.next(mark1) // set mark on stealBlock.next.next using  CAS
                }
                while(!(stealPrev == null || stealPrev.next.compareAndSet(stealBlock + mark2, stealBlock - mark1))){
                    if (stealPrev.next is not referencing stealBlock){
                        UpdateStealPrev();
                    }
                }
                stealBlock = nextStealBlock(); //next block in linked list or next list
            }
        } else {
            stealPrev = stealBlock;
            stealBlock = nextStealBlock();
        }

    }
    */

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
                bagArrayList.add(new ArrayList<Block<T>>());

                //  Create the local metadata for this thread
                ThreadMetaData md = new ThreadMetaData(nThreads-1);
                localMetadata.set(md);

                return true;
            }
        } finally {
            registeredThreadLock.unlock();
        }
    }

    /**
     *
     * Get the number of threads that are currently registered.
     * Use simple lock to guarantee thread-safety and linearizability
     *
     * @return
     */
    private int getNThreads() {
        try {
            registeredThreadLock.lock();
            return threadToIndexMap.size();
        } finally {
            registeredThreadLock.unlock();
        }
    }
}
