package bag;
import java.util.LinkedList;
public class SequentialBag<T> implements Bag
{
    public LinkedList<Object>[] bagList;
    private int amount;
    private int capacity;

    //Creating the array the bag
    //will stay in
    public SequentialBag(int size)
    {
        this.bagList = new LinkedList[size];
        this.capacity = size;
        this.amount = 0;
    }

    //From the Paper
    public T tryRemoveAny()
    {

        return null;
    }

    //From the paper
    @Override
    public void add(Object item)
    {

        //If this location in the array is uninitialized
        if(bagList[amount % capacity] == null)
            bagList[amount % capacity] = new LinkedList<>();
        //If we've reached the capacity for the array
        if(bagList[amount % capacity].size() == capacity) {
            int temp = amount;
            while (temp != capacity) {
                if (bagList[temp % capacity].size() == capacity)
                    temp++;
                else {
                    bagList[temp % capacity].add(item);
                    amount = temp + 1;
                    return;
                }
            }
            //If we get here, we're at capacity
            bagList = increaseCapacity(bagList, capacity);
            bagList[temp].add(item);
            amount++;
            return;
        }
        //Normal Case
        bagList[amount % capacity].add(item);
        amount++;
    }

    private LinkedList<Object>[] increaseCapacity(LinkedList<Object>[] bgL, int cap)
    {
        capacity *= 2;
        LinkedList<Object>[] biggerBagList = new LinkedList[capacity];
        for(int i = 0; i < bgL.length; i++)
            biggerBagList[i] = bgL[i];
        return biggerBagList;
    }

    public T remove()
    {

        return null;
    }
}