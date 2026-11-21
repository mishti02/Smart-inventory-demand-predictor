package service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ListUtils {
    private ListUtils() { }

    // A GENERIC method: <T> means it works for a list of ANY type.
    // "? super T" means the comparator may be written for T or for any parent class of T.
    public static <T> List<T> topN(List<T> items, Comparator<? super T> order, int n) {
        return items.stream()
                .sorted(order)
                .limit(Math.max(0, n))
                .collect(Collectors.toList());
    }
}