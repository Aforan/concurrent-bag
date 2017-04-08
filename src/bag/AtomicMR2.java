/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package bag;

import java.util.concurrent.atomic.AtomicReference;

public class AtomicMR2<V> {

    private static class ReferenceBooleanPair<T> {
        private final T reference;

        private final boolean bit1;
        private final boolean bit2;

        ReferenceBooleanPair(T r, boolean b1, boolean b2) {
            reference = r;
            bit1 = b1;
            bit2 = b2;
        }
    }

    private final AtomicReference<ReferenceBooleanPair<V>> atomicRef;

    public AtomicMR2(V initialRef, boolean initialMark1, boolean initialMark2) {
        atomicRef = new AtomicReference<ReferenceBooleanPair<V>>(new ReferenceBooleanPair<V>(initialRef, initialMark1, initialMark2));
    }

    public boolean compareAndSet(V expectedReference, V newReference, boolean expectedMark1, boolean expectedMark2,
                                 boolean newMark1, boolean newMark2) {
        ReferenceBooleanPair<V> current = atomicRef.get();

        return expectedReference == current.reference && expectedMark1 == current.bit1 && expectedMark2 == current.bit2 &&
                ((newReference == current.reference && newMark1 == current.bit1 && newMark2 == current.bit2) ||
                        atomicRef.compareAndSet(current, new ReferenceBooleanPair<V>(newReference, newMark1, newMark2)));
    }

    public boolean hasMark2() {
        return atomicRef.get().bit2;
    }

    public V getReference() {
        return atomicRef.get().reference;
    }
}