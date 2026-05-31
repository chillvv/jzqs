package com.jzqs.app.customer.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("meal_wallets")
public class MealWalletEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("customer_id")
    private Long customerId;

    @TableField("package_plan_id")
    private Long packagePlanId;

    @TableField("total_meals")
    private Integer totalMeals;

    @TableField("reserved_meals")
    private Integer reservedMeals;

    @TableField("consumed_meals")
    private Integer consumedMeals;

    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getPackagePlanId() {
        return packagePlanId;
    }

    public void setPackagePlanId(Long packagePlanId) {
        this.packagePlanId = packagePlanId;
    }

    public Integer getTotalMeals() {
        return totalMeals;
    }

    public void setTotalMeals(Integer totalMeals) {
        this.totalMeals = totalMeals;
    }

    public Integer getReservedMeals() {
        return reservedMeals;
    }

    public void setReservedMeals(Integer reservedMeals) {
        this.reservedMeals = reservedMeals;
    }

    public Integer getConsumedMeals() {
        return consumedMeals;
    }

    public void setConsumedMeals(Integer consumedMeals) {
        this.consumedMeals = consumedMeals;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
