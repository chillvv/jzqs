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

    @TableField("lunch_enabled")
    private Boolean lunchEnabled;

    @TableField("lunch_quantity")
    private Integer lunchQuantity;

    @TableField("dinner_enabled")
    private Boolean dinnerEnabled;

    @TableField("dinner_quantity")
    private Integer dinnerQuantity;

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
