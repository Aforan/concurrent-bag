package test;

import bag.ConcurrentBag;
import bag.ConcurrentBag.ThreadMetaData;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Created by Andrew on 3/26/2017.
 */
public class ConcurrentBagTest {

    @Test
    public void addTest() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        ConcurrentBag<Integer> bag = new ConcurrentBag<>();
        bag.registerThread();

        Field mdField = Class.forName("bag.ConcurrentBag").getDeclaredField("localMetadata");
        mdField.setAccessible(true);
        ThreadMetaData md = (ThreadMetaData) ((ThreadLocal)mdField.get(bag)).get();

        assertEquals(md.indexInBag, 0);
        assertEquals(md.indexInBlock, 0);
        assertEquals(md.indexInList, 0);

        for(int i = 0; i<2056; i++) {
            try {
                bag.add(i);
                assertEquals(md.curBlock.get(md.indexInBlock-1), i);
            } catch (ConcurrentBag.NotRegisteredException e) {
                e.printStackTrace();
                assertFalse(true);
            }
        }
    }

    @Test
    public void removeTest() throws ClassNotFoundException, NoSuchFieldException,
                                    IllegalAccessException, NullPointerException {
        ConcurrentBag<Integer> bag = new ConcurrentBag<>();
        bag.registerThread();

        Field mdField = Class.forName("bag.ConcurrentBag").getDeclaredField("localMetadata");
        mdField.setAccessible(true);
        ThreadMetaData md = (ThreadMetaData) ((ThreadLocal)mdField.get(bag)).get();

        for(int i = 0; i < 1030; i++) {
            try {
                bag.add(i);
            } catch (ConcurrentBag.NotRegisteredException e) {
                e.printStackTrace();
            }
        }
        assertEquals(1, md.indexInList);
        assertEquals(6, md.indexInBlock);

        for(int i = 0; i < 3; i++) {
            try {
                bag.remove();
            } catch (ConcurrentBag.CannotStealException |
                     ConcurrentBag.NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        assertEquals(1, md.indexInList);
        assertEquals(3, md.indexInBlock);


        for(int i = 0; i < 1027; i++) {
            try {
                bag.remove();
            } catch (ConcurrentBag.CannotStealException |
                    ConcurrentBag.NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        try {
            bag.remove();
        } catch (ConcurrentBag.CannotStealException |
                ConcurrentBag.NotRegisteredException e) {
            assertTrue(e instanceof ConcurrentBag.CannotStealException);
        }

    }
}
