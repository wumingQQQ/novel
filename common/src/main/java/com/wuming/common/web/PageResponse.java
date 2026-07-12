package com.wuming.common.web;

import java.util.List;

/** 通用分页响应。 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {
}
