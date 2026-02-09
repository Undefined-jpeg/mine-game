import java.io.Serializable;

public class Inventory implements Serializable {
    private int[] ids;
    private int[] counts;
    private int[] data; // Extra data (e.g. for Hulcs content ID)
    private int size;
    public static final int MAX_STACK = 64;

    public Inventory(int size) {
        this.size = size;
        this.ids = new int[size];
        this.counts = new int[size];
        this.data = new int[size];
        for (int i = 0; i < size; i++) {
            ids[i] = 0;
            counts[i] = 0;
            data[i] = 0;
        }
    }

    public int getItem(int index) { return (index < 0 || index >= size) ? 0 : ids[index]; }
    public int getCount(int index) { return (index < 0 || index >= size) ? 0 : counts[index]; }
    public int getData(int index) { return (index < 0 || index >= size) ? 0 : data[index]; }

    public void setItem(int index, int id, int count) { setItem(index, id, count, 0); }
    public void setItem(int index, int id, int count, int d) {
        if (index >= 0 && index < size) {
            ids[index] = id;
            counts[index] = (id == 0) ? 0 : count;
            data[index] = (id == 0) ? 0 : d;
        }
    }

    public int getSize() { return size; }

    public void swap(int i, int j) {
        if (i >= 0 && i < size && j >= 0 && j < size) {
            int ti = ids[i]; int tc = counts[i]; int td = data[i];
            ids[i] = ids[j]; counts[i] = counts[j]; data[i] = data[j];
            ids[j] = ti; counts[j] = tc; data[j] = td;
        }
    }

    public boolean addItem(int id, int amount) { return addItem(id, amount, 0); }
    public boolean addItem(int id, int amount, int d) {
        if (id == 0) return true;
        for (int i = 0; i < size; i++) {
            if (ids[i] == id && data[i] == d && counts[i] < MAX_STACK) {
                int toAdd = Math.min(amount, MAX_STACK - counts[i]);
                counts[i] += toAdd; amount -= toAdd;
                if (amount <= 0) return true;
            }
        }
        for (int i = 0; i < size; i++) {
            if (ids[i] == 0) {
                int toAdd = Math.min(amount, MAX_STACK);
                ids[i] = id; counts[i] = toAdd; data[i] = d;
                amount -= toAdd;
                if (amount <= 0) return true;
            }
        }
        return amount <= 0;
    }

    public void clear(int index) { if (index >= 0 && index < size) { ids[index] = 0; counts[index] = 0; data[index] = 0; } }
    public void removeItems(int index, int amount) {
        if (index >= 0 && index < size) {
            counts[index] -= amount;
            if (counts[index] <= 0) clear(index);
        }
    }
}
