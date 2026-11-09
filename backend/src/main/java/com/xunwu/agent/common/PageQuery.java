package com.xunwu.agent.common;

import lombok.Getter;

/**
 * 分页入参：统一归一化 page / size，避免每个接口各写一遍边界处理。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>页码从 <b>1</b> 开始（不是 0），与前端「第 1 页」的直觉一致；</li>
 *   <li>缺省页大小 {@link #DEFAULT_SIZE}，单页上限 {@link #MAX_SIZE}，
 *       防止客户端传 size=99999 把整表拉走；</li>
 *   <li>非法值（null / 0 / 负数）回落到默认值，不报错 —— 分页参数写错
 *       应该「按默认分页」而不是「请求失败」。</li>
 * </ul>
 *
 * @author xunwu
 */
@Getter
public class PageQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    /** 单页上限，防止一次拉全表 */
    public static final int MAX_SIZE = 200;

    private final int page;
    private final int size;

    private PageQuery(int page, int size) {
        this.page = page;
        this.size = size;
    }

    /**
     * 按默认页大小 / 上限归一化。
     *
     * @param page 页码，null 或小于 1 时取 {@link #DEFAULT_PAGE}
     * @param size 每页条数，null 或小于 1 时取 {@link #DEFAULT_SIZE}，超过上限时截断
     */
    public static PageQuery of(Integer page, Integer size) {
        return of(page, size, DEFAULT_SIZE, MAX_SIZE);
    }

    /**
     * 按指定默认值 / 上限归一化。
     *
     * <p>用于像「会话消息」这类单页天然更长的列表：默认 200 条、上限 500 条。</p>
     *
     * @param defaultSize 缺省页大小
     * @param maxSize     单页上限
     */
    public static PageQuery of(Integer page, Integer size, int defaultSize, int maxSize) {
        int normalizedPage = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int rawSize = (size == null || size < 1) ? defaultSize : size;
        int bound = Math.max(1, maxSize);
        return new PageQuery(normalizedPage, Math.min(rawSize, bound));
    }

    /** SQL OFFSET：第 1 页偏移 0 */
    public int offset() {
        return (page - 1) * size;
    }
}
