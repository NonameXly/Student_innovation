package com.innovation.common;

import lombok.Data;

@Data
public class PageQuery {
    private Integer page = 1;
    private Integer size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
