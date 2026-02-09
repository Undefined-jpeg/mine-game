import java.io.Serializable;

public class Inventory implements Serializable {
    private int[] ids;
    private int[] counts;
    private int size;
    public static final int MAX_STACK = 64;

    public Inventory(int size) {
        this.size = size;
        this.ids = new int[size];
        this.counts = new int[size];
        for (int i = 0; i < size; i++) {
            ids[i] = 0; // AIR
            counts[i] = 0;
        }
    }

    public int getItem(int index) {
        if (index < 0 || index >= size) return 0;
        return ids[index];
    }

    public int getCount(int index) {
        if (index < 0 || index >= size) return 0;
        return counts[index];
    }

    public void setItem(int index, int id, int count) {
        if (index >= 0 && index < size) {
            ids[index] = id;
            counts[index] = (id == 0) ? 0 : count;
        }
    }

    public int getSize() {
        return size;
    }

    public void swap(int i, int j) {
        if (i >= 0 && i < size && j >= 0 && j < size) {
            int tempId = ids[i];
            int tempCount = counts[i];
            ids[i] = ids[j];
            counts[i] = counts[j];
            ids[j] = tempId;
            counts[j] = tempCount;
        }
    }

    public boolean addItem(int id, int amount) {
        if (id == 0) return true;

        // Try to stack with existing
        for (int i = 0; i < size; i++) {
            if (ids[i] == id && counts[i] < MAX_STACK) {
                int toAdd = Math.min(amount, MAX_STACK - counts[i]);
                counts[i] += toAdd;
                amount -= toAdd;
                if (amount <= 0) return true;
            }
        }

        // Try to find empty slot
        for (int i = 0; i < size; i++) {
            if (ids[i] == 0) {
                int toAdd = Math.min(amount, MAX_STACK);
                ids[i] = id;
                counts[i] = toAdd;
                amount -= toAdd;
                if (amount <= 0) return true;
            }
        }

        return amount <= 0;
    }

    public void clear(int index) {
        if (index >= 0 && index < size) {
            ids[index] = 0;
            counts[index] = 0;
        }
    }

    public void removeItems(int index, int amount) {
        if (index >= 0 && index < size) {
            counts[index] -= amount;
            if (counts[index] <= 0) {
                ids[index] = 0;
                counts[index] = 0;
            }
        }
    }
}
