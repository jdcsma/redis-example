package jun.example;

public class Elapse {

    private final long begin;

    public Elapse() {
        this.begin = System.currentTimeMillis();
    }

    public long stop() {
        return System.currentTimeMillis() - this.begin;
    }
}
