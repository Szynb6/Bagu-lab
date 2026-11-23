package com.bagu.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一分页结果")
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(IPage<?> pageObj, List<T> records) {
        return PageResult.<T>builder()
                .records(records)
                .total(pageObj.getTotal())
                .page(pageObj.getCurrent())
                .size(pageObj.getSize())
                .pages(pageObj.getPages())
                .build();
    }
}
