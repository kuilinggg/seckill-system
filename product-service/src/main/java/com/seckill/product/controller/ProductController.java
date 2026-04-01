package com.seckill.product.controller;

import com.seckill.product.common.Result;
import com.seckill.product.entity.Product;
import com.seckill.product.search.ProductDoc;
import com.seckill.product.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable("id") Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return Result.error("商品不存在或暂不可用");
        }
        return Result.success(product);
    }

    @PostMapping("/sync/{id}")
    public Result<Boolean> syncToEs(@PathVariable("id") Long id) {
        boolean success = productService.syncProductToEs(id);
        if (!success) {
            return Result.error("同步失败，商品不存在或参数非法");
        }
        return Result.success(success);
    }

    @GetMapping("/search")
    public Result<List<ProductDoc>> search(@RequestParam("keyword") String keyword) {
        return Result.success(productService.searchProducts(keyword));
    }
}
