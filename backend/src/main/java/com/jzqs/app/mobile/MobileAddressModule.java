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
    private final JdbcTemplate jdbcTemplate;

    MobileAddressModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        if (!canChangeAddress(order.serveDate())) {
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
        jdbcTemplate.update("UPDATE meal_slot_orders SET address_id = ? WHERE id = ?", addressId, orderId);
        return new MobileOrderAddressChangeResponse(orderId, addressId, "ADDRESS_UPDATED");
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
