package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileDefaultAddressResponse;
import com.jzqs.app.mobile.api.MobileOrderAddressChangeResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class MobileAddressModule {
    private static final int MAX_ADDRESSES_PER_CUSTOMER = 5;

    private final JdbcTemplate jdbcTemplate;

    MobileAddressModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private record CustomerMealPeriodRow(long customerId, String mealPeriod) {}

    private static String normalizeMealPeriod(String mealPeriod) {
        if (mealPeriod == null) {
            return null;
        }
        String upper = mealPeriod.trim().toUpperCase();
        if (upper.equals("LUNCH") || upper.equals("DINNER")) {
            return upper;
        }
        return mealPeriod;
    }

    List<MobileAddressResponse> customerAddresses(long customerId) {
        return jdbcTemplate.query(
            """
                SELECT id, contact_name, contact_phone, address_line, area_code, is_default
                FROM customer_addresses
                WHERE customer_id = ?
                ORDER BY is_default DESC, id ASC
                """,
            (rs, rowNum) -> new MobileAddressResponse(
                rs.getLong("id"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getString("address_line"),
                rs.getString("area_code"),
                rs.getBoolean("is_default")
            ),
            customerId
        );
    }

    MobileAddressResponse saveCustomerAddress(
        long customerId,
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
        ContactSnapshot contact = resolveCustomerAddressContact(customerId);
        String finalAddressLine = requireAddressLine(addressLine);
        String finalAreaCode = areaCode == null ? "" : areaCode.trim();
        if (isDefault) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        Integer addressCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE customer_id = ?",
            Integer.class,
            customerId
        );
        if (addressCount != null && addressCount >= MAX_ADDRESSES_PER_CUSTOMER) {
            throw new BusinessException(
                ErrorCode.ADDRESS_LIMIT_EXCEEDED,
                "每个用户最多保存 " + MAX_ADDRESSES_PER_CUSTOMER + " 个收货地址，请先删除一个再新增"
            );
        }
        long addressId = insertAndReturnId(
            """
                INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            customerId,
            // Keep current behavior: address book contact info follows customer profile.
            contact.name(),
            contact.phone(),
            finalAddressLine,
            finalAreaCode,
            isDefault
        );
        return new MobileAddressResponse(addressId, contact.name(), contact.phone(), finalAddressLine, finalAreaCode, isDefault);
    }

    MobileAddressResponse updateCustomerAddress(
        long customerId,
        long addressId,
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, "未找到该地址");
        }
        
        ContactSnapshot contact = resolveCustomerAddressContact(customerId);
        String finalAddressLine = requireAddressLine(addressLine);
        String finalAreaCode = areaCode == null ? "" : areaCode.trim();
        
        if (isDefault) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }
        
        jdbcTemplate.update(
            """
                UPDATE customer_addresses 
                SET contact_name = ?, contact_phone = ?, address_line = ?, area_code = ?, is_default = ?
                WHERE id = ? AND customer_id = ?
                """,
            contact.name(),
            contact.phone(),
            finalAddressLine,
            finalAreaCode,
            isDefault,
            addressId,
            customerId
        );
        
        return new MobileAddressResponse(addressId, contact.name(), contact.phone(), finalAddressLine, finalAreaCode, isDefault);
    }

    void deleteCustomerAddress(long customerId, long addressId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, "未找到该地址");
        }
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ? AND customer_id = ?", addressId, customerId);
    }

    MobileDefaultAddressResponse setDefaultAddress(long customerId, long addressId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该地址");
        }
        jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        jdbcTemplate.update("UPDATE customer_addresses SET is_default = TRUE WHERE id = ?", addressId);
        return new MobileDefaultAddressResponse(addressId, "DEFAULT_UPDATED");
    }

    MobileOrderAddressChangeResponse changeCustomerOrderAddress(long customerId, long orderId, long addressId) {
        return changeCustomerOrderAddressInternal(customerId, orderId, addressId, true);
    }

    /**
     * 商家后台代客改址：跳过「送餐当天不可改」的时间窗口限制（顾客端仍受限制），
     * 但保留同地址校验与派单区域重分配，避免误操作。顾客当天来联系时，由商家判断后再改。
     */
    MobileOrderAddressChangeResponse changeCustomerOrderAddressByMerchant(long customerId, long orderId, long addressId) {
        return changeCustomerOrderAddressInternal(customerId, orderId, addressId, false);
    }

    private MobileOrderAddressChangeResponse changeCustomerOrderAddressInternal(long customerId, long orderId, long addressId, boolean enforceWindow) {
        CustomerOrderAddressRow order = jdbcTemplate.query(
            """
                SELECT mso.address_id, do.serve_date
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ? AND do.customer_id = ?
                """,
            ps -> {
                ps.setLong(1, orderId);
                ps.setLong(2, customerId);
            },
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerOrderAddressRow(
                    rs.getLong("address_id"),
                    rs.getObject("serve_date", LocalDate.class)
                );
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        if (enforceWindow && !canChangeAddress(order.serveDate())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "送餐当天请联系客服修改地址");
        }
        Integer addressCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
            Integer.class,
            addressId,
            customerId
        );
        if (addressCount == null || addressCount == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, "未找到该地址");
        }
        if (order.addressId() == addressId) {
            return new MobileOrderAddressChangeResponse(orderId, addressId, "ADDRESS_UNCHANGED");
        }
        jdbcTemplate.update("UPDATE meal_slot_orders SET address_id = ? WHERE id = ?", addressId, orderId);
        // 换地址后让派单区域与新地址接轨：自动派单本身是实时 JOIN 新 address_id 的，
        // 这里主要修正「已派单订单」的 dispatch_assignments 区域快照，避免改址后派单区域错乱。
        reconcileDispatchArea(orderId, addressId);
        return new MobileOrderAddressChangeResponse(orderId, addressId, "ADDRESS_UPDATED");
    }

    /**
     * 区域记忆以「地址」为单位，而非「用户」。换地址后的区域重分配规则：
     * - 新地址之前派过单（记忆表有记录）→ 直接取其自身的区域，订单快照同步为新地址区域；
     * - 新地址从未派过单（记忆表无记录）→ 不继承旧地址的区域，写入一条 area_code 为空的
     *   「地址变更待确认」绑定，由商家后台手动分配；对应已派单订单的快照区域置为待分配标记，
     *   不再沿用旧区域。
     * - 若订单尚未派单（无 dispatch_assignments 行），自动派单环节会基于新 address_id 实时 JOIN，无需处理。
     */
    private static final String AREA_PENDING = "PENDING";

    private void reconcileDispatchArea(long orderId, long newAddressId) {
        CustomerMealPeriodRow row = jdbcTemplate.query(
            "SELECT do.customer_id, mso.meal_period FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE mso.id = ?",
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerMealPeriodRow(rs.getLong("customer_id"), rs.getString("meal_period"));
            }
        );
        if (row == null) {
            return;
        }
        long customerId = row.customerId();
        String mealPeriod = normalizeMealPeriod(row.mealPeriod());
        // 新地址是否有「已确认」的区域记忆（rider_profile_id 非空才算真实记忆，
        // 空壳待确认记录 area_code='' 不代表已分配，必须走重新分配流程）。
        // 记忆按餐期区分：优先取同餐期记忆，无同餐期时回退到通用(NULL)记忆。
        String newArea = jdbcTemplate.query(
            """
                SELECT area_code
                FROM rider_address_bindings
                WHERE customer_id = ? AND address_id = ? AND rider_profile_id IS NOT NULL
                  AND (meal_period <=> ? OR meal_period IS NULL)
                ORDER BY CASE WHEN meal_period <=> ? THEN 0 ELSE 1 END, id DESC
                LIMIT 1
                """,
            (rs, rn) -> rs.getString("area_code"),
            customerId,
            newAddressId,
            mealPeriod,
            mealPeriod
        ).stream().findFirst().orElse(null);
        if (newArea == null || newArea.isBlank()) {
            // 新地址无已确认记忆：不沿用旧地址区域，留空待商家手动分配，
            // 并同步写入/刷新「地址变更待确认」绑定，供分单工作台与异常单展示。
            jdbcTemplate.update(
                "INSERT INTO rider_address_bindings (customer_id, address_id, meal_period, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at) "
                    + "SELECT do.customer_id, ?, ?, '', NULL, FALSE, 'ADDRESS_CHANGED_PENDING_CONFIRM', CURRENT_TIMESTAMP "
                    + "FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE mso.id = ? "
                    + "ON DUPLICATE KEY UPDATE area_code = '', rider_profile_id = NULL, updated_reason = VALUES(updated_reason), updated_at = CURRENT_TIMESTAMP",
                newAddressId,
                mealPeriod,
                orderId
            );
            newArea = AREA_PENDING;
        }
        // 仅当订单已派单（存在 dispatch_assignments 行）时才刷新区域快照；
        // 未派单订单由自动派单环节基于新 address_id 实时 JOIN，无需处理。
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET area_code = ? WHERE meal_slot_order_id = ?",
            newArea,
            orderId
        );
    }

    private ContactSnapshot resolveCustomerAddressContact(long customerId) {
        CustomerContactRow customer = jdbcTemplate.query(
            "SELECT name, phone FROM customers WHERE id = ? AND active = TRUE",
            ps -> ps.setLong(1, customerId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
                }
                return new CustomerContactRow(rs.getString("name"), rs.getString("phone"));
            }
        );
        String finalName = safeString(customer.name()).trim();
        String finalPhone = safeString(customer.phone()).replaceAll("\\D", "");
        if (finalName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先完善姓名");
        }
        if (!finalPhone.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先完善手机号");
        }
        return new ContactSnapshot(finalName, finalPhone);
    }

    private String requireAddressLine(String addressLine) {
        String value = addressLine == null ? "" : addressLine.trim();
        if (value.length() < 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "详细地址至少 4 个字");
        }
        if (value.length() > 120) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "详细地址不能超过120个字");
        }
        return value;
    }

    private boolean canChangeAddress(LocalDate serveDate) {
        return serveDate != null && serveDate.isAfter(LocalDate.now());
    }

    private long insertAndReturnId(String sql, Object... args) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ContactSnapshot(String name, String phone) {
    }

    private record CustomerContactRow(String name, String phone) {
    }

    private record CustomerOrderAddressRow(long addressId, LocalDate serveDate) {
    }
}
