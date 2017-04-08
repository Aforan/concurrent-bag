package bag;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
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

    private Lock registeredThreadLock;
    private ThreadLocal<ThreadMetaData> localMetadata = new ThreadLocal<>();
    private LinkedList<LinkedList<AtomicReferenceArray<T>>> bagArrayList;

    //  Assume mutual exclusion
    private Integer nThreads;

    public static class NotRegisteredException extends Exception {
        public NotRegisteredException(Long threadId) {
            super(threadId + " is not a registered thread");
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

        LinkedList<AtomicReferenceArray<T>> subBag = bagArrayList.get(md.indexInBag);
        AtomicMR2<AtomicReferenceArray<T>> blockRef = new AtomicMR2<>(md.curBlock, false, false);

        if(md.curBlock == null || md.indexInBlock == blockSize) {
            if(md.indexInList < subBag.size() - 1) {
                //  Another block exists in the list, just increment to it
                md.curBlock = subBag.get(md.indexInList++);
                md.indexInBlock = 0;
            } else {
                //  No next block, allocate a new one
                List[] t = new List[5];
                AtomicReferenceArray<T> newBlock = new AtomicReferenceArray<>(blockSize);

                md.curBlock = newBlock;
                subBag.add(newBlock);
                md.indexInBlock = 0;
                md.indexInList = subBag.size() - 1;
            }
        }

        //  Insert the item
        // but first, make sure block isn't logically deleted, but we only care about mark2
        if (blockRef.compareAndSet(md.curBlock, md.curBlock, false, false, false, false) ||
                blockRef.compareAndSet(md.curBlock, md.curBlock, false, true, false, true)) {
            md.curBlock.set(md.indexInBlock++, (T) item);
        }
    }

    @Override
    public T remove() throws NotRegisteredException, CannotStealException {
        if(!isRegistered()) {
            throw new NotRegisteredException(Thread.currentThread().getId());
        }

        ThreadMetaData md = localMetadata.get();
        LinkedList<AtomicReferenceArray<T>> subBag = bagArrayList.get(md.indexInBag);
        
        while (0 != 1) {
            // no more items to remove in this block, so attempt to remove from an earlier block if it exists
            if (md.indexInBlock <= 0) {
                // first block in the list, so there's nothing else to remove
                if (md.indexInList == 0) {
                    return steal();
                } else {
                    md.curBlock = subBag.get(--md.indexInList);
                    md.indexInBlock = blockSize;
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
                bagArrayList.add(new LinkedList<>());

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

    private AtomicReferenceArray<T> nextStealBlock() throws CannotStealException {
        ThreadMetaData md = localMetadata.get();

        //  Find the next list to steal from.
        //  We need to find a non-empty list,
        // cannot guarantee that the next list has a block
        boolean found = false;
        for(int i=1; i < nThreads; i++) {
            if(!bagArrayList.get((md.stealFromBagIndex + i) % nThreads).isEmpty()) {
                found = true;
                md.stealFromListIndex = 0;
                md.stealFromBagIndex = (md.stealFromBagIndex + i) % nThreads;
                break;
            }
        }

        //  Globally empty
        if(!found)
            throw new CannotStealException("All bags are empty");

        return bagArrayList.get(md.stealFromBagIndex).get(md.stealFromListIndex);
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
                // attempt to delete this empty block
                deleteBlock(md.stealFromBagIndex, md.stealFromListIndex, md.curBlock);

                //  End of list
                if(md.stealFromListIndex >= bagArrayList.get(md.stealFromBagIndex).size()-1) {
                    stealBlock = nextStealBlock();
                } else {
                    stealBlock = bagArrayList.get(md.stealFromBagIndex).get(++md.stealFromListIndex);
                }

                md.stealFromBlockIndex = 0;
            } else {
                stealBlock = bagArrayList.get(md.stealFromBagIndex).get(md.stealFromListIndex);
            }
        }

        T item = stealBlock.get(md.stealFromBlockIndex++);
        return item;
    }

    private T steal() throws CannotStealException {
        ThreadMetaData md = localMetadata.get();

        //  Steal
        while(true) {
            //  Raises exception when only one thread, or all baglists are empty
            //  This does not detect when all nodes are null, will continually re-check
            T item = nextStealItem();

            if(item != null) {
                AtomicReferenceArray<T> stealBlock = bagArrayList.get(md.stealFromBagIndex).get(md.stealFromListIndex);

                if (stealBlock.compareAndSet(md.stealFromBlockIndex-1, item, null)) {
                    return item;
                }
            }

            md.stealFromBlockIndex++;
        }

    }

//    private AtomicReferenceArray<T> deleteBlock(int index)
    private void deleteBlock(int bagIndex, int listIndex, AtomicReferenceArray<T> stealBlock) {
        ThreadMetaData md = localMetadata.get();
        AtomicReferenceArray<T> stealPrev = bagArrayList.get(bagIndex).get(listIndex);
        AtomicReferenceArray<T> stealNext = bagArrayList.get(bagIndex).get(listIndex + 2);
        AtomicReferenceArray<T> stealNextNext = bagArrayList.get(bagIndex).get(listIndex + 3);

        if (stealPrev != null) {
            AtomicMR2<AtomicReferenceArray<T>> stealBlockRef = new AtomicMR2<>(stealBlock, false, false);
            AtomicMR2<AtomicReferenceArray<T>> stealNextRef = new AtomicMR2<>(stealNext, false, false);
            AtomicMR2<AtomicReferenceArray<T>> stealNextNextRef = new AtomicMR2<>(stealNext, false, false);

            if (stealBlockRef.compareAndSet(bagArrayList.get(bagIndex).get(listIndex + 1), stealBlock,
                    false, false, false, true)) {
                // set mark1 on stealBlock's reference
                stealNextRef.compareAndSet(stealNext, stealNext, false, false, true, false);
                if (stealNextRef.hasMark2()) {
                    if (stealNextNext != null) {
                        stealNextNextRef.compareAndSet(stealNextNext, stealNextNext,
                                false, false, true, false);
                    }
                }

                do {
                    if (stealBlockRef.getReference() != stealBlock) {
//                         paper doesn't describe this much, not having this will slightly reduce efficiency, but will not change correctness
//                         updateStealPrev();
                    }
                    // need a "don't care" for expectedMark2 here, so we check again if the first one failed (automatically via "or"
                }
                while (stealPrev == null || stealNextRef.compareAndSet(bagArrayList.get(bagIndex).get(listIndex + 1), stealBlock,
                        true, true, false, true) || stealNextRef.compareAndSet(bagArrayList.get(bagIndex).get(listIndex + 1), stealBlock,
                        true, false, false, false));

                //                         updateStealPrev();
            } else {
                // deleteBlock not performed on this item as it was or has become the first item in the list
                // at this point we can either update stealBlock to point to to the next block or do nothing
                // we do nothing as steal() functions will already do the update for us
                return;
            }
        }
    }

}
