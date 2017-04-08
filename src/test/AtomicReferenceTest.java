package test;

import bag.AtomicMR2;
import bag.ConcurrentBag;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * Created by alex on 4/1/17.
 */
public class AtomicReferenceTest {

    @Test
    public void overFlowFirstBlock() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        ConcurrentBag<Integer> bag = new ConcurrentBag<>();
        bag.registerThread();

        Field mdField = Class.forName("bag.ConcurrentBag").getDeclaredField("localMetadata");
        mdField.setAccessible(true);
        ConcurrentBag.ThreadMetaData md = (ConcurrentBag.ThreadMetaData) ((ThreadLocal)mdField.get(bag)).get();

        assertEquals(0, md.indexInList);

        for(int i = 0; i < 1056; i++) {
            try {
                bag.add(i);
                assertEquals(md.curBlock.get(md.indexInBlock-1), i);
            } catch (ConcurrentBag.NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        assertEquals(0, md.indexInBag);
        assertEquals(1, md.indexInList);

        Object block0 = bag.bagArrayList.get(0).get(0);
        Object block1 = bag.bagArrayList.get(0).get(1);

        AtomicMR2<Object> stealPrev = new AtomicMR2<> (block0, false, false);
        AtomicMR2<Object> stealBlock = new AtomicMR2<> (block1, false, false);

        // CAS(stealPrev.next, stealBlock, stealBlock + mark2)
        stealPrev.compareAndSet(block0, block0, false, false, false, true);
    }
}
