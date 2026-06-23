package com.seckill.product.search;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResult {

    private List<ProductDoc> records;

    private long total;

    private int page;

    private int size;
}
