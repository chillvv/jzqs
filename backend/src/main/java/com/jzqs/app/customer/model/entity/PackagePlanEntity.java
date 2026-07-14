package com.jzqs.app.customer.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("package_plans")
public class PackagePlanEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("package_code")
    private String packageCode;

    @TableField("package_name")
    private String packageName;

    @TableField("total_meals")
    private Integer totalMeals;

    private Boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Integer getTotalMeals() {
        return totalMeals;
    }

    public void setTotalMeals(Integer totalMeals) {
        this.totalMeals = totalMeals;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
