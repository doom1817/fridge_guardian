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
 * @Description: 负责页面跳转。它现在只负责“开门”
 * 负责直接在浏览器地址栏敲 URL 时显示的页面。
 */
@Controller
public class PageController {
    @Autowired
    private FoodItemService foodItemService;

    // 首页：展示临期提醒、概览数据
    @GetMapping("/")
    public String index() {
        return "index"; // 返回 index.html，数据由 JS 加载
    }
    //登录页
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // 食材列表页
    @GetMapping("/food/list-page")
    public String foodListPage() {
        return "food_list";
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
