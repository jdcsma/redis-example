package jun.example;

public class Elapse {

    private long begin;
    private long amount;

    public void start() {
        this.begin = System.currentTimeMillis();
    }

    public void stop() {
        this.amount += System.currentTimeMillis() - this.begin;
    }

    public long getAmount() {
        return this.amount;
    }
}
