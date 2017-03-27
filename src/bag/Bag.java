package bag;

/**
 * Created by rick on 3/22/17.
 */
public interface Bag<T> {
    void add(T item) throws Exception;
    T remove() throws Exception;
}
