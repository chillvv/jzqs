package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileDefaultAddressResponse;
import com.jzqs.app.mobile.api.MobileOrderAddressChangeResponse;
import com.jzqs.app.order.persistence.OrderDispatchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class MobileAddressModule {
    private static final Logger log = LoggerFactory.getLogger(MobileAddressModule.class);
    private static final int MAX_ADDRESSES_PER_CUSTOMER = 5;

    private final JdbcTemplate jdbcTemplate;
    private final OrderDispatchRepository orderDispatchRepository;

    MobileAddressModule(JdbcTemplate jdbcTemplate, OrderDispatchRepository orderDispatchRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderDispatchRepository = orderDispatchRepository;
    }

    private record CustomerMealPeriodRow(long customerId, String mealPeriod, String status) {}

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
                SELECT id, contact_name, contact_phone, address_line, door_number, area_code, is_default, latitude, longitude
                FROM customer_addresses
                WHERE customer_id = ?
                ORDER BY is_default DESC, id ASC
                """,
            (rs, rowNum) -> new MobileAddressResponse(
                rs.getLong("id"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getString("address_line"),
                rs.getString("door_number"),
                rs.getString("area_code"),
                rs.getBoolean("is_default"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude")
            ),
            customerId
        );
    }

    MobileAddressResponse saveCustomerAddress(
        long customerId,
        String contactName,
        String contactPhone,
        String addressLine,
        String doorNumber,
        String areaCode,
        boolean isDefault,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        ContactSnapshot contact = resolveCustomerAddressContact(customerId);
        String finalAddressLine = requireAddressLine(addressLine);
        String finalDoorNumber = doorNumber == null ? null : doorNumber.trim();
        String finalAreaCode = areaCode == null ? "" : areaCode.trim();
        BigDecimal finalLatitude = sanitizeLatitude(latitude);
        BigDecimal finalLongitude = sanitizeLongitude(finalLatitude, longitude);
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
                INSERT INTO customer_addresses (customer_id, contact_name, contact_phone, address_line, door_number, area_code, is_default, latitude, longitude)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            customerId,
            // Keep current behavior: address book contact info follows customer profile.
            contact.name(),
            contact.phone(),
            finalAddressLine,
            finalDoorNumber,
            finalAreaCode,
            isDefault,
            finalLatitude,
            finalLongitude
        );
        log.info("客户新增地址: customer={} addressId={} area_code={} isDefault={} 定位={}",
            customerId, addressId, finalAreaCode, isDefault, finalLatitude != null);
        return new MobileAddressResponse(addressId, contact.name(), contact.phone(), finalAddressLine, finalDoorNumber, finalAreaCode, isDefault, finalLatitude, finalLongitude);
    }

    MobileAddressResponse updateCustomerAddress(
        long customerId,
        long addressId,
        String contactName,
        String contactPhone,
        String addressLine,
        String doorNumber,
        String areaCode,
        boolean isDefault,
        BigDecimal latitude,
        BigDecimal longitude
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
        String finalDoorNumber = doorNumber == null ? null : doorNumber.trim();
        String finalAreaCode = areaCode == null ? "" : areaCode.trim();
        BigDecimal finalLatitude = sanitizeLatitude(latitude);
        BigDecimal finalLongitude = sanitizeLongitude(finalLatitude, longitude);

        if (isDefault) {
            jdbcTemplate.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?", customerId);
        }

        jdbcTemplate.update(
            """
                UPDATE customer_addresses
                SET contact_name = ?, contact_phone = ?, address_line = ?, door_number = ?, area_code = ?, is_default = ?, latitude = ?, longitude = ?
                WHERE id = ? AND customer_id = ?
                """,
            contact.name(),
            contact.phone(),
            finalAddressLine,
            finalDoorNumber,
            finalAreaCode,
            isDefault,
            finalLatitude,
            finalLongitude,
            addressId,
            customerId
        );

        return new MobileAddressResponse(addressId, contact.name(), contact.phone(), finalAddressLine, finalDoorNumber, finalAreaCode, isDefault, finalLatitude, finalLongitude);
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
        // 地址删除防护：进行中（待派/配送中）的订单仍引用该地址时禁止删除，
        // 否则订单中心/骑手中心按地址 INNER JOIN 查询会把这些订单"藏"起来，
        // 造成三端数量对不上（9.2 事故：吴天豪 3 个进行中订单因地址被删而从订单中心消失）。
        Integer activeOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE address_id = ? AND status IN ('PENDING_DISPATCH', 'DISPATCHING')",
            Integer.class,
            addressId
        );
        if (activeOrders != null && activeOrders > 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                "该地址有 " + activeOrders + " 个进行中的订单正在使用，暂时无法删除；请先更换这些订单的地址");
        }
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ? AND customer_id = ?", addressId, customerId);
        // 订阅默认地址指向被删地址时置空，避免订阅自动下单生成无地址订单
        jdbcTemplate.update(
            "UPDATE subscription_rules SET default_address_id = NULL WHERE default_address_id = ? AND customer_id = ?",
            addressId,
            customerId
        );
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
                SELECT mso.address_id, do.serve_date, mso.status
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
                    rs.getObject("serve_date", LocalDate.class),
                    rs.getString("status")
                );
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        if (order.status() != null && (order.status().equals("CANCELLED") || order.status().equals("REFUNDED"))) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该订单已取消或已退款，无法更换地址");
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
        log.info("订单换地址: orderId={} customer={} 新addressId={} 旧addressId={} enforceWindow={}",
            orderId, customerId, addressId, order.addressId(), enforceWindow);
        return new MobileOrderAddressChangeResponse(orderId, addressId, "ADDRESS_UPDATED");
    }

    /**
     * 区域记忆以「地址」为单位，而非「用户」。换地址后的区域重分配规则：
     * - 新地址之前派过单（记忆表有已确认记录）→ 撤销当前派单并回退待派状态，
     *   由分单工作台的自动归区（ensureRememberedAssignments）按新地址记忆重新归区、
     *   派到记忆区域的默认骑手；
     * - 新地址从未派过单（记忆表无已确认记录）→ 不继承旧地址的区域，写入一条 area_code
     *   为空的「地址变更待确认」绑定，订单撤销派单后停留在「待分配」，
     *   由商家在工作台手动归区兜底（异常单列表会提示"新地址，尚未确认区域"）。
     * 两种情况都会撤销原有派单快照（含骑手批次项、空批次清理）。
     * 禁止把快照写成不存在的假区域：旧实现写入魔法值 'PENDING'，导致骑手中心
     * 按真实区域对账少单、工作台"待分配"又因派单行存在而不可见的静默死锁（9.1 事故 503；
     * 9.2 复发：换到无记忆地址的 3 单快照被写成 'PENDING'，骑手中心多出一个不存在的区域）。
     */
    private void reconcileDispatchArea(long orderId, long newAddressId) {
        CustomerMealPeriodRow row = jdbcTemplate.query(
            "SELECT do.customer_id, mso.meal_period, mso.status FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE mso.id = ?",
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerMealPeriodRow(rs.getLong("customer_id"), rs.getString("meal_period"), rs.getString("status"));
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
        boolean hasRememberedArea = newArea != null && !newArea.isBlank();
        if (!hasRememberedArea) {
            // 新地址无已确认记忆：不沿用旧地址区域，写入/刷新「地址变更待确认」空壳绑定，
            // 供分单工作台与异常单展示（订单停留在待分配，由商家手动归区）。
            jdbcTemplate.update(
                // 列序：customer_id, address_id, meal_period, address_fingerprint,
                //       area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at
                // area_code 列 NOT NULL 不允许 NULL，空壳语义用 ''（rider_profile_id=NULL）。
                "INSERT INTO rider_address_bindings (customer_id, address_id, meal_period, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason, updated_at) "
                    + "SELECT do.customer_id, ?, ?, '', '', NULL, FALSE, 'ADDRESS_CHANGED_PENDING_CONFIRM', CURRENT_TIMESTAMP "
                    + "FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE mso.id = ? "
                    + "ON DUPLICATE KEY UPDATE area_code = '', rider_profile_id = NULL, updated_reason = VALUES(updated_reason), updated_at = CURRENT_TIMESTAMP",
                newAddressId,
                mealPeriod,
                orderId
            );
            log.info("换地址后新地址无已确认记忆，写入空壳绑定(area_code='')待商家分配: orderId={} customer={} address={} mealPeriod={}",
                orderId, customerId, newAddressId, mealPeriod);
        }
        // 撤销该订单的原有派单（含骑手批次项、空批次清理），仅限未配送终态的订单；
        // 商家端可修改任意日期订单，已送达(DELIVERED)订单的派单/回执记录必须保留。
        // 注意：绝不把快照改写成 'PENDING' 假区域——撤销派单让订单回工作台「待分配」才是正确语义。
        if ("PENDING_DISPATCH".equals(row.status()) || "DISPATCHING".equals(row.status())) {
            orderDispatchRepository.resetDispatchFlow(orderId);
            jdbcTemplate.update(
                "UPDATE meal_slot_orders SET status = 'PENDING_DISPATCH' WHERE id = ? AND status = 'DISPATCHING'",
                orderId
            );
            log.info("换地址后撤销原派单、订单回退待派状态: orderId={} customer={} 新addressId={} 记忆区域={}",
                orderId, customerId, newAddressId, hasRememberedArea ? newArea : "无(待商家分配)");
        }
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

    // 坐标来自用户端 wx.chooseLocation 选点；范围非法（如传了 0,0 或越界值）一律视为未定位存 NULL，
    // 避免骑手端拿坏坐标导航到错误位置。
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

    private record CustomerOrderAddressRow(long addressId, LocalDate serveDate, String status) {
    }
}
