package it.uninafoodlab.application.common;

public record PageRequest(int index, int size) {

    public PageRequest {
        if (index < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page index must be nonnegative and size between 1 and 100");
        }
        if (index > Integer.MAX_VALUE / size) {
            throw new ArithmeticException("Page offset exceeds the supported range");
        }
    }

    public int offset() {
        return Math.multiplyExact(index, size);
    }
}
