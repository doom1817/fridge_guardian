package com.doom.fg.task;

import com.doom.fg.entity.FoodItem;
import com.doom.fg.service.FoodItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/22/11:47
 * @Description:  定时监控类
 * 它的作用是：每隔 10 秒（为了演示方便）扫描一次数据库，看看谁的食物快过期了，并在控制台打印警报。
 */
@Component
public class ExpiryAlertTask {
    @Autowired
    private FoodItemService foodItemService;
    /**
     * 定时扫描任务
     * cron 表达式: "0/10 * * * * ?" 表示从第 0 秒开始，每 10 秒执行一次。
     * 实际生产中可能是每天早上9点: "0 0 9 * * ?"
     */
    @Scheduled(cron = "0/10 * * * * ?")
    public void checkExpiryFoods() {
        // 1. 调用你刚才写好的 Service 方法，查询 3 天内过期的食材
        List<FoodItem> expiringList = foodItemService.getExpiringSoon(3);

        if (!expiringList.isEmpty()) {
            System.err.println("============== ⚠️ 临期食品警报 ⚠️ ==============");
            for (FoodItem item : expiringList) {
                // 打印出：用户ID、食材名、过期时间、剩余天数
                System.err.printf("【警告】用户(ID:%d) 的食材 [%s] 即将过期！过期日：%s (剩余 %d 天)\n",
                        item.getUserId(),
                        item.getName(),
                        item.getExpiryDate(),
                        item.getDaysLeft());
            }
            System.err.println("================================================");
        }
    }
}
