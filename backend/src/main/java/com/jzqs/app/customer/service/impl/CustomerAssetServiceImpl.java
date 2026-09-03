package com.jzqs.app.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.security.AdminRequestContext;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.customer.api.CustomerAddressActionResponse;
import com.jzqs.app.customer.api.CustomerAddressDetailResponse;
import com.jzqs.app.customer.api.CustomerAddressUpsertRequest;
import com.jzqs.app.customer.api.CustomerAssetResponse;
import com.jzqs.app.customer.api.CustomerBatchExtendRequest;
import com.jzqs.app.customer.api.CustomerBatchExtendResponse;
import com.jzqs.app.customer.api.CustomerDetailResponse;
import com.jzqs.app.customer.api.CustomerProfileCreateRequest;
import com.jzqs.app.customer.api.CustomerProfileCreateResponse;
import com.jzqs.app.customer.api.CustomerProfileUpdateRequest;
import com.jzqs.app.customer.api.CustomerProfileUpdateResponse;
import com.jzqs.app.customer.api.CustomerSubscriptionDetailResponse;
import com.jzqs.app.customer.api.CustomerWalletDetailResponse;
import com.jzqs.app.customer.api.CustomerWalletAdjustResponse;
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
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@Service
public class CustomerAssetServiceImpl implements CustomerAssetService {
    private static final Logger log = LoggerFactory.getLogger(CustomerAssetServiceImpl.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 统一使用北京时间作为加餐/有效期计算的基准时区 */
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

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
        PackageReminderSettings reminderSettings = loadPackageReminderSettings();

        List<CustomerAssetResponse> items = customers.stream().map(customer -> {
            MealWalletEntity wallet = walletMap.get(customer.getId());
            int remainingMeals = wallet == null ? 0 : remainingMeals(wallet);
            boolean hasOpenedCard = wallet != null;
            boolean fixedEnabled = fixedCustomerIds.contains(customer.getId());
            PackageAlert packageAlert = evaluatePackageAlert(wallet, remainingMeals, reminderSettings);
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
                formatDateTime(wallet == null ? null : wallet.getOpenedAt()),
                formatDate(wallet == null ? null : wallet.getExpiredAt()),
                remainingValidityDays(wallet == null ? null : wallet.getExpiredAt()),
                packageAlert.code(),
                packageAlert.label(),
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
    public CustomerDetailResponse customerDetail(long customerId) {
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
        List<CustomerAddressDetailResponse> addresses = jdbcTemplate.query(
            "SELECT id, contact_name, contact_phone, address_line, area_code, is_default, latitude, longitude FROM customer_addresses WHERE customer_id = ? AND active = TRUE ORDER BY is_default DESC, id ASC",
            (rs, rowNum) -> new CustomerAddressDetailResponse(
                rs.getLong("id"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getString("address_line"),
                rs.getString("area_code"),
                rs.getBoolean("is_default"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude")
            ),
            customerId
        );
        List<CustomerSubscriptionDetailResponse> subscriptions = jdbcTemplate.query(
            "SELECT id, lunch_enabled, dinner_enabled, start_date, end_date, merchant_remark, is_priority_follow, paused FROM subscription_rules WHERE customer_id = ? ORDER BY id DESC",
            (rs, rowNum) -> new CustomerSubscriptionDetailResponse(
                rs.getLong("id"),
                rs.getBoolean("lunch_enabled"),
                rs.getBoolean("dinner_enabled"),
                rs.getDate("start_date") == null ? null : rs.getDate("start_date").toLocalDate().toString(),
                rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate().toString(),
                rs.getString("merchant_remark"),
                rs.getBoolean("is_priority_follow"),
                rs.getBoolean("paused")
            ),
            customerId
        );
        int remainingMeals = remainingMeals(wallet);
        PackageAlert packageAlert = evaluatePackageAlert(wallet, remainingMeals, loadPackageReminderSettings());
        CustomerWalletDetailResponse walletDetail = new CustomerWalletDetailResponse(
            nvl(wallet.getTotalMeals()),
            0,
            nvl(wallet.getConsumedMeals()),
            remainingMeals,
            formatDateTime(wallet.getOpenedAt()),
            formatDate(wallet.getExpiredAt()),
            remainingValidityDays(wallet.getExpiredAt()),
            packageAlert.code(),
            packageAlert.label()
        );
        return new CustomerDetailResponse(
            customer.getId(),
            customer.getName(),
            customer.getPhone(),
            normalizeCustomerStatus(customer.getCustomerStatus()),
            blankToNull(customer.getMerchantRemark()),
            Boolean.TRUE.equals(customer.getPriorityCustomer()),
            blankToNull(customer.getPriorityTag()),
            blankToNull(customer.getPriorityNote()),
            remainingMeals,
            formatDateTime(wallet.getOpenedAt()),
            formatDate(wallet.getExpiredAt()),
            remainingValidityDays(wallet.getExpiredAt()),
            formatDateTime(customer.getRegisteredAt() != null ? customer.getRegisteredAt() : customer.getCreatedAt()),
            formatDateTime(customer.getLastOrderAt()),
            walletDetail,
            addresses,
            subscriptions,
            walletTransactions(customerId).items()
        );
    }

    @Override
    @Transactional
    public CustomerProfileCreateResponse createCustomerProfile(CustomerProfileCreateRequest request) {
        String phone = blankToDefault(request == null ? null : request.phone(), "");
        String name = blankToDefault(request == null ? null : request.name(), "未命名客户");
        int initialMealDelta = request == null || request.initialMealDelta() == null ? 0 : request.initialMealDelta();
        String initialMealRemark = blankToNull(request == null ? null : request.initialMealRemark());
        int initialValidityDays = request == null || request.initialValidityDays() == null ? 0 : request.initialValidityDays();
        String customerStatus = normalizeCustomerStatus(request == null ? null : request.customerStatus());

        String addressLine = blankToNull(request == null ? null : request.addressLine());
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
        if (initialMealDelta > 0 && initialValidityDays <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "初始加餐后请同时填写有效期天数");
        }
        
        boolean nameExists = customerMapper.selectCount(new LambdaQueryWrapper<CustomerEntity>()
            .eq(CustomerEntity::getName, name)
            .eq(CustomerEntity::getActive, true)) > 0;
        if (nameExists) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户姓名已存在，请更换姓名（如加编号后缀）");
        }

        LocalDateTime now = now();
        CustomerEntity customer = new CustomerEntity();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerStatus(customerStatus);
        customer.setMerchantRemark(blankToNull(request == null ? null : request.merchantRemark()));
        customer.setPriorityCustomer(Boolean.TRUE.equals(request != null ? request.priorityCustomer() : null));
        customer.setPriorityTag(blankToNull(request == null ? null : request.priorityTag()));
        customer.setPriorityNote(blankToNull(request == null ? null : request.priorityNote()));
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
            LocalDateTime grantExpiredAt = resolveWalletExpiry(initialValidityDays);
            // 原子自增，避免初始建档加餐被并发覆盖
            int updated = jdbcTemplate.update(
                """
                    UPDATE meal_wallets
                    SET total_meals = total_meals + ?,
                        expired_at = ?,
                        last_adjusted_at = ?
                    WHERE id = ?
                    """,
                initialMealDelta,
                Timestamp.valueOf(grantExpiredAt),
                Timestamp.valueOf(now()),
                wallet.getId()
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "钱包更新失败，请重试");
            }
            insertWalletTransaction(wallet.getId(), "GRANT", initialMealDelta, currentOperator(), initialMealRemark, grantExpiredAt);
            log.info("客户建档授权初始餐次: customer={} walletId={} 餐次={} 有效期至={}",
                customer.getId(), wallet.getId(), initialMealDelta, grantExpiredAt);
        }

        return new CustomerProfileCreateResponse(customer.getId(), "CREATED");
    }

    @Override
    @Transactional
    public CustomerProfileUpdateResponse updateCustomerProfile(long customerId, CustomerProfileUpdateRequest request) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Boolean.TRUE.equals(customer.getActive())) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }

        if (request.name() != null) {
            String newName = requireCustomerName(blankToDefault(request.name(), customer.getName()));
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
        if (request.phone() != null) {
            String newPhone = requireCustomerPhone(blankToDefault(request.phone(), customer.getPhone()));
            if (!newPhone.equals(customer.getPhone()) && !newPhone.isBlank()) {
                // 仅校验活跃客户，软删（active=false）的客户视为已释放，允许复用其手机号
                CustomerEntity duplicate = customerMapper.selectOne(new LambdaQueryWrapper<CustomerEntity>()
                    .eq(CustomerEntity::getPhone, newPhone)
                    .eq(CustomerEntity::getActive, true));
                if (duplicate != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "手机号 " + newPhone + " 已被客户「" + duplicate.getName() + "」使用，无法重复绑定");
                }
                // 严格改绑：更换手机号后，原手机号与绑定的微信 openid 全部失效，
                // 原微信小程序登录态作废；新手机号需重新走验证登录流程。
                customer.setOpenid(null);
                customer.setCurrentOpenid(null);
                customer.setOpenidUpdatedAt(null);
                customer.setSessionKey(null);
            }
            customer.setPhone(newPhone);
        }
        if (request.merchantRemark() != null) {
            customer.setMerchantRemark(blankToNull(request.merchantRemark()));
        }
        if (request.customerStatus() != null) {
            customer.setCustomerStatus(normalizeCustomerStatus(request.customerStatus()));
        }

        boolean shouldPatchWalletProfile = request.openedAt() != null
            || request.expiredAt() != null
            || request.remainingValidityDays() != null;
        if (shouldPatchWalletProfile) {
            MealWalletEntity wallet = findOrCreateWallet(customerId);
            if (request.openedAt() != null) {
                wallet.setOpenedAt(parseDateTimeValue(request.openedAt()));
            }
            if (request.expiredAt() != null) {
                wallet.setExpiredAt(parseDateEndOfDayValue(request.expiredAt()));
            } else if (request.remainingValidityDays() != null) {
                wallet.setExpiredAt(resolveWalletExpiryFromRemainingDays(request.remainingValidityDays()));
            }
            wallet.setLastAdjustedAt(now());
            mealWalletMapper.updateById(wallet);
        }
        
        // 处理 defaultUserRemark 更新
        if (request.defaultUserRemark() != null) {
            String defaultUserRemark = blankToNull(request.defaultUserRemark());
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
                    """, customerId, defaultUserRemark, Timestamp.valueOf(now()), Timestamp.valueOf(now()));
            }
        }
        
        customer.setUpdatedAt(now());
        customerMapper.updateById(customer);
        jdbcTemplate.update(
            "UPDATE customer_addresses SET contact_name = ?, contact_phone = ? WHERE customer_id = ?",
            customer.getName(),
            customer.getPhone(),
            customerId
        );

        return new CustomerProfileUpdateResponse(customerId, "UPDATED");
    }

    @Override
    @Transactional
    public CustomerAddressActionResponse createCustomerAddress(long customerId, CustomerAddressUpsertRequest request) {
        requireActiveCustomer(customerId);
        AddressPayload address = normalizeAddressPayload(customerId, request);
        if (address.isDefault()) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        long addressId = insertAndReturnId(
            """
                INSERT INTO customer_addresses (
                    customer_id, contact_name, contact_phone, address_line, area_code, is_default, latitude, longitude
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            customerId,
            address.contactName(),
            address.contactPhone(),
            address.addressLine(),
            address.areaCode(),
            address.isDefault(),
            address.latitude(),
            address.longitude()
        );
        return new CustomerAddressActionResponse(customerId, addressId, "CREATED");
    }

    @Override
    @Transactional
    public CustomerAddressActionResponse updateCustomerAddress(long customerId, long addressId, CustomerAddressUpsertRequest request) {
        requireActiveCustomer(customerId);
        requireExistingCustomerAddress(customerId, addressId);
        AddressPayload address = normalizeAddressPayload(customerId, request);
        if (address.isDefault()) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        jdbcTemplate.update(
            """
                UPDATE customer_addresses
                SET contact_name = ?, contact_phone = ?, address_line = ?, area_code = ?, is_default = ?, latitude = ?, longitude = ?
                WHERE id = ? AND customer_id = ?
                """,
            address.contactName(),
            address.contactPhone(),
            address.addressLine(),
            address.areaCode(),
            address.isDefault(),
            address.latitude(),
            address.longitude(),
            addressId,
            customerId
        );
        return new CustomerAddressActionResponse(customerId, addressId, "UPDATED");
    }

    @Override
    @Transactional
    public CustomerAddressActionResponse deleteCustomerAddress(long customerId, long addressId) {
        requireActiveCustomer(customerId);
        requireExistingCustomerAddress(customerId, addressId);
        Integer addressCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE customer_id = ? AND active = TRUE",
            Integer.class,
            customerId
        );
        if (addressCount != null && addressCount <= 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少保留一个地址");
        }
        Boolean wasDefault = jdbcTemplate.queryForObject(
            "SELECT is_default FROM customer_addresses WHERE id = ? AND customer_id = ? AND active = TRUE",
            Boolean.class,
            addressId,
            customerId
        );
        // 地址删除防护：进行中（待派/配送中）的订单仍引用该地址时禁止删除，
        // 否则骑手端/订单中心按地址查询时这些订单会失去可送达地址，配送目标悬空。
        Integer activeOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE address_id = ? AND status IN ('PENDING_DISPATCH', 'DISPATCHING')",
            Integer.class,
            addressId
        );
        if (activeOrders != null && activeOrders > 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                "该地址有 " + activeOrders + " 个进行中的订单正在使用，暂时无法删除；请先为这些订单更换地址");
        }
        // 软删除：保留地址行，历史订单 address_id 永不悬空，各端口 INNER JOIN 照常显示。
        jdbcTemplate.update(
            "UPDATE customer_addresses SET active = FALSE, is_default = FALSE WHERE id = ? AND customer_id = ?",
            addressId,
            customerId
        );
        // 订阅默认地址指向被删地址时置空，避免订阅自动下单生成无地址订单
        jdbcTemplate.update(
            "UPDATE subscription_rules SET default_address_id = NULL WHERE default_address_id = ? AND customer_id = ?",
            addressId,
            customerId
        );
        // 清理该地址的骑手派单记忆与门牌参考图（地址停用后无意义）。
        jdbcTemplate.update(
            "DELETE FROM rider_address_bindings WHERE customer_id = ? AND address_id = ?",
            customerId,
            addressId
        );
        jdbcTemplate.update(
            "DELETE FROM address_reference_images WHERE customer_address_id = ?",
            addressId
        );
        if (Boolean.TRUE.equals(wasDefault)) {
            List<Long> remainingIds = jdbcTemplate.query(
                "SELECT id FROM customer_addresses WHERE customer_id = ? AND active = TRUE ORDER BY id ASC",
                (rs, rowNum) -> rs.getLong("id"),
                customerId
            );
            if (!remainingIds.isEmpty()) {
                jdbcTemplate.update("UPDATE customer_addresses SET is_default = TRUE WHERE id = ?", remainingIds.get(0));
            }
        }
        return new CustomerAddressActionResponse(customerId, addressId, "DELETED");
    }

    @Override
    @Transactional
    public CustomerWalletAdjustResponse grantMeals(long customerId, WalletAdjustRequest request) {
        // 统一过期时间：优先使用日历指定的 expiredAt，否则按当前北京时间 + validityDays 计算
        LocalDateTime grantExpiredAt = resolveGrantExpiry(request);
        MealWalletEntity wallet = findOrCreateWallet(customerId);
        // 原子自增，避免"读-改-写"整行覆盖导致并发加餐丢失
        int updated = jdbcTemplate.update(
            """
                UPDATE meal_wallets
                SET total_meals = total_meals + ?,
                    expired_at = ?,
                    last_adjusted_at = ?
                WHERE id = ?
                """,
            request.mealDelta(),
            Timestamp.valueOf(grantExpiredAt),
            Timestamp.valueOf(now()),
            wallet.getId()
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "钱包更新失败，请重试");
        }
        insertWalletTransaction(wallet.getId(), "GRANT", request.mealDelta(), request.operatorName(), request.operatorId(), request.remark(), grantExpiredAt);
        int remainingMeals = querySnapshotBalance(wallet.getId());
        return buildAdjustResult(customerId, remainingMeals);
    }

    @Override
    @Transactional
    public CustomerWalletAdjustResponse deductMeals(long customerId, WalletAdjustRequest request) {
        MealWalletEntity wallet = findOrCreateWallet(customerId);
        // 原子扣减 + 数据库层余额校验，避免并发扣成负数
        int updated = jdbcTemplate.update(
            """
                UPDATE meal_wallets
                SET total_meals = total_meals - ?,
                    last_adjusted_at = ?
                WHERE id = ?
                  AND (total_meals - consumed_meals) >= ?
                """,
            request.mealDelta(),
            Timestamp.valueOf(now()),
            wallet.getId(),
            request.mealDelta()
        );
        if (updated == 0) {
            log.warn("手动扣餐失败(余额不足): customer={} walletId={} 请求扣餐={} 操作人={}",
                customerId, wallet.getId(), request.mealDelta(), request.operatorName());
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "客户余额不足，无法继续扣餐");
        }
        insertWalletTransaction(wallet.getId(), "MANUAL_DEDUCT", -request.mealDelta(), request.operatorName(), request.operatorId(), request.remark(), null);
        int remainingMeals = querySnapshotBalance(wallet.getId());
        log.info("手动扣餐成功: customer={} walletId={} 扣餐={} 剩余={} 操作人={}",
            customerId, wallet.getId(), request.mealDelta(), remainingMeals, request.operatorName());
        return buildAdjustResult(customerId, remainingMeals);
    }

    @Override
    @Transactional
    public CustomerBatchExtendResponse batchExtendValidity(CustomerBatchExtendRequest request) {
        int extendDays = request.extendDays();
        String remark = blankToNull(request.remark());
        if (remark == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写延期原因（会展示在流水上）");
        }
        List<MealWalletEntity> wallets = mealWalletMapper.selectList(
            new LambdaQueryWrapper<MealWalletEntity>()
                .eq(MealWalletEntity::getActive, true)
        );
        LocalDate currentDay = today();
        int affected = 0;
        int skipped = 0;
        for (MealWalletEntity wallet : wallets) {
            LocalDateTime expiredAt = wallet.getExpiredAt();
            // 只给设置了到期时间且尚未过期的客户延期；无到期时间 / 已过期的跳过
            if (expiredAt == null || expiredAt.toLocalDate().isBefore(currentDay)) {
                skipped++;
                continue;
            }
            LocalDateTime nextExpiry = expiredAt.plusDays(extendDays);
            wallet.setExpiredAt(nextExpiry);
            wallet.setLastAdjustedAt(now());
            mealWalletMapper.updateById(wallet);
            insertWalletTransaction(wallet.getId(), "EXTEND_VALIDITY", 0, currentOperator(), remark, nextExpiry);
            affected++;
        }
        return new CustomerBatchExtendResponse(affected, skipped, wallets.size());
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
            tx.getOperatorId(),
            tx.getRemark() == null ? "" : tx.getRemark(),
            tx.getRelatedOrderId(),
            tx.getRelatedAftersaleId(),
            tx.getRelatedTransactionId(),
            Boolean.TRUE.equals(tx.getRefunded()),
            tx.getRefundReasonCode() == null ? "" : tx.getRefundReasonCode(),
            tx.getRefundReasonText() == null ? "" : tx.getRefundReasonText(),
            formatDateTime(tx.getCreatedAt()),
            formatDate(tx.getExpiredAtSnapshot())
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

    private LocalDateTime now() {
        return LocalDateTime.now(SHANGHAI);
    }

    private LocalDate today() {
        return LocalDate.now(SHANGHAI);
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
        MealWalletEntity wallet = findActiveWalletOrNull(customerId);
        if (wallet != null) {
            return wallet;
        }
        try {
            return createInitialWallet(customerId);
        } catch (DuplicateKeyException ex) {
            // 并发下已由其他请求创建了生效钱包（数据库唯一约束兜底），重新读取返回
            MealWalletEntity existing = findActiveWalletOrNull(customerId);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    private MealWalletEntity findActiveWalletOrNull(long customerId) {
        return mealWalletMapper.selectOne(
            new LambdaQueryWrapper<MealWalletEntity>()
                .eq(MealWalletEntity::getCustomerId, customerId)
                .eq(MealWalletEntity::getActive, true)
                .last("LIMIT 1")
        );
    }

    private MealWalletEntity createInitialWallet(long customerId) {
        LocalDateTime now = now();
        MealWalletEntity wallet = new MealWalletEntity();
        wallet.setCustomerId(customerId);
        wallet.setTotalMeals(0);
        wallet.setReservedMeals(0);
        wallet.setConsumedMeals(0);
        wallet.setActive(true);
        wallet.setOpenedAt(now);
        wallet.setLastAdjustedAt(now);
        mealWalletMapper.insert(wallet);
        return wallet;
    }

    private PackageReminderSettings loadPackageReminderSettings() {
        PackageReminderSettingsSnapshot snapshot = jdbcTemplate.queryForObject(
            "SELECT package_expiry_reminder_days, package_low_balance_threshold FROM admin_settings WHERE id = 1",
            (rs, rowNum) -> new PackageReminderSettingsSnapshot(
                rs.getObject("package_expiry_reminder_days"),
                rs.getObject("package_low_balance_threshold")
            )
        );
        return new PackageReminderSettings(
            intValue(snapshot == null ? null : snapshot.expiryReminderDays(), 7),
            intValue(snapshot == null ? null : snapshot.lowBalanceThreshold(), 3)
        );
    }

    private LocalDateTime resolveWalletExpiry(int validityDays) {
        return today().plusDays(validityDays).atTime(23, 59, 59);
    }

    private LocalDateTime resolveWalletExpiryFromRemainingDays(Object rawRemainingValidityDays) {
        String normalized = blankToNull(stringValue(rawRemainingValidityDays));
        if (normalized == null) {
            return null;
        }
        int remainingDays = Integer.parseInt(normalized);
        return today().plusDays(remainingDays).atTime(23, 59, 59);
    }

    private int remainingValidityDays(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(today(), expiredAt.toLocalDate());
    }

    /**
     * 解析加餐请求的到期时间：优先使用请求中显式指定的 expiredAt（日历选定的日期），
     * 否则按有效期天数从当前北京时间 + N 天计算。两种方式最终统一为一个过期时间。
     */
    private LocalDateTime resolveGrantExpiry(WalletAdjustRequest request) {
        String explicitExpiry = blankToNull(request.expiredAt());
        if (explicitExpiry != null) {
            return parseDateEndOfDayValue(explicitExpiry);
        }
        if (request.validityDays() == null || request.validityDays() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "有效期天数不能为空且必须大于等于 1");
        }
        return resolveWalletExpiry(request.validityDays());
    }

    private LocalDateTime parseDateTimeValue(Object rawValue) {
        String normalized = blankToNull(stringValue(rawValue));
        if (normalized == null) {
            return null;
        }
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        return LocalDateTime.parse(normalized);
    }

    private LocalDateTime parseDateEndOfDayValue(Object rawValue) {
        String normalized = blankToNull(stringValue(rawValue));
        if (normalized == null) {
            return null;
        }
        if (normalized.contains("T")) {
            String dateTime = normalized.length() == 16 ? normalized + ":00" : normalized;
            return LocalDateTime.parse(dateTime);
        }
        return LocalDate.parse(normalized, DATE_FORMATTER).atTime(23, 59, 59);
    }

    private PackageAlert evaluatePackageAlert(MealWalletEntity wallet, int remainingMeals, PackageReminderSettings settings) {
        if (wallet == null || wallet.getExpiredAt() == null) {
            return PackageAlert.none();
        }
        int remainingDays = remainingValidityDays(wallet.getExpiredAt());
        if (remainingDays < 0) {
            return new PackageAlert("EXPIRED", "已过期");
        }
        if (remainingDays <= settings.expiryReminderDays()) {
            return new PackageAlert("EXPIRING_SOON", "即将到期");
        }
        if (remainingMeals <= settings.lowBalanceThreshold()) {
            return new PackageAlert("LOW_BALANCE", "餐数不足");
        }
        return PackageAlert.none();
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, AdminRequestContext operator, String remark, LocalDateTime expiredAtSnapshot) {
        insertWalletTransaction(walletId, transactionType, mealDelta,
            operator == null ? "系统" : operator.operatorName(),
            operator == null ? null : operator.userId(),
            remark, expiredAtSnapshot);
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, Long operatorId, String remark, LocalDateTime expiredAtSnapshot) {
        WalletTransactionEntity tx = new WalletTransactionEntity();
        tx.setWalletId(walletId);
        tx.setTransactionType(transactionType);
        tx.setBizType(transactionType);
        tx.setMealDelta(mealDelta);
        tx.setOperatorName(operatorName);
        tx.setOperatorId(operatorId);
        tx.setRemark(remark);
        tx.setExpiredAtSnapshot(expiredAtSnapshot);
        tx.setCreatedAt(now());
        tx.setSnapshotBalance(querySnapshotBalance(walletId));
        walletTransactionMapper.insert(tx);
    }

    /**
     * 当前后台操作人（用于流水留痕）：非后台链路（定时任务/小程序）时为 null，流水记为"系统"
     */
    private AdminRequestContext currentOperator() {
        return AdminRequestContextSupport.currentAdminOrNull();
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
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ? AND active = TRUE",
            Integer.class,
            addressId,
            customerId
        );
        if (count == null || count <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户地址不存在");
        }
    }

    private AddressPayload normalizeAddressPayload(long customerId, CustomerAddressUpsertRequest request) {
        ContactSnapshot contact = resolveCustomerContact(customerId);
        String addressLine = blankToNull(request == null ? null : request.addressLine());
        String areaCode = blankToDefault(request == null ? null : request.areaCode(), "");
        boolean isDefault = Boolean.TRUE.equals(request != null ? request.isDefault() : null);
        BigDecimal latitude = sanitizeLatitude(request == null ? null : request.latitude());
        BigDecimal longitude = sanitizeLongitude(latitude, request == null ? null : request.longitude());
        // 经纬度必须成对：任一缺失/非法都整体视为未定位，避免只存单边坐标误导骑手导航
        if (latitude == null || longitude == null) {
            latitude = null;
            longitude = null;
        }
        return new AddressPayload(contact.name(), contact.phone(), requireCustomerAddressLine(addressLine), areaCode, isDefault, latitude, longitude);
    }

    // 坐标来自商家端地图点位（拾取器回填或手动输入）；范围非法（0,0 或越界）一律视为未定位存 NULL，
    // 避免骑手端拿坏坐标导航到错误位置。校验规则与顾客小程序端（MobileAddressModule）保持一致。
    private BigDecimal sanitizeLatitude(BigDecimal latitude) {
        if (latitude == null) {
            return null;
        }
        double value = latitude.doubleValue();
        if (value < -90 || value > 90 || value == 0) {
            return null;
        }
        return latitude;
    }

    private BigDecimal sanitizeLongitude(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        double value = longitude.doubleValue();
        if (value < -180 || value > 180 || value == 0) {
            return null;
        }
        return longitude;
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
        if (!value.matches("^[\\u4e00-\\u9fa5·]{2,20}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "姓名仅支持汉字（2-20个字符）");
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

    private void rewriteLegacyCustomerNotes(long customerId, LegacyCustomerNotesPayload payload) {
        requireActiveCustomer(customerId);
        boolean enabled = payload == null || payload.orderPreferenceEnabled() == null
            ? true
            : payload.orderPreferenceEnabled();

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

        String defaultUserRemark = blankToNull(payload == null ? null : payload.defaultUserRemark());
        if (defaultUserRemark != null) {
            insertCustomerNote(customerId, "USER", "LONG_TERM", defaultUserRemark, null, null, 0);
        }

        LinkedHashSet<String> merchantNotes = new LinkedHashSet<>();
        String defaultMerchantRemark = blankToNull(payload == null ? null : payload.defaultMerchantRemark());
        if (defaultMerchantRemark != null) {
            merchantNotes.add(defaultMerchantRemark);
        }
        merchantNotes.addAll(splitTags(payload == null ? null : payload.defaultTagsText()));

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
        return nvl(wallet.getTotalMeals()) - nvl(wallet.getConsumedMeals());
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

    private String formatDate(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATE_FORMATTER.format(value);
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
            "SELECT total_meals - consumed_meals FROM meal_wallets WHERE id = ?",
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
        boolean isDefault,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
    }

    private record ContactSnapshot(
        String name,
        String phone
    ) {
    }

    private record PackageReminderSettings(
        int expiryReminderDays,
        int lowBalanceThreshold
    ) {
    }

    private record PackageReminderSettingsSnapshot(
        Object expiryReminderDays,
        Object lowBalanceThreshold
    ) {
    }

    private record LegacyCustomerNotesPayload(
        Boolean orderPreferenceEnabled,
        String defaultUserRemark,
        String defaultMerchantRemark,
        String defaultTagsText
    ) {
    }

    private record PackageAlert(
        String code,
        String label
    ) {
        private static PackageAlert none() {
            return new PackageAlert("", "");
        }
    }


    private CustomerWalletAdjustResponse buildAdjustResult(long customerId, int remainingMeals) {
        return new CustomerWalletAdjustResponse(
            customerId,
            remainingMeals,
            remainingMeals > 0 ? "ACTIVE" : "EXHAUSTED"
        );
    }

    @Override
    @Transactional
    public void deleteCustomer(long customerId) {
        CustomerEntity existing = customerMapper.selectById(customerId);
        if (existing == null || !Boolean.TRUE.equals(existing.getActive())) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }
        // 彻底硬删：按依赖顺序物理删除该客户的所有关联数据，最后删除主档案。
        // 这些子表均以 customer_id 普通字段关联（无外键约束），因此按依赖顺序删除即可。
        // 1) 先删依赖 daily_orders / meal_wallets 的子表
        jdbcTemplate.update("DELETE FROM customer_delivery_subscriptions WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM customer_nightly_subscriptions WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM customer_notes WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM subscription_rules WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM subscription_confirmations WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM subscription_import_skips WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM rider_address_bindings WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM address_reference_images WHERE customer_address_id IN (SELECT id FROM customer_addresses WHERE customer_id = ?)", customerId);
        jdbcTemplate.update("DELETE FROM aftersale_cases WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM order_notes WHERE customer_id = ?", customerId);
        // 2) 删除订单：先删 meal_slot_orders（依赖 daily_orders），再删 daily_orders
        jdbcTemplate.update(
            "DELETE mso FROM meal_slot_orders mso INNER JOIN daily_orders d ON d.id = mso.daily_order_id WHERE d.customer_id = ?",
            customerId
        );
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ?", customerId);
        // 3) 删除钱包：先删 wallet_transactions（依赖 meal_wallets），再删 meal_wallets
        jdbcTemplate.update(
            "DELETE wt FROM wallet_transactions wt INNER JOIN meal_wallets w ON w.id = wt.wallet_id WHERE w.customer_id = ?",
            customerId
        );
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id = ?", customerId);
        // 4) 删除配送地址与主档案
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", customerId);
        customerMapper.deleteById(customerId);
    }
}
