package com.doom.fg.controller;

import com.doom.fg.entity.FoodItem;
import com.doom.fg.service.FoodItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/15:50
 * @Description: 负责页面跳转。它返回 String（Thymeleaf 模板路径），通过 Model 对象传参。
 * 负责直接在浏览器地址栏敲 URL 时显示的页面。
 */
@Controller
public class PageController {
    @Autowired
    private FoodItemService foodItemService;

    // 首页：展示临期提醒、概览数据
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("expiringList", foodItemService.getExpiringSoon(3));
        return "index"; // 对应 templates/index.html
    }

    // 食材列表页
    @GetMapping("/food/list-page")
    public String foodListPage(Model model) {
        List<FoodItem> list = foodItemService.lambdaQuery()
                .eq(FoodItem::getStatus, 0)
                .orderByAsc(FoodItem::getExpiryDate)
                .list();
        list.forEach(foodItemService::calculateDaysLeft);
        model.addAttribute("foods", list);
        return "food_list"; // 对应 templates/food_list.html
    }

    // 新增食材表单页
    @GetMapping("/food/add-page")
    public String foodAddPage() {
        return "food_form";
    }

    // AI 助手页
    @GetMapping("/ai/recipe-page")
    public String aiPage() {
        return "ai_recipe";
    }
}
