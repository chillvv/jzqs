package com.jzqs.app.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单备注（MealSlotOrder）的展示拼接规则。
 *
 * <p>规则：用户侧与商家侧各自成栏，栏内把「客户档案长期备注 / 本单一次性备注 /
 * 订单列上的历史备注」全部列出，去重后用中文逗号拼接。
 *
 * <p>历史上三处读取口（后台订单中心 / 派单中心 / 骑手端）各自实现了「有备注快照就整列丢弃订单列值」的
 * 回退逻辑，导致一旦出现用户备注就把商家备注顶掉。这里收敛成唯一一份拼接实现，避免三端再次分化。
 */
public final class OrderNoteTexts {
    /** 备注条目之间的分隔符（中文逗号）。 */
    public static final String SEPARATOR = "，";

    private OrderNoteTexts() {
    }

    /** 把一条备注追加进列表：空值、空白、`-` 以及与已有条目重复的一律丢弃。 */
    public static void addPart(List<String> parts, String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty() || parts.contains(normalized)) {
            return;
        }
        parts.add(normalized);
    }

    /** 创建列表并把快照条目按顺序灌入。 */
    public static List<String> newParts(List<String> notes) {
        List<String> parts = new ArrayList<>();
        if (notes != null) {
            for (String note : notes) {
                addPart(parts, note);
            }
        }
        return parts;
    }

    /** 归一化：null → 空串，去掉首尾空白，`-` 视为无备注。 */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return "-".equals(trimmed) ? "" : trimmed;
    }

    public static String join(List<String> parts) {
        return String.join(SEPARATOR, parts);
    }
}
