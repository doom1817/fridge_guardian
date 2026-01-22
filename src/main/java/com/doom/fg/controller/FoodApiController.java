package com.doom.fg.controller;

import com.doom.fg.common.Result;
import com.doom.fg.entity.Category;
import com.doom.fg.entity.FoodItem;
import com.doom.fg.entity.User;
import com.doom.fg.service.CategoryService;
import com.doom.fg.service.FoodItemService;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Description: 负责处理增删改查逻辑。
 */
@RestController
@RequestMapping("/api/food")
public class FoodApiController {
    @Autowired
    private FoodItemService foodItemService;
    @Autowired
    private CategoryService categoryService;

    // 获取当前登录用户的辅助方法
    private User getCurrentUser() {
        return (User) SecurityUtils.getSubject().getPrincipal();
    }

    // 1. 获取所有在库食材 (按过期时间排序)
    @GetMapping("/list")
    public Result<List<FoodItem>> getInStockList() {
        Long userId = getCurrentUser().getId();
        List<FoodItem> list = foodItemService.lambdaQuery()
                .eq(FoodItem::getUserId, userId)
                .eq(FoodItem::getStatus, 0)
                .orderByAsc(FoodItem::getExpiryDate)
                .list();
        list.forEach(foodItemService::calculateDaysLeft);
        return Result.success(list);
    }

    // 2. 新增食材
    @PostMapping("/add")
    public Result<Void> addFood(@RequestBody FoodItem foodItem) {
        foodItem.setUserId(getCurrentUser().getId());
        foodItemService.saveFoodWithExpiry(foodItem, null);
        return Result.success(null);
    }

    // 3. 获取临期食材 (合并后的版本：支持参数 + 用户过滤)
    @GetMapping("/expiring")
    public Result<List<FoodItem>> getExpiringSoon(@RequestParam(defaultValue = "3") int days) {
        // 1. 获取当前用户
        Long userId = getCurrentUser().getId();

        // 2. 调用 Service 获取所有临期食材
        // (注意：为了不改动 Service 接口签名，我们在这里做内存过滤，这对于软著项目完全足够)
        List<FoodItem> allExpiring = foodItemService.getExpiringSoon(days);

        // 3. 只保留属于当前用户的食材
        List<FoodItem> myExpiring = allExpiring.stream()
                .filter(item -> item.getUserId().equals(userId))
                .collect(Collectors.toList());

        return Result.success(myExpiring);
    }

    // 4. 标记食材状态
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Long userId = getCurrentUser().getId();
        foodItemService.lambdaUpdate()
                .eq(FoodItem::getUserId, userId)
                .set(FoodItem::getStatus, status)
                .eq(FoodItem::getId, id) // 确保只能改自己的
                .update();
        return Result.success(null);
    }

    // 5. 获取分类列表
    @GetMapping("/categories")
    public Result<List<Category>> getCategories() {
        return Result.success(categoryService.list());
    }

    // 6. 获取统计数据
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Long userId = getCurrentUser().getId();
        long consumedCount = foodItemService.lambdaQuery()
                .eq(FoodItem::getUserId, userId)
                .eq(FoodItem::getStatus, 1)
                .count();
        long wastedCount = foodItemService.lambdaQuery()
                .eq(FoodItem::getUserId, userId)
                .eq(FoodItem::getStatus, 2)
                .count();
        long total = consumedCount + wastedCount;
        Map<String, Object> stats = new HashMap<>();
        stats.put("consumed", consumedCount);
        stats.put("wasted", wastedCount);
        stats.put("total", total);

        return Result.success(stats);
    }
}