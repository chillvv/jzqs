package com.jzqs.app.admin.persistence;

import com.jzqs.app.dispatch.api.DispatchBoardItemResponse;
import com.jzqs.app.menu.api.MenuScheduleResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import org.springframework.jdbc.core.RowMapper;

public final class AdminRowMappers {
    private AdminRowMappers() {
    }

    public static final RowMapper<OrderPrepItemResponse> ORDER_PREP_ITEM = AdminRowMappers::mapOrderPrepItem;
    public static final RowMapper<MenuScheduleResponse> MENU_SCHEDULE = AdminRowMappers::mapMenuSchedule;
    public static final RowMapper<DispatchBoardItemResponse> DISPATCH_BOARD = AdminRowMappers::mapDispatchBoard;

    private static OrderPrepItemResponse mapOrderPrepItem(ResultSet rs, int rowNum) throws SQLException {
        return new OrderPrepItemResponse(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("meal_summary"),
            rs.getInt("quantity"),
            rs.getString("user_note"),
            rs.getString("admin_note"),
            rs.getString("special_tag"),
            rs.getString("delivery_address"),
            rs.getString("source"),
            rs.getBoolean("priority_customer"),
            rs.getBoolean("fixed_subscription"),
            rs.getString("status"),
            rs.getString("display_status"),
            rs.getString("display_status_label"),
            rs.getBoolean("can_assign"),
            rs.getBoolean("can_cancel"),
            rs.getBoolean("can_receipt"),
            rs.getString("wallet_status_label")
        );
    }

    private static MenuScheduleResponse mapMenuSchedule(ResultSet rs, int rowNum) throws SQLException {
        Date serveDate = rs.getDate("serve_date");
        return new MenuScheduleResponse(
            rs.getLong("id"),
            serveDate == null ? "" : serveDate.toLocalDate().toString(),
            rs.getString("meal_period"),
            rs.getString("meal_name"),
            rs.getString("meal_detail"),
            rs.getInt("calories"),
            rs.getString("merchant_note"),
            rs.getString("status")
        );
    }

    private static DispatchBoardItemResponse mapDispatchBoard(ResultSet rs, int rowNum) throws SQLException {
        return new DispatchBoardItemResponse(
            rs.getLong("dispatch_id"),
            rs.getLong("order_id"),
            rs.getString("customer_name"),
            rs.getString("delivery_address"),
            rs.getString("rider_name"),
            rs.getString("area_code"),
            rs.getString("delivery_status"),
            rs.getString("receipt_status"),
            rs.getString("receipt_label"),
            rs.getBoolean("can_notify_customer")
        );
    }

}
