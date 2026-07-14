package com.jzqs.app.customer.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("wallet_transactions")
public class WalletTransactionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("wallet_id")
    private Long walletId;

    @TableField("transaction_type")
    private String transactionType;

    @TableField("meal_delta")
    private Integer mealDelta;

    @TableField("operator_name")
    private String operatorName;

    private String remark;

    @TableField("biz_type")
    private String bizType;

    @TableField("related_order_id")
    private Long relatedOrderId;

    @TableField("related_aftersale_id")
    private Long relatedAftersaleId;

    @TableField("related_transaction_id")
    private Long relatedTransactionId;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("snapshot_balance")
    private Integer snapshotBalance;

    private Boolean refunded;

    @TableField("refund_reason_code")
    private String refundReasonCode;

    @TableField("refund_reason_text")
    private String refundReasonText;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Integer getMealDelta() {
        return mealDelta;
    }

    public void setMealDelta(Integer mealDelta) {
        this.mealDelta = mealDelta;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getRelatedOrderId() {
        return relatedOrderId;
    }

    public void setRelatedOrderId(Long relatedOrderId) {
        this.relatedOrderId = relatedOrderId;
    }

    public Long getRelatedAftersaleId() {
        return relatedAftersaleId;
    }

    public void setRelatedAftersaleId(Long relatedAftersaleId) {
        this.relatedAftersaleId = relatedAftersaleId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getRelatedTransactionId() {
        return relatedTransactionId;
    }

    public void setRelatedTransactionId(Long relatedTransactionId) {
        this.relatedTransactionId = relatedTransactionId;
    }

    public Integer getSnapshotBalance() {
        return snapshotBalance;
    }

    public void setSnapshotBalance(Integer snapshotBalance) {
        this.snapshotBalance = snapshotBalance;
    }

    public Boolean getRefunded() {
        return refunded;
    }

    public void setRefunded(Boolean refunded) {
        this.refunded = refunded;
    }

    public String getRefundReasonCode() {
        return refundReasonCode;
    }

    public void setRefundReasonCode(String refundReasonCode) {
        this.refundReasonCode = refundReasonCode;
    }

    public String getRefundReasonText() {
        return refundReasonText;
    }

    public void setRefundReasonText(String refundReasonText) {
        this.refundReasonText = refundReasonText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
