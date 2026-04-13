package com.kk.mila.common.util;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageUtil {
    public static <T, R> PageResult<R> convert(PageResult<T> page, Function<T, R> converter) {
        List<R> records = page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal());
    }

    public record PageResult<T>(List<T> records, long total) {}
}
