import java.io.Serializable;

public class Inventory implements Serializable {
    private int[] slots;
    private int size;

    public Inventory(int size) {
        this.size = size;
        this.slots = new int[size];
        for (int i = 0; i < size; i++) slots[i] = 0; // AIR
    }

    public int getItem(int index) {
        if (index < 0 || index >= size) return 0;
        return slots[index];
    }

    public void setItem(int index, int id) {
        if (index >= 0 && index < size) {
            slots[index] = id;
        }
    }

    public int getSize() {
        return size;
    }

    public void swap(int i, int j) {
        if (i >= 0 && i < size && j >= 0 && j < size) {
            int temp = slots[i];
            slots[i] = slots[j];
            slots[j] = temp;
        }
    }

    public void clear(int index) {
        if (index >= 0 && index < size) slots[index] = 0;
    }
}
