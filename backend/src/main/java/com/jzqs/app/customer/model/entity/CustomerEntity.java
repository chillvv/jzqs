package com.jzqs.app.customer.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("customers")
public class CustomerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String phone;

    private String source;

    private String openid;

    @TableField("session_key")
    private String sessionKey;

    @TableField("customer_status")
    private String customerStatus;

    @TableField("registered_at")
    private LocalDateTime registeredAt;

    @TableField("first_paid_at")
    private LocalDateTime firstPaidAt;

    @TableField("last_order_at")
    private LocalDateTime lastOrderAt;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("merchant_remark")
    private String merchantRemark;

    @TableField("current_openid")
    private String currentOpenid;

    @TableField("openid_updated_at")
    private LocalDateTime openidUpdatedAt;

    @TableField("is_priority_customer")
    private Boolean priorityCustomer;

    @TableField("priority_tag")
    private String priorityTag;

    @TableField("priority_note")
    private String priorityNote;

    private Boolean active;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getCustomerStatus() {
        return customerStatus;
    }

    public void setCustomerStatus(String customerStatus) {
        this.customerStatus = customerStatus;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getFirstPaidAt() {
        return firstPaidAt;
    }

    public void setFirstPaidAt(LocalDateTime firstPaidAt) {
        this.firstPaidAt = firstPaidAt;
    }

    public LocalDateTime getLastOrderAt() {
        return lastOrderAt;
    }

    public void setLastOrderAt(LocalDateTime lastOrderAt) {
        this.lastOrderAt = lastOrderAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getMerchantRemark() {
        return merchantRemark;
    }

    public void setMerchantRemark(String merchantRemark) {
        this.merchantRemark = merchantRemark;
    }

    public String getCurrentOpenid() {
        return currentOpenid;
    }

    public void setCurrentOpenid(String currentOpenid) {
        this.currentOpenid = currentOpenid;
    }

    public LocalDateTime getOpenidUpdatedAt() {
        return openidUpdatedAt;
    }

    public void setOpenidUpdatedAt(LocalDateTime openidUpdatedAt) {
        this.openidUpdatedAt = openidUpdatedAt;
    }

    public Boolean getPriorityCustomer() {
        return priorityCustomer;
    }

    public void setPriorityCustomer(Boolean priorityCustomer) {
        this.priorityCustomer = priorityCustomer;
    }

    public String getPriorityTag() {
        return priorityTag;
    }

    public void setPriorityTag(String priorityTag) {
        this.priorityTag = priorityTag;
    }

    public String getPriorityNote() {
        return priorityNote;
    }

    public void setPriorityNote(String priorityNote) {
        this.priorityNote = priorityNote;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
