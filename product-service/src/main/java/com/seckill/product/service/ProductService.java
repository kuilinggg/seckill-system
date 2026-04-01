package com.seckill.product.service;

import com.seckill.product.entity.Product;
import com.seckill.product.search.ProductDoc;
import java.util.List;

public interface ProductService {

    Product getProductById(Long id);

    boolean reduceStock(Long id, Integer count);

    boolean syncProductToEs(Long id);

    List<ProductDoc> searchProducts(String keyword);
}
