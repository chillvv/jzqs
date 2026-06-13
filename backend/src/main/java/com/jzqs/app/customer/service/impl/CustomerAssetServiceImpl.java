package com.jzqs.app.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.customer.api.CustomerAssetResponse;
import com.jzqs.app.customer.api.RemarkSuggestionResponse;
import com.jzqs.app.customer.api.WalletAdjustRequest;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.customer.mapper.CustomerMapper;
import com.jzqs.app.customer.mapper.MealWalletMapper;
import com.jzqs.app.customer.mapper.WalletTransactionMapper;
import com.jzqs.app.customer.model.entity.CustomerEntity;
import com.jzqs.app.customer.model.entity.MealWalletEntity;
import com.jzqs.app.customer.model.entity.WalletTransactionEntity;
import com.jzqs.app.customer.service.CustomerAssetService;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@Service
public class CustomerAssetServiceImpl implements CustomerAssetService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final CustomerMapper customerMapper;
    private final MealWalletMapper mealWalletMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final JdbcTemplate jdbcTemplate;

    public CustomerAssetServiceImpl(
        CustomerMapper customerMapper,
        MealWalletMapper mealWalletMapper,
        WalletTransactionMapper walletTransactionMapper,
        JdbcTemplate jdbcTemplate
    ) {
        this.customerMapper = customerMapper;
        this.mealWalletMapper = mealWalletMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResponse<CustomerAssetResponse> listAssets(
        String keyword,
        String customerStatus,
        Boolean hasBalance,
        Boolean fixedSubscriptionEnabled,
        Boolean priorityCustomer
    ) {
        List<MealWalletEntity> wallets = mealWalletMapper.selectList(
            new LambdaQueryWrapper<MealWalletEntity>()
                .eq(MealWalletEntity::getActive, true)
                .orderByAsc(MealWalletEntity::getCustomerId)
        );

        List<CustomerEntity> customers = customerMapper.selectList(
            new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getActive, true)
                .orderByAsc(CustomerEntity::getId)
        );
        if (customers.isEmpty()) {
            return PageResponse.of(List.of(), 1, 20, 0);
        }

        Map<Long, MealWalletEntity> walletMap = wallets.stream()
            .collect(Collectors.toMap(MealWalletEntity::getCustomerId, wallet -> wallet, (left, right) -> left));
        Set<Long> fixedCustomerIds = fixedSubscriptionCustomerIds();

        List<CustomerAssetResponse> items = customers.stream().map(customer -> {
            MealWalletEntity wallet = walletMap.get(customer.getId());
            int remainingMeals = wallet == null ? 0 : remainingMeals(wallet);
            boolean hasOpenedCard = wallet != null;
            boolean fixedEnabled = fixedCustomerIds.contains(customer.getId());
            return new CustomerAssetResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                normalizeCustomerStatus(customer.getCustomerStatus()),
                wallet == null ? 0 : nvl(wallet.getTotalMeals()),
                remainingMeals,
                hasOpenedCard,
                fixedEnabled,
                Boolean.TRUE.equals(customer.getPriorityCustomer()),
                blankToNull(customer.getPriorityTag()),
                blankToNull(customer.getMerchantRemark()),
                formatDateTime(customer.getLastOrderAt()),
                formatDateTime(customer.getRegisteredAt() != null ? customer.getRegisteredAt() : customer.getCreatedAt()),
                remainingMeals > 0 ? "ACTIVE" : "EXHAUSTED"
            );
        }).filter(item -> matchesKeyword(item, keyword))
            .filter(item -> matchesText(item.customerStatus(), customerStatus))
            .filter(item -> hasBalance == null || hasBalance == (item.remainingMeals() > 0))
            .filter(item -> fixedSubscriptionEnabled == null || fixedSubscriptionEnabled == item.fixedSubscriptionEnabled())
            .filter(item -> priorityCustomer == null || priorityCustomer == item.priorityCustomer())
            .toList();

        return PageResponse.of(items, 1, 20, items.size());
    }

    @Override
    public Map<String, Object> customerDetail(long customerId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Boolean.TRUE.equals(customer.getActive())) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }
        MealWalletEntity wallet = mealWalletMapper.selectOne(
            new LambdaQueryWrapper<MealWalletEntity>()
                .eq(MealWalletEntity::getCustomerId, customerId)
                .eq(MealWalletEntity::getActive, true)
                .last("LIMIT 1")
        );
        
        // 如果钱包不存在，自动创建一个初始钱包
        if (wallet == null) {
            wallet = createInitialWallet(customerId);
        }
        List<Map<String, Object>> addresses = jdbcTemplate.query(
            "SELECT id, contact_name, contact_phone, address_line, area_code, is_default FROM customer_addresses WHERE customer_id = ? ORDER BY is_default DESC, id ASC",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("contactName", rs.getString("contact_name"));
                row.put("contactPhone", rs.getString("contact_phone"));
                row.put("addressLine", rs.getString("address_line"));
                row.put("areaCode", rs.getString("area_code"));
                row.put("isDefault", rs.getBoolean("is_default"));
                return row;
            },
            customerId
        );
        List<Map<String, Object>> subscriptions = jdbcTemplate.query(
            "SELECT id, lunch_enabled, dinner_enabled, start_date, end_date, merchant_remark, is_priority_follow, paused FROM subscription_rules WHERE customer_id = ? ORDER BY id DESC",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("lunchEnabled", rs.getBoolean("lunch_enabled"));
                row.put("dinnerEnabled", rs.getBoolean("dinner_enabled"));
                row.put("startDate", rs.getDate("start_date") == null ? null : rs.getDate("start_date").toLocalDate().toString());
                row.put("endDate", rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate().toString());
                row.put("merchantRemark", rs.getString("merchant_remark"));
                row.put("priorityFollow", rs.getBoolean("is_priority_follow"));
                row.put("paused", rs.getBoolean("paused"));
                return row;
            },
            customerId
        );

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", customer.getId());
        detail.put("name", customer.getName());
        detail.put("phone", customer.getPhone());
        detail.put("customerStatus", normalizeCustomerStatus(customer.getCustomerStatus()));
        detail.put("merchantRemark", blankToNull(customer.getMerchantRemark()));
        detail.put("priorityCustomer", false);
        detail.put("registeredAt", formatDateTime(customer.getRegisteredAt() != null ? customer.getRegisteredAt() : customer.getCreatedAt()));
        detail.put("lastOrderAt", formatDateTime(customer.getLastOrderAt()));
        detail.put("wallet", wallet == null ? null : Map.of(
            "totalMeals", nvl(wallet.getTotalMeals()),
            "reservedMeals", nvl(wallet.getReservedMeals()),
            "consumedMeals", nvl(wallet.getConsumedMeals()),
            "remainingMeals", remainingMeals(wallet)
        ));
        detail.put("addresses", addresses);
        detail.put("subscriptions", subscriptions);
        detail.put("transactions", walletTransactions(customerId).items());
        return detail;
    }

    @Override
    @Transactional
    public Map<String, Object> createCustomerProfile(Map<String, Object> payload) {
        String phone = blankToDefault(stringValue(payload.get("phone")), "");
        String name = blankToDefault(stringValue(payload.get("name")), "未命名客户");
        int initialMealDelta = intValue(payload.get("initialMealDelta"), 0);
        String initialMealRemark = blankToNull(stringValue(payload.get("initialMealRemark")));
        String customerStatus = normalizeCustomerStatus(stringValue(payload.get("customerStatus")));
        
        String addressLine = blankToNull(stringValue(payload.get("addressLine")));
        name = requireCustomerName(name);
        phone = requireCustomerPhone(phone);
        addressLine = requireCustomerAddressLine(addressLine);

        if (!phone.isBlank()) {
            boolean phoneExists = customerMapper.selectCount(new LambdaQueryWrapper<CustomerEntity>().eq(CustomerEntity::getPhone, phone)) > 0;
            if (phoneExists) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号已存在，请检查是否重复建档");
            }
        }
        if (initialMealDelta < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "初始加餐数量不能小于 0");
        }
        
        boolean nameExists = customerMapper.selectCount(new LambdaQueryWrapper<CustomerEntity>()
            .eq(CustomerEntity::getName, name)
            .eq(CustomerEntity::getActive, true)) > 0;
        if (nameExists) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户姓名已存在，请更换姓名（如加编号后缀）");
        }

        LocalDateTime now = LocalDateTime.now();
        CustomerEntity customer = new CustomerEntity();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerStatus(customerStatus);
        customer.setMerchantRemark(blankToNull(stringValue(payload.get("merchantRemark"))));
        customer.setPriorityCustomer(false);
        customer.setPriorityTag(null);
        customer.setPriorityNote(null);
        customer.setSource("BACKEND");
        customer.setSourceChannel("ADMIN");
        customer.setRegisteredAt(now);
        customer.setActive(true);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customerMapper.insert(customer);

        jdbcTemplate.update("""
            INSERT INTO customer_addresses (
                customer_id, contact_name, contact_phone, address_line, area_code, is_default
            ) VALUES (?, ?, ?, ?, ?, TRUE)
            """, customer.getId(), name, phone, addressLine, "");

        if (initialMealDelta > 0) {
            MealWalletEntity wallet = findOrCreateWallet(customer.getId());
            wallet.setTotalMeals(nvl(wallet.getTotalMeals()) + initialMealDelta);
            mealWalletMapper.updateById(wallet);
            insertWalletTransaction(wallet.getId(), "GRANT", initialMealDelta, "ADMIN", initialMealRemark);
        }

        return Map.of("customerId", customer.getId(), "status", "CREATED");
    }

    @Override
    @Transactional
    public Map<String, Object> updateCustomerProfile(long customerId, Map<String, Object> payload) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Boolean.TRUE.equals(customer.getActive())) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }

        if (payload.containsKey("name")) {
            String newName = requireCustomerName(blankToDefault(stringValue(payload.get("name")), customer.getName()));
            if (!newName.equals(customer.getName())) {
                boolean nameExists = customerMapper.selectCount(new LambdaQueryWrapper<CustomerEntity>()
                    .eq(CustomerEntity::getName, newName)
                    .eq(CustomerEntity::getActive, true)) > 0;
                if (nameExists) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户姓名已存在，请更换姓名（如加编号后缀）");
                }
            }
            customer.setName(newName);
        }
        if (payload.containsKey("phone")) {
            String newPhone = requireCustomerPhone(blankToDefault(stringValue(payload.get("phone")), customer.getPhone()));
            if (!newPhone.equals(customer.getPhone()) && !newPhone.isBlank()) {
                boolean phoneExists = customerMapper.selectCount(new LambdaQueryWrapper<CustomerEntity>().eq(CustomerEntity::getPhone, newPhone)) > 0;
                if (phoneExists) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号已存在，请检查是否重复建档");
                }
            }
            customer.setPhone(newPhone);
        }
        if (payload.containsKey("merchantRemark")) {
            customer.setMerchantRemark(blankToNull(stringValue(payload.get("merchantRemark"))));
        }
        if (payload.containsKey("customerStatus")) {
            customer.setCustomerStatus(normalizeCustomerStatus(stringValue(payload.get("customerStatus"))));
        }
        
        // 处理 defaultUserRemark 更新
        if (payload.containsKey("defaultUserRemark")) {
            String defaultUserRemark = blankToNull(stringValue(payload.get("defaultUserRemark")));
            // 删除旧的用户长期备注
            jdbcTemplate.update("""
                DELETE FROM customer_notes
                WHERE customer_id = ? AND note_type = 'USER' AND scope_type = 'LONG_TERM'
                """, customerId);
            
            if (defaultUserRemark != null && !defaultUserRemark.isBlank()) {
                // 插入新的用户长期备注
                jdbcTemplate.update("""
                    INSERT INTO customer_notes (
                        customer_id, note_type, scope_type, content, is_active, display_order, created_by, updated_by, created_at, updated_at
                    ) VALUES (?, 'USER', 'LONG_TERM', ?, TRUE, 0, 'USER', 'USER', ?, ?)
                    """, customerId, defaultUserRemark, Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
            }
        }
        
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.updateById(customer);
        jdbcTemplate.update(
            "UPDATE customer_addresses SET contact_name = ?, contact_phone = ? WHERE customer_id = ?",
            customer.getName(),
            customer.getPhone(),
            customerId
        );

        return Map.of("customerId", customerId, "status", "UPDATED");
    }

    @Override
    @Transactional
    public Map<String, Object> createCustomerAddress(long customerId, Map<String, Object> payload) {
        requireActiveCustomer(customerId);
        AddressPayload address = normalizeAddressPayload(customerId, payload);
        if (address.isDefault()) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        long addressId = insertAndReturnId(
            """
                INSERT INTO customer_addresses (
                    customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            customerId,
            address.contactName(),
            address.contactPhone(),
            address.addressLine(),
            address.areaCode(),
            address.isDefault()
        );
        return Map.of("customerId", customerId, "addressId", addressId, "status", "CREATED");
    }

    @Override
    @Transactional
    public Map<String, Object> updateCustomerAddress(long customerId, long addressId, Map<String, Object> payload) {
        requireActiveCustomer(customerId);
        requireExistingCustomerAddress(customerId, addressId);
        AddressPayload address = normalizeAddressPayload(customerId, payload);
        if (address.isDefault()) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        jdbcTemplate.update(
            """
                UPDATE customer_addresses
                SET contact_name = ?, contact_phone = ?, address_line = ?, area_code = ?, is_default = ?
                WHERE id = ? AND customer_id = ?
                """,
            address.contactName(),
            address.contactPhone(),
            address.addressLine(),
            address.areaCode(),
            address.isDefault(),
            addressId,
            customerId
        );
        return Map.of("customerId", customerId, "addressId", addressId, "status", "UPDATED");
    }

    @Override
    @Transactional
    public Map<String, Object> deleteCustomerAddress(long customerId, long addressId) {
        requireActiveCustomer(customerId);
        requireExistingCustomerAddress(customerId, addressId);
        Integer addressCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE customer_id = ?",
            Integer.class,
            customerId
        );
        if (addressCount != null && addressCount <= 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少保留一个地址");
        }
        Boolean wasDefault = jdbcTemplate.queryForObject(
            "SELECT is_default FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Boolean.class,
            addressId,
            customerId
        );
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ? AND customer_id = ?", addressId, customerId);
        if (Boolean.TRUE.equals(wasDefault)) {
            List<Long> remainingIds = jdbcTemplate.query(
                "SELECT id FROM customer_addresses WHERE customer_id = ? ORDER BY id ASC",
                (rs, rowNum) -> rs.getLong("id"),
                customerId
            );
            if (!remainingIds.isEmpty()) {
                jdbcTemplate.update("UPDATE customer_addresses SET is_default = TRUE WHERE id = ?", remainingIds.get(0));
            }
        }
        return Map.of("customerId", customerId, "addressId", addressId, "status", "DELETED");
    }

    @Override
    @Transactional
    public Map<String, Object> grantMeals(long customerId, WalletAdjustRequest request) {
        MealWalletEntity wallet = findOrCreateWallet(customerId);
        wallet.setTotalMeals(nvl(wallet.getTotalMeals()) + request.mealDelta());
        mealWalletMapper.updateById(wallet);
        insertWalletTransaction(wallet.getId(), "GRANT", request.mealDelta(), request.operatorName(), request.remark());
        int remainingMeals = remainingMeals(wallet);
        return buildAdjustResult(customerId, remainingMeals);
    }

    @Override
    @Transactional
    public Map<String, Object> deductMeals(long customerId, WalletAdjustRequest request) {
        MealWalletEntity wallet = findOrCreateWallet(customerId);
        int remainingMeals = remainingMeals(wallet);
        if (remainingMeals < request.mealDelta()) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "客户余额不足，无法继续扣餐");
        }
        int nextTotal = nvl(wallet.getTotalMeals()) - request.mealDelta();
        wallet.setTotalMeals(nextTotal);
        mealWalletMapper.updateById(wallet);
        insertWalletTransaction(wallet.getId(), "MANUAL_DEDUCT", -request.mealDelta(), request.operatorName(), request.remark());
        remainingMeals = remainingMeals(wallet);
        return buildAdjustResult(customerId, remainingMeals);
    }

    @Override
    public PageResponse<WalletTransactionResponse> walletTransactions(long customerId) {
        MealWalletEntity wallet = findOrCreateWallet(customerId);
        List<WalletTransactionEntity> records = walletTransactionMapper.selectList(
            new LambdaQueryWrapper<WalletTransactionEntity>()
                .eq(WalletTransactionEntity::getWalletId, wallet.getId())
                .orderByDesc(WalletTransactionEntity::getId)
        );

        List<WalletTransactionResponse> items = records.stream().map(tx -> new WalletTransactionResponse(
            tx.getId(),
            customerId,
            tx.getTransactionType(),
            nvl(tx.getMealDelta()),
            tx.getOperatorName(),
            tx.getRemark() == null ? "" : tx.getRemark(),
            tx.getRelatedOrderId(),
            tx.getRelatedAftersaleId(),
            tx.getRelatedTransactionId(),
            Boolean.TRUE.equals(tx.getRefunded()),
            tx.getRefundReasonCode() == null ? "" : tx.getRefundReasonCode(),
            tx.getRefundReasonText() == null ? "" : tx.getRefundReasonText(),
            formatDateTime(tx.getCreatedAt())
        )).toList();

        return PageResponse.of(items, 1, 20, items.size());
    }

    @Override
    public RemarkSuggestionResponse remarkSuggestions(String scene, Long customerId) {
        String normalizedScene = blankToDefault(stringValue(scene), "ORDER_REMARK").toUpperCase();
        List<String> items = switch (normalizedScene) {
            case "CUSTOMER_REMARK" -> recentDistinct(querySuggestionValues(
                "SELECT merchant_remark FROM customers WHERE merchant_remark IS NOT NULL ORDER BY updated_at DESC, id DESC"
            ));
            case "PRIORITY_NOTE" -> recentDistinct(querySuggestionValues(
                "SELECT priority_note FROM customers WHERE priority_note IS NOT NULL ORDER BY updated_at DESC, id DESC"
            ));
            case "WALLET_REMARK" -> recentDistinct(querySuggestionValues(
                "SELECT remark FROM wallet_transactions WHERE remark IS NOT NULL ORDER BY created_at DESC, id DESC"
            ));
            case "RECEIPT_NOTE" -> recentDistinct(querySuggestionValues(
                "SELECT receipt_note FROM delivery_receipts WHERE receipt_note IS NOT NULL ORDER BY delivered_at DESC, id DESC"
            ));
            case "SUBSCRIPTION_NOTE" -> recentDistinct(querySuggestionValues(
                customerId != null ?
                "SELECT merchant_remark FROM subscription_rules WHERE customer_id = " + customerId + " AND merchant_remark IS NOT NULL ORDER BY id DESC" :
                "SELECT merchant_remark FROM subscription_rules WHERE merchant_remark IS NOT NULL ORDER BY id DESC"
            ));
            case "MENU_NOTE" -> recentDistinct(querySuggestionValues(
                "SELECT merchant_note FROM menu_week_items WHERE merchant_note IS NOT NULL ORDER BY serve_date DESC, id DESC"
            ));
            case "COST_REMARK" -> recentDistinct(querySuggestionValues(
                "SELECT remark FROM cost_entries WHERE remark IS NOT NULL ORDER BY created_at DESC, id DESC"
            ));
            case "ORDER_REMARK" -> recentDistinct(
                querySuggestionValues(
                    customerId != null ? 
                    "SELECT m.note FROM meal_slot_orders m JOIN daily_orders d ON m.daily_order_id = d.id WHERE d.customer_id = " + customerId + " AND m.note IS NOT NULL ORDER BY m.id DESC" :
                    "SELECT note FROM meal_slot_orders WHERE note IS NOT NULL ORDER BY id DESC"
                ),
                querySuggestionValues(
                    customerId != null ?
                    "SELECT m.user_note FROM meal_slot_orders m JOIN daily_orders d ON m.daily_order_id = d.id WHERE d.customer_id = " + customerId + " AND m.user_note IS NOT NULL ORDER BY m.id DESC" :
                    "SELECT user_note FROM meal_slot_orders WHERE user_note IS NOT NULL ORDER BY id DESC"
                ),
                querySuggestionValues(
                    customerId != null ?
                    "SELECT merchant_remark FROM subscription_rules WHERE customer_id = " + customerId + " AND merchant_remark IS NOT NULL ORDER BY id DESC" :
                    "SELECT merchant_remark FROM subscription_rules WHERE merchant_remark IS NOT NULL ORDER BY id DESC"
                )
            );
            default -> List.of();
        };
        return new RemarkSuggestionResponse(normalizedScene, items);
    }

    private MealWalletEntity findActiveWallet(long customerId) {
        MealWalletEntity wallet = mealWalletMapper.selectOne(
            new LambdaQueryWrapper<MealWalletEntity>()
                .eq(MealWalletEntity::getCustomerId, customerId)
                .eq(MealWalletEntity::getActive, true)
                .last("LIMIT 1")
        );
        if (wallet == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户钱包不存在");
        }
        return wallet;
    }

    private MealWalletEntity findOrCreateWallet(long customerId) {
        MealWalletEntity wallet = mealWalletMapper.selectOne(
            new LambdaQueryWrapper<MealWalletEntity>()
                .eq(MealWalletEntity::getCustomerId, customerId)
                .eq(MealWalletEntity::getActive, true)
                .last("LIMIT 1")
        );
        if (wallet == null) {
            wallet = createInitialWallet(customerId);
        }
        return wallet;
    }

    private MealWalletEntity createInitialWallet(long customerId) {
        LocalDateTime now = LocalDateTime.now();
        MealWalletEntity wallet = new MealWalletEntity();
        wallet.setCustomerId(customerId);
        wallet.setTotalMeals(0);
        wallet.setReservedMeals(0);
        wallet.setConsumedMeals(0);
        wallet.setActive(true);
        mealWalletMapper.insert(wallet);
        return wallet;
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark) {
        WalletTransactionEntity tx = new WalletTransactionEntity();
        tx.setWalletId(walletId);
        tx.setTransactionType(transactionType);
        tx.setBizType(transactionType);
        tx.setMealDelta(mealDelta);
        tx.setOperatorName(operatorName);
        tx.setRemark(remark);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setSnapshotBalance(querySnapshotBalance(walletId));
        walletTransactionMapper.insert(tx);
    }

    private List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String part : raw.split("[,，\\n]")) {
            String normalized = blankToNull(part);
            if (normalized != null) {
                tags.add(normalized);
            }
        }
        return new ArrayList<>(tags);
    }

    private String buildTagCode(String tagName) {
        return tagName.trim().replaceAll("\\s+", "_").toUpperCase();
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().format(DATETIME_FORMATTER);
    }

    private List<String> querySuggestionValues(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
    }

    @SafeVarargs
    private final List<String> recentDistinct(List<String>... groups) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (List<String> group : groups) {
            for (String raw : group) {
                String normalized = normalizeSuggestion(raw);
                if (normalized == null) {
                    continue;
                }
                values.add(normalized);
                if (values.size() >= 5) {
                    return new ArrayList<>(values);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private String normalizeSuggestion(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private void requireActiveCustomer(long customerId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Boolean.TRUE.equals(customer.getActive())) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }
    }

    private void requireExistingCustomerAddress(long customerId, long addressId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (count == null || count <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户地址不存在");
        }
    }

    private AddressPayload normalizeAddressPayload(long customerId, Map<String, Object> payload) {
        ContactSnapshot contact = resolveCustomerContact(customerId);
        String addressLine = blankToNull(stringValue(payload.get("addressLine")));
        String areaCode = blankToDefault(stringValue(payload.get("areaCode")), "");
        boolean isDefault = booleanValue(payload.get("isDefault"), false);
        return new AddressPayload(contact.name(), contact.phone(), requireCustomerAddressLine(addressLine), areaCode, isDefault);
    }

    private ContactSnapshot resolveCustomerContact(long customerId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Boolean.TRUE.equals(customer.getActive())) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }
        return new ContactSnapshot(
            requireCustomerName(customer.getName()),
            requireCustomerPhone(customer.getPhone())
        );
    }

    private String requireCustomerName(String name) {
        String value = blankToDefault(name, "").trim();
        if (!value.matches("^[\\u4e00-\\u9fa5A-Za-z·\\s]{2,20}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写正确的客户姓名");
        }
        return value;
    }

    private String requireCustomerPhone(String phone) {
        String value = blankToDefault(phone, "").trim();
        if (!value.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写正确的11位手机号");
        }
        return value;
    }

    private String requireCustomerAddressLine(String addressLine) {
        String value = blankToDefault(addressLine, "").trim();
        if (value.length() < 4 || value.length() > 120) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "收货地址长度需在4到120个字符之间");
        }
        return value;
    }

    private String normalizeCustomerStatus(String status) {
        String value = blankToDefault(status, "FORMAL").trim().toUpperCase();
        if ("DORMANT".equals(value)) {
            return "DORMANT";
        }
        return "FORMAL";
    }

    private String normalizeCustomerNoteType(String noteType) {
        String normalized = blankToDefault(noteType, "").trim().toUpperCase();
        if (!"USER".equals(normalized) && !"MERCHANT".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注类型不支持");
        }
        return normalized;
    }

    private String normalizeCustomerNoteScope(String noteType, String scopeType) {
        String normalized = blankToDefault(scopeType, "").trim().toUpperCase();
        if ("USER".equals(noteType)) {
            return "LONG_TERM";
        }
        if (!"LONG_TERM".equals(normalized) && !"TIME_BOXED".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注范围不支持");
        }
        return normalized;
    }

    private String requireCustomerNoteContent(String content) {
        String normalized = blankToNull(content == null ? null : content.trim());
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注内容不能为空");
        }
        if (normalized.length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注内容不能超过255个字符");
        }
        return normalized;
    }

    private void rewriteLegacyCustomerNotes(long customerId, Map<String, Object> payload) {
        requireActiveCustomer(customerId);
        boolean enabled = booleanValue(payload.get("orderPreferenceEnabled"), true);

        jdbcTemplate.update(
            """
                DELETE FROM customer_notes
                WHERE customer_id = ?
                  AND scope_type = 'LONG_TERM'
                  AND note_type IN ('USER', 'MERCHANT')
                """,
            customerId
        );

        if (!enabled) {
            return;
        }

        String defaultUserRemark = blankToNull(stringValue(payload.get("defaultUserRemark")));
        if (defaultUserRemark != null) {
            insertCustomerNote(customerId, "USER", "LONG_TERM", defaultUserRemark, null, null, 0);
        }

        LinkedHashSet<String> merchantNotes = new LinkedHashSet<>();
        String defaultMerchantRemark = blankToNull(stringValue(payload.get("defaultMerchantRemark")));
        if (defaultMerchantRemark != null) {
            merchantNotes.add(defaultMerchantRemark);
        }
        merchantNotes.addAll(splitTags(stringValue(payload.get("defaultTagsText"))));

        int displayOrder = 0;
        for (String merchantNote : merchantNotes) {
            insertCustomerNote(customerId, "MERCHANT", "LONG_TERM", merchantNote, null, null, displayOrder++);
        }
    }

    private void insertCustomerNote(
        long customerId,
        String noteType,
        String scopeType,
        String content,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int displayOrder
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO customer_notes (
                    customer_id, note_type, scope_type, content, start_at, end_at, is_active, display_order, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?)
                """,
            customerId,
            noteType,
            scopeType,
            content,
            startAt == null ? null : Timestamp.valueOf(startAt),
            endAt == null ? null : Timestamp.valueOf(endAt),
            displayOrder,
            "ADMIN",
            "ADMIN"
        );
    }

    private int remainingMeals(MealWalletEntity wallet) {
        return nvl(wallet.getTotalMeals()) - nvl(wallet.getReservedMeals()) - nvl(wallet.getConsumedMeals());
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATETIME_FORMATTER.format(value);
    }

    private boolean matchesKeyword(CustomerAssetResponse item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim();
        return item.name().contains(normalized)
            || item.phone().contains(normalized)
            || (item.merchantRemark() != null && item.merchantRemark().contains(normalized));
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private boolean matchesText(String source, String target) {
        if (target == null || target.isBlank()) {
            return true;
        }
        return target.equalsIgnoreCase(blankToDefault(source, ""));
    }

    private Set<Long> fixedSubscriptionCustomerIds() {
        return new HashSet<>(jdbcTemplate.query(
            "SELECT DISTINCT customer_id FROM subscription_rules WHERE active = TRUE AND (paused = FALSE OR paused IS NULL)",
            (rs, rowNum) -> rs.getLong(1)
        ));
    }

    private Integer querySnapshotBalance(long walletId) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT total_meals - reserved_meals - consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            walletId
        );
        return value == null ? 0 : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (!normalized.isEmpty()) {
                try {
                    return Integer.parseInt(normalized);
                } catch (NumberFormatException ex) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "餐次数量格式不正确");
                }
            }
        }
        return fallback;
    }

    private record AddressPayload(
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
    }

    private record ContactSnapshot(
        String name,
        String phone
    ) {
    }


    private Map<String, Object> buildAdjustResult(long customerId, int remainingMeals) {
        Map<String, Object> result = new HashMap<>();
        result.put("customerId", customerId);
        result.put("remainingMeals", remainingMeals);
        result.put("status", remainingMeals > 0 ? "ACTIVE" : "EXHAUSTED");
        return result;
    }
}
