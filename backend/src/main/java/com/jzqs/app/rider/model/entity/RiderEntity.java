package com.jzqs.app.rider.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rider_profiles")
public class RiderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rider_name")
    private String riderName;

    @TableField("display_name")
    private String displayName;

    private String phone;

    @TableField("employment_status")
    private String employmentStatus;

    @TableField("auth_status")
    private String authStatus;

    @TableField("default_area_code")
    private String defaultAreaCode;

    @TableField("display_order")
    private Integer displayOrder;

    private String remark;

    @TableField("assigned_by")
    private String assignedBy;

    @TableField("assigned_at")
    private LocalDateTime assignedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("first_login_at")
    private LocalDateTime firstLoginAt;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }

    public String getAuthStatus() { return authStatus; }
    public void setAuthStatus(String authStatus) { this.authStatus = authStatus; }

    public String getDefaultAreaCode() { return defaultAreaCode; }
    public void setDefaultAreaCode(String defaultAreaCode) { this.defaultAreaCode = defaultAreaCode; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getFirstLoginAt() { return firstLoginAt; }
    public void setFirstLoginAt(LocalDateTime firstLoginAt) { this.firstLoginAt = firstLoginAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
