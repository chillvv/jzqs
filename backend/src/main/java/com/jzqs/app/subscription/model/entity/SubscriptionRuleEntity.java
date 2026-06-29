package com.jzqs.app.subscription.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("subscription_rules")
public class SubscriptionRuleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("customer_id")
    private Long customerId;

    private Boolean active;

    @TableField("week_days")
    private String weekDays;

    @TableField("lunch_enabled")
    private Boolean lunchEnabled;

    @TableField("lunch_quantity")
    private Integer lunchQuantity;

    @TableField("lunch_delivery_meal_period")
    private String lunchDeliveryMealPeriod;

    @TableField("dinner_enabled")
    private Boolean dinnerEnabled;

    @TableField("dinner_quantity")
    private Integer dinnerQuantity;

    @TableField("dinner_delivery_meal_period")
    private String dinnerDeliveryMealPeriod;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    @TableField("default_address_id")
    private Long defaultAddressId;

    @TableField("merchant_remark")
    private String merchantRemark;

    @TableField("is_priority_follow")
    private Boolean isPriorityFollow;

    private Boolean paused;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getWeekDays() {
        return weekDays;
    }

    public void setWeekDays(String weekDays) {
        this.weekDays = weekDays;
    }

    public Boolean getLunchEnabled() {
        return lunchEnabled;
    }

    public void setLunchEnabled(Boolean lunchEnabled) {
        this.lunchEnabled = lunchEnabled;
    }

    public Integer getLunchQuantity() {
        return lunchQuantity;
    }

    public void setLunchQuantity(Integer lunchQuantity) {
        this.lunchQuantity = lunchQuantity;
    }

    public String getLunchDeliveryMealPeriod() {
        return lunchDeliveryMealPeriod;
    }

    public void setLunchDeliveryMealPeriod(String lunchDeliveryMealPeriod) {
        this.lunchDeliveryMealPeriod = lunchDeliveryMealPeriod;
    }

    public Boolean getDinnerEnabled() {
        return dinnerEnabled;
    }

    public void setDinnerEnabled(Boolean dinnerEnabled) {
        this.dinnerEnabled = dinnerEnabled;
    }

    public Integer getDinnerQuantity() {
        return dinnerQuantity;
    }

    public void setDinnerQuantity(Integer dinnerQuantity) {
        this.dinnerQuantity = dinnerQuantity;
    }

    public String getDinnerDeliveryMealPeriod() {
        return dinnerDeliveryMealPeriod;
    }

    public void setDinnerDeliveryMealPeriod(String dinnerDeliveryMealPeriod) {
        this.dinnerDeliveryMealPeriod = dinnerDeliveryMealPeriod;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Long getDefaultAddressId() {
        return defaultAddressId;
    }

    public void setDefaultAddressId(Long defaultAddressId) {
        this.defaultAddressId = defaultAddressId;
    }

    public String getMerchantRemark() {
        return merchantRemark;
    }

    public void setMerchantRemark(String merchantRemark) {
        this.merchantRemark = merchantRemark;
    }

    public Boolean getIsPriorityFollow() {
        return isPriorityFollow;
    }

    public void setIsPriorityFollow(Boolean isPriorityFollow) {
        this.isPriorityFollow = isPriorityFollow;
    }

    public Boolean getPaused() {
        return paused;
    }

    public void setPaused(Boolean paused) {
        this.paused = paused;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
