package util;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by rick on 4/6/17.
 */
public class ConcurrentBagList<T> {
    private static final int blockLength = 1024;

    private static class Node<T> {
        public AtomicReferenceArray<T> block;
        public AtomicMultiMarkableReference<Node<T>> next;

        public Node() {
            this.block = new AtomicReferenceArray<>(blockLength);
            this.next = new AtomicMultiMarkableReference<>(null, false, false);
        }
    }

    private Node<T> head;
    private Node<T> tail;

    public ConcurrentBagList() {
        head = new Node<>();
        tail = head;
    }

    /**
     * This should only be called by the owning thread!
     */
    public void newNode() {
        boolean[] marks = new boolean[2];
        AtomicMultiMarkableReference<Node<T>> tailNext = tail.next;
        tailNext.get(marks);

        Node<T> newNode = new Node<T>();
        tail.next.set(newNode, marks[0], marks[1]);
        tail = tail.next.get(marks);
    }

    public int size() {
        Node<T> node = head;
        int size = 0;
        boolean[] marks = new boolean[2];

        while(node != null) {
            node = node.next.get(marks);
            size++;
        }

        return size;
    }

    public AtomicReferenceArray<T> get(int index) {
        Node<T> node = head;
        int curIndex = 0;
        boolean[] marks = new boolean[2];

        while(node != null) {
            if(index == curIndex) {
                return node.block;
            }

            node = node.next.get(marks);
            curIndex++;
        }

        return null;
    }

    public boolean isEmpty() {
        return head == null;
    }

}
