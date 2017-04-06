package util;

import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * Directly based on java.util.atomic.AtomicMarkableReference
 * Simply adds an additional mark to ReferenceBooleanPair
 * Unnecessary methods, wrt the bag implementation have been removed
 *
 * Created by rick on 4/6/17.
 */
public class AtomicMultiMarkableReference<V>  {

    private static class ReferenceBooleanPair<T> {
        private final T reference;
        private final boolean mark1;
        private final boolean mark2;

        ReferenceBooleanPair(T r, boolean mark1, boolean mark2) {
            reference = r;
            this.mark1 = mark1;
            this.mark2 = mark2;
        }
    }

    private final AtomicReference<ReferenceBooleanPair<V>> atomicRef;

    public AtomicMultiMarkableReference(V initialRef, boolean initialMark1, boolean initialaMark2) {
        atomicRef = new AtomicReference<ReferenceBooleanPair<V>> (new ReferenceBooleanPair<V>(initialRef, initialMark1, initialaMark2));
    }

    public V getReference() {
        return atomicRef.get().reference;
    }

    public boolean getMark1() {
        return atomicRef.get().mark1;
    }

    public boolean getMark2() {
        return atomicRef.get().mark2;
    }

    public V get(boolean[] boolRes) {
        ReferenceBooleanPair<V> r = atomicRef.get();
        boolRes[0] = r.mark1;
        boolRes[1] = r.mark2;
        return r.reference;
    }


    /**
     * Atomically sets the value of both the reference and marks
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current marks are equal to the expected marks.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedMark1 the expected value of the mark
     * @param newMark1 the new value for the mark
     * @param expectedMark2 the expected value of the mark
     * @param newMark2 the new value for the mark
     * @return true if successful
     */
    public boolean compareAndSet(V       expectedReference,
                                 V       newReference,
                                 boolean expectedMark1,
                                 boolean newMark1,
                                 boolean expectedMark2,
                                 boolean newMark2) {
        ReferenceBooleanPair<V> current = atomicRef.get();
        return  expectedReference == current.reference &&
                expectedMark1 == current.mark1 &&
                expectedMark2 == current.mark2 &&
                ((newReference == current.reference && newMark1 == current.mark1 && newMark2 == current.mark2) ||
                        atomicRef.compareAndSet(current,
                                new ReferenceBooleanPair<V>(newReference,
                                        newMark1, newMark2)));
    }

    /**
     * Unconditionally sets the value of both the reference and mark.
     *
     * @param newReference the new value for the reference
     * @param newMark1 the new value for the mark
     * @param newMark2 the new value for the mark
     */
    public void set(V newReference, boolean newMark1, boolean newMark2) {
        ReferenceBooleanPair<V> current = atomicRef.get();
        if (newReference != current.reference || newMark1 != current.mark1 || newMark2 != current.mark2)
            atomicRef.set(new ReferenceBooleanPair<V>(newReference, newMark1, newMark2));
    }
}