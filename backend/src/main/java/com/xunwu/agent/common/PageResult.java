package com.xunwu.agent.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 分页结果统一结构。
 *
 * <p>所有列表接口统一返回该结构，避免「有的接口返回数组、有的返回裸对象」
 * 导致前端要为每种形状写一套渲染与判空逻辑。</p>
 *
 * @param <T> 记录类型
 * @author xunwu
 */
@Getter
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页记录 */
    private final List<T> records;

    /** 总记录数（不是当前页条数） */
    private final long total;

    /** 当前页码，从 1 开始 */
    private final int page;

    /** 每页条数 */
    private final int size;

    /** 总页数 */
    public int getTotalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size);
    }
}
