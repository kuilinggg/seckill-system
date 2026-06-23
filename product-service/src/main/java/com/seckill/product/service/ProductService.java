package com.seckill.product.service;

import com.seckill.product.entity.Product;
import com.seckill.product.search.ProductSearchResult;

public interface ProductService {

    Product getProductById(Long id);

    boolean reduceStock(Long id, Integer count);

    boolean syncProductToEs(Long id);

    ProductSearchResult searchProducts(String keyword, Integer page, Integer size, Boolean inStockOnly, String sort);
}
