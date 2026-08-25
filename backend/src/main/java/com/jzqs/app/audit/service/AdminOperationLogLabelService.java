package com.jzqs.app.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 操作日志中文标签服务：把技术化的 module/action/请求路径/参数 转换为普通人可读的中文描述。
 * 表格直接展示「谁 + 在哪 + 干了什么 + 对谁」，技术细节（原始路径/JSON）保留在展开详情中。
 */
@Service
public class AdminOperationLogLabelService {
    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogLabelService.class);

    private static final Map<String, String> MODULE_LABELS = Map.ofEntries(
        Map.entry("CUSTOMER_ASSET", "客户管理"),
        Map.entry("CUSTOMER", "客户管理"),
        Map.entry("CUSTOMERS", "客户管理"),
        Map.entry("ORDER", "订单管理"),
        Map.entry("ORDERS", "订单管理"),
        Map.entry("AFTERSALE", "售后处理"),
        Map.entry("AFTERSALES", "售后处理"),
        Map.entry("MENU_WEEK", "每周菜单"),
        Map.entry("MENU-WEEKS", "每周菜单"),
        Map.entry("MENU_SCHEDULE", "排菜计划"),
        Map.entry("MENU-SCHEDULES", "排菜计划"),
        Map.entry("PACKAGE_PLAN", "套餐发放"),
        Map.entry("PACKAGE-GRANTS", "套餐发放"),
        Map.entry("DISPATCH", "骑手调度"),
        Map.entry("DELIVERIES", "配送管理"),
        Map.entry("RIDERS", "骑手管理"),
        Map.entry("SETTINGS", "系统设置"),
        Map.entry("ADMIN_USER", "后台账号"),
        Map.entry("USERS", "后台账号"),
        Map.entry("MAINTENANCE", "系统维护"),
        Map.entry("SUBSCRIPTION_RULE", "订阅规则"),
        Map.entry("SUBSCRIPTION-RULES", "订阅规则"),
        Map.entry("AUTH", "登录"),
        Map.entry("ANALYSIS", "经营分析"),
        Map.entry("OPERATION-LOGS", "操作日志"),
        Map.entry("DASHBOARD", "数据看板")
    );

    /** key 为 module:action 或 :action（后者表示任意模块通用） */
    private static final Map<String, String> ACTION_LABELS = buildActionLabels();

    private static Map<String, String> buildActionLabels() {
        Map<String, String> map = new HashMap<>();
        // 客户管理（余额是重点审计对象）
        map.put("CUSTOMER_ASSET:WALLET_GRANT", "赠送餐次（加余额）");
        map.put("CUSTOMER_ASSET:WALLET_DEDUCT", "扣减餐次（减余额）");
        map.put("CUSTOMER_ASSET:BATCH_EXTEND_VALIDITY", "批量延长有效期");
        map.put("CUSTOMER_ASSET:CREATE_PROFILE", "新建客户档案");
        map.put("CUSTOMER_ASSET:UPDATE_PROFILE", "修改客户资料");
        map.put("CUSTOMER_ASSET:SYNC_MAIN_SHEET", "同步客户总表");
        map.put("CUSTOMER_ASSET:POST_ADDRESSES", "新增/修改客户地址");
        map.put("CUSTOMER_ASSET:DELETE_ADDRESSES", "删除客户地址");
        map.put("CUSTOMERS:DELETE_CUSTOMERS", "删除客户");
        map.put("CUSTOMERS:POST_CUSTOMERS", "新建客户");
        // 订单管理
        map.put("ORDER:MANUAL_CREATE", "手工创建订单");
        map.put("ORDER:CANCEL", "取消订单");
        map.put("ORDER:CANCEL_SUBSCRIPTION", "取消订餐");
        map.put("ORDER:CONFIRM_SUBSCRIPTION", "确认订餐");
        map.put("ORDER:BULK_IMPORT_SUBSCRIPTION", "批量导入订餐");
        map.put("ORDER:ADMIN_CHANGE_ADDRESS", "修改订单地址");
        map.put("ORDER:UPDATE_MERCHANT_REMARK", "修改订单商家备注");
        map.put("ORDER:UPDATE_PROFILE", "修改订单客户信息");
        map.put("ORDER:SET_SPECIAL_DISPATCH", "设置特殊调度");
        map.put("ORDER:RESET_SPECIAL_DISPATCH", "取消特殊调度");
        map.put("ORDER:ADD_NOTE", "添加订单备注");
        map.put("ORDER:DELETE", "删除订单");
        map.put("ORDER:DELETE_RECEIPT", "删除配送回单");
        map.put("ORDER:CONSUME", "核销订单");
        map.put("ORDER:DELIVERY_RELEASE", "释放配送");
        // 售后处理
        map.put("AFTERSALE:CREATE", "新建售后单");
        map.put("AFTERSALE:DIRECT_REFUND", "直接退款");
        map.put("AFTERSALE:RESOLVE", "处理售后");
        // 菜单
        map.put("MENU_WEEK:CREATE_TEMPLATE", "创建菜单模板");
        map.put("MENU_WEEK:SAVE_DAY", "保存每日菜单");
        map.put("MENU_WEEK:PUBLISH", "发布菜单");
        map.put("MENU_WEEK:COPY_LAST_WEEK", "复制上周菜单");
        map.put("MENU_SCHEDULE:CREATE", "新增排菜计划");
        map.put("MENU_SCHEDULE:UPDATE", "修改排菜计划");
        map.put("MENU_SCHEDULE:DISABLE", "停用排菜计划");
        // 套餐
        map.put("PACKAGE_PLAN:GRANT", "发放套餐");
        // 骑手调度
        map.put("DISPATCH:BATCH_ASSIGN", "批量分配订单");
        map.put("DISPATCH:CREATE_RIDER", "新增骑手");
        map.put("DISPATCH:UPDATE_RIDER_PROFILE", "修改骑手资料");
        map.put("DISPATCH:TAKEOVER_RIDER_AUTH", "接管骑手账号");
        map.put("DISPATCH:UNBIND_RIDER_AUTH", "解绑骑手账号");
        map.put("DISPATCH:UPDATE_AREA_BINDING", "修改区域绑定");
        map.put("DISPATCH:ASSIGN_RIDER_AREA", "分配骑手负责区域");
        map.put("DISPATCH:ASSIGN_RIDER_ORDER", "指派订单给骑手");
        map.put("DISPATCH:REORDER_AREA", "调整区域排序");
        map.put("DISPATCH:AREA_AI_CORRECTION_PREVIEW", "预览区域AI纠偏");
        map.put("DISPATCH:AREA_AI_CORRECTION_CONFIRM", "确认区域AI纠偏");
        map.put("DISPATCH:MOVE_ORDER_AREA", "移动订单到其他区域");
        map.put("DISPATCH:REASSIGN", "改派订单");
        map.put("DISPATCH:CONFIRM_EXCEPTION_AREA", "确认异常订单区域");
        map.put("DISPATCH:ACTIVATE_RIDER", "启用骑手");
        map.put("DISPATCH:DISABLE_RIDER", "停用骑手");
        map.put("DISPATCH:GENERATE_ROUTE_SUGGESTION", "生成配送路线建议");
        map.put("DISPATCH:ROUTE_LAB_SIMULATE", "路线模拟测试");
        map.put("DISPATCH:DELETE_JOB_LOGS", "删除调度任务日志");
        map.put("DISPATCH:ROUTE_FEEDBACK", "提交路线反馈");
        map.put("DISPATCH:POST_AUTO-ASSIGN", "自动分配订单");
        // 系统设置
        map.put("SETTINGS:DISPATCH_ROUTE_WORKBENCH", "保存调度工作台设置");
        map.put("SETTINGS:DISPATCH_AI_SETTINGS", "保存调度AI设置");
        map.put("SETTINGS:DISPATCH_AI_BALANCE_REFRESH", "刷新调度AI余额");
        map.put("SETTINGS:DISPATCH_AREA_MEMORY_UPDATE", "更新区域配送记忆");
        map.put("SETTINGS:DISPATCH_AREA_MEMORY_DELETE", "删除区域配送记忆");
        map.put("SETTINGS:DISPATCH_AI_RUN_NOW", "立即运行AI调度");
        map.put("SETTINGS:ORDERING_TOGGLE", "切换用户端接单开关");
        map.put("SETTINGS:HOLIDAY_NOTICE", "保存放假通知");
        map.put("SETTINGS:REST_NOTICE_TEMPLATE", "保存休息通知模板");
        map.put("SETTINGS:BANNER_IMAGES", "保存首页轮播图");
        map.put("SETTINGS:PAUSE_WITH_NOTICE", "暂停营业并通知用户");
        map.put("SETTINGS:POPUP_ANNOUNCEMENT", "保存弹窗公告");
        map.put("SETTINGS:PACKAGE_REMINDERS", "保存套餐到期提醒设置");
        // 后台账号
        map.put("ADMIN_USER:CREATE", "创建后台账号");
        map.put("ADMIN_USER:UPDATE", "修改后台账号");
        map.put("ADMIN_USER:RESET_PASSWORD", "重置后台账号密码");
        map.put("USERS:PUT_USERS", "修改后台账号");
        map.put("USERS:POST_RESET-PASSWORD", "重置后台账号密码");
        map.put("USERS:DELETE_USERS", "删除后台账号");
        // 系统维护
        map.put("MAINTENANCE:TRIGGER_CLEANUP", "手动触发数据清理");
        map.put("MAINTENANCE:TRIGGER_MODULE_CLEANUP", "手动触发模块数据清理");
        map.put("MAINTENANCE:UPDATE_SETTINGS", "修改维护设置");
        map.put("MAINTENANCE:POST_CLEANUP", "手动触发数据清理");
        map.put("MAINTENANCE:POST_MARK-CLOUD-DELETED", "标记回单云存储已删除");
        map.put("MAINTENANCE:POST_CLOUD-JOB-LOGS", "查询云清理日志");
        // 订阅规则
        map.put("SUBSCRIPTION_RULE:CREATE", "新增订阅规则");
        map.put("SUBSCRIPTION_RULE:UPDATE", "修改订阅规则");
        map.put("SUBSCRIPTION_RULE:DELETE", "删除订阅规则");
        map.put("SUBSCRIPTION_RULE:TOGGLE", "切换订阅规则状态");
        // 登录
        map.put("AUTH:POST_LOGIN", "登录后台");
        map.put("AUTH:POST_CHANGE-PASSWORD", "修改登录密码");
        map.put("AUTH:POST_LOGOUT", "退出登录");
        map.put("AUTH:POST_REGISTER-PHONE", "注册手机号");
        map.put("AUTH:POST_BIND-PHONE", "绑定手机号");
        map.put("AUTH:POST_PHONE-LOGIN", "手机号登录");
        // 经营分析
        map.put("ANALYSIS:POST_COST-ENTRIES", "新增成本记录");
        return Map.copyOf(map);
    }

    /** 请求路径中的对象 ID 解析（用于把 /customers/917 显示成 客户「胡胡」） */
    private static final Pattern CUSTOMER_PATH = Pattern.compile("/customers/(\\d+)");
    private static final Pattern RIDER_PATH = Pattern.compile("/riders/(\\d+)");
    private static final Pattern AFTERSALE_PATH = Pattern.compile("/aftersales/(\\d+)");
    private static final Pattern ORDER_PATH = Pattern.compile("/orders/(\\d+)");
    private static final Pattern ADMIN_USER_PATH = Pattern.compile("/api/admin/users/(\\d+)");
    private static final Pattern AREA_PATH = Pattern.compile("/areas/([^/\\d][^/]*)");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminOperationLogLabelService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String moduleLabel(String module) {
        if (module == null || module.isBlank()) {
            return "其他";
        }
        return MODULE_LABELS.getOrDefault(module, module);
    }

    public String actionLabel(String module, String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        String byModule = module == null ? null : ACTION_LABELS.get(module + ":" + action);
        if (byModule != null) {
            return byModule;
        }
        String generic = ACTION_LABELS.get(":" + action);
        if (generic != null) {
            return generic;
        }
        return fallbackActionLabel(action);
    }

    /** 未收录动作的兜底：按 HTTP 动词前缀给出中文动词，关键词保持原文 */
    private String fallbackActionLabel(String action) {
        String prefix;
        String rest;
        if (action.startsWith("POST_")) {
            prefix = "操作";
            rest = action.substring(5);
        } else if (action.startsWith("PUT_")) {
            prefix = "修改";
            rest = action.substring(4);
        } else if (action.startsWith("DELETE_")) {
            prefix = "删除";
            rest = action.substring(7);
        } else if (action.startsWith("PATCH_")) {
            prefix = "修改";
            rest = action.substring(6);
        } else {
            return action;
        }
        if (rest.isBlank()) {
            return prefix;
        }
        return prefix + "(" + rest.toLowerCase().replace('-', '_') + ")";
    }

    /**
     * 解析「对谁操作」：优先解析请求路径/请求体中的客户、订单、骑手、后台账号，
     * 统一翻译成客户姓名等普通人能看懂的描述；同一批日志共享名称缓存，避免逐条查库。
     */
    public String targetLabel(String requestPath, String requestSummary, NameCache cache) {
        if (requestPath == null || requestPath.isBlank()) {
            return "";
        }
        String pathOnly = requestPath.split("\\?", 2)[0];

        // 批量延长有效期：作用于所有未过期客户，路径和请求体中没有客户ID
        if (pathOnly.endsWith("/wallet/batch-extend")) {
            return "全部未过期客户";
        }

        Matcher customer = CUSTOMER_PATH.matcher(pathOnly);
        if (customer.find()) {
            return customerLabel(Long.parseLong(customer.group(1)), requestSummary, cache);
        }
        // 订单类操作：定位到订单所属的客户（优先用删除前快照，订单删除后仍可追溯）
        Matcher order = ORDER_PATH.matcher(pathOnly);
        if (order.find()) {
            String snapName = snapshotField(requestSummary, "customer_name");
            if (snapName != null) {
                return "客户「" + snapName + "」的订单";
            }
            Long customerId = cache.orderCustomerIds().get(Long.parseLong(order.group(1)));
            return customerId != null ? customerLabel(customerId, requestSummary, cache) : "已删除的订单";
        }
        // 售后单处理：定位到售后单所属的客户（优先用删除前快照）
        Matcher aftersale = AFTERSALE_PATH.matcher(pathOnly);
        if (aftersale.find()) {
            String snapName = snapshotField(requestSummary, "customer_name");
            if (snapName != null) {
                return "客户「" + snapName + "」的售后单";
            }
            Long customerId = cache.aftersaleCustomerIds().get(Long.parseLong(aftersale.group(1)));
            return customerId != null ? customerLabel(customerId, requestSummary, cache) : "已删除的售后单";
        }
        Matcher adminUser = ADMIN_USER_PATH.matcher(pathOnly);
        if (adminUser.find()) {
            return adminUserLabel(Long.parseLong(adminUser.group(1)), requestSummary, cache);
        }
        Matcher rider = RIDER_PATH.matcher(pathOnly);
        if (rider.find()) {
            return riderLabel(Long.parseLong(rider.group(1)), requestSummary, cache);
        }
        Matcher area = AREA_PATH.matcher(pathOnly);
        if (area.find()) {
            return "区域「" + area.group(1) + "」";
        }

        return targetLabelFromSummary(pathOnly, requestSummary, cache);
    }

    /** 请求体里携带操作对象时（如手工建单/发放套餐的 customerId），从参数中解析 */
    private String targetLabelFromSummary(String pathOnly, String requestSummary, NameCache cache) {
        JsonNode node = parseSummary(requestSummary);
        if (node == null) {
            return "";
        }
        // 新建客户档案：POST /api/admin/customers，客户名字在请求体里
        if ("/api/admin/customers".equals(pathOnly) && node.hasNonNull("name")) {
            return "新客户「" + node.get("name").asText().trim() + "」";
        }
        // 创建后台账号：POST /api/admin/users，账号名字在请求体里
        if ("/api/admin/users".equals(pathOnly) && node.hasNonNull("displayName")) {
            return "账号「" + node.get("displayName").asText().trim() + "」";
        }
        Set<Long> customerIds = new LinkedHashSet<>();
        collectId(node.get("customerId"), customerIds);
        collectIds(node.get("customerIds"), customerIds);
        if (!customerIds.isEmpty()) {
            return customersLabel(customerIds, cache);
        }
        // 批量核销等场景：请求体里是订单ID，换成订单所属客户
        Set<Long> orderIds = new LinkedHashSet<>();
        collectId(node.get("orderId"), orderIds);
        collectIds(node.get("orderIds"), orderIds);
        if (!orderIds.isEmpty()) {
            Set<Long> orderCustomerIds = new LinkedHashSet<>();
            for (Long orderId : orderIds) {
                Long customerId = cache.orderCustomerIds().get(orderId);
                if (customerId != null) {
                    orderCustomerIds.add(customerId);
                }
            }
            if (!orderCustomerIds.isEmpty()) {
                return customersLabel(orderCustomerIds, cache);
            }
            return orderIds.size() > 1 ? "多个已删除的订单" : "已删除的订单";
        }
        return "";
    }

    /**
     * 单个客户：客户「张三」；客户已被删除时提示已删除而不是ID。
     * 优先使用删除前快照中的姓名（删除后记录已消失，快照保证仍能追溯）；
     * 其次若客户已被删除后按手机号重新建档（新ID），则通过请求体中的手机号反查出当前客户姓名。
     */
    private String customerLabel(long customerId, String requestSummary, NameCache cache) {
        String snapName = snapshotField(requestSummary, "name");
        if (snapName != null) {
            return "客户「" + snapName + "」";
        }
        String name = cache.customerNames().get(customerId);
        if (name != null && !name.isBlank()) {
            return "客户「" + name + "」";
        }
        String phone = extractPhone(requestSummary);
        if (phone != null) {
            String nameByPhone = cache.customerNamesByPhone().get(phone);
            if (nameByPhone != null && !nameByPhone.isBlank()) {
                return "客户「" + nameByPhone + "」";
            }
        }
        return "已删除的客户";
    }

    /** 单个骑手：骑手「张三」；优先用删除前快照，其次按手机号反查当前骑手姓名 */
    private String riderLabel(long riderId, String requestSummary, NameCache cache) {
        String snapName = snapshotField(requestSummary, "rider_name");
        if (snapName != null) {
            return "骑手「" + snapName + "」";
        }
        String name = cache.riderNames().get(riderId);
        if (name != null && !name.isBlank()) {
            return "骑手「" + name + "」";
        }
        String phone = extractPhone(requestSummary);
        if (phone != null) {
            String nameByPhone = cache.riderNamesByPhone().get(phone);
            if (nameByPhone != null && !nameByPhone.isBlank()) {
                return "骑手「" + nameByPhone + "」";
            }
        }
        return "已删除的骑手";
    }

    /** 单个后台账号：账号「张三」；优先用删除前快照，其次按手机号反查当前账号姓名 */
    private String adminUserLabel(long userId, String requestSummary, NameCache cache) {
        String snapName = snapshotField(requestSummary, "display_name");
        if (snapName != null) {
            return "账号「" + snapName + "」";
        }
        String name = cache.adminUserNames().get(userId);
        if (name != null && !name.isBlank()) {
            return "账号「" + name + "」";
        }
        String phone = extractPhone(requestSummary);
        if (phone != null) {
            String nameByPhone = cache.adminUserNamesByPhone().get(phone);
            if (nameByPhone != null && !nameByPhone.isBlank()) {
                return "账号「" + nameByPhone + "」";
            }
        }
        return "已删除的账号";
    }

    /** 从请求体摘要中读取删除前快照字段（_target 下的字段），取不到返回 null */
    private String snapshotField(String requestSummary, String field) {
        JsonNode node = parseSummary(requestSummary);
        if (node == null || !node.has("_target")) {
            return null;
        }
        JsonNode target = node.get("_target");
        if (target == null || !target.hasNonNull(field)) {
            return null;
        }
        String value = target.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 从请求体摘要中提取手机号（删除后重建的对象靠手机号反查当前姓名） */
    private String extractPhone(String requestSummary) {
        JsonNode node = parseSummary(requestSummary);
        if (node == null || !node.hasNonNull("phone") || !node.get("phone").isValueNode()) {
            return null;
        }
        String phone = node.get("phone").asText();
        return phone == null ? null : phone.trim();
    }

    /** 多个客户：少数几个直接列出姓名，多则「张三 等N位客户」 */
    private String customersLabel(Collection<Long> customerIds, NameCache cache) {
        List<String> names = new ArrayList<>();
        for (Long customerId : customerIds) {
            String name = cache.customerNames().get(customerId);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return customerIds.size() > 1 ? "多位已删除的客户" : "已删除的客户";
        }
        if (names.size() == 1) {
            return "客户「" + names.get(0) + "」";
        }
        if (names.size() <= 3) {
            return "客户「" + String.join("、", names) + "」";
        }
        return names.get(0) + " 等" + names.size() + "位客户";
    }

    private JsonNode parseSummary(String requestSummary) {
        if (requestSummary == null || !requestSummary.trim().startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readTree(requestSummary);
        } catch (Exception ex) {
            return null;
        }
    }

    private void collectId(JsonNode node, Set<Long> ids) {
        if (node != null && node.canConvertToLong()) {
            ids.add(node.asLong());
        }
    }

    private void collectIds(JsonNode node, Set<Long> ids) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            collectId(item, ids);
        }
    }

    /** 从请求参数 JSON 中提取普通人关心的重点（餐次数量、备注等） */
    public String detailLabel(String action, String requestSummary) {
        JsonNode node = parseSummary(requestSummary);
        if (node == null) {
            return "";
        }
        try {
            StringBuilder sb = new StringBuilder();
            if (node.hasNonNull("mealDelta")) {
                int delta = node.get("mealDelta").asInt();
                if ("WALLET_GRANT".equals(action)) {
                    sb.append("加").append(delta).append("餐");
                } else if ("WALLET_DEDUCT".equals(action)) {
                    sb.append("扣").append(delta).append("餐");
                } else {
                    sb.append(delta).append("餐");
                }
            }
            if (node.hasNonNull("totalMeals")) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append("套餐共").append(node.get("totalMeals").asInt()).append("餐");
            }
            if (node.hasNonNull("validityDays")) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append("有效期").append(node.get("validityDays").asInt()).append("天");
            }
            if (node.hasNonNull("extendDays")) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append("延长有效期").append(node.get("extendDays").asInt()).append("天");
            }
            if (node.hasNonNull("customerIds")) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(node.get("customerIds").size()).append("个客户");
            }
            if (node.hasNonNull("remark") && !node.get("remark").asText().isBlank()) {
                String remark = node.get("remark").asText().trim();
                if (remark.length() > 40) {
                    remark = remark.substring(0, 40) + "…";
                }
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append("备注：").append(remark);
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /** 一条日志的原始信息（路径 + 请求体摘要），供批量加载名称缓存 */
    public record LogRef(String requestPath, String requestSummary) {
    }

    /** 批量加载一批日志涉及的对象名称，避免逐条查库；同时按手机号反查，兼容对象删除后重建（新ID）的日志 */
    public NameCache loadNameCache(Iterable<LogRef> logs) {
        Set<Long> customerIds = new HashSet<>();
        Set<Long> riderIds = new HashSet<>();
        Set<Long> adminUserIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        Set<Long> aftersaleCaseIds = new HashSet<>();
        Set<String> phones = new HashSet<>();
        for (LogRef log : logs) {
            String path = log == null ? null : log.requestPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            String pathOnly = path.split("\\?", 2)[0];
            collectIds(CUSTOMER_PATH, pathOnly, customerIds);
            collectIds(RIDER_PATH, pathOnly, riderIds);
            collectIds(ADMIN_USER_PATH, pathOnly, adminUserIds);
            collectIds(ORDER_PATH, pathOnly, orderIds);
            collectIds(AFTERSALE_PATH, pathOnly, aftersaleCaseIds);
            JsonNode node = parseSummary(log.requestSummary());
            if (node != null) {
                collectId(node.get("customerId"), customerIds);
                collectIds(node.get("customerIds"), customerIds);
                collectId(node.get("orderId"), orderIds);
                collectIds(node.get("orderIds"), orderIds);
                if (node.hasNonNull("phone") && node.get("phone").isValueNode()) {
                    String phone = node.get("phone").asText();
                    if (phone != null && !phone.isBlank()) {
                        phones.add(phone.trim());
                    }
                }
            }
        }
        return new NameCache(
            queryNames("SELECT id, name FROM customers WHERE id IN ", customerIds),
            queryNames("SELECT id, rider_name FROM rider_profiles WHERE id IN ", riderIds),
            queryNames("SELECT id, display_name FROM users WHERE id IN ", adminUserIds),
            queryLongs("SELECT id, customer_id FROM daily_orders WHERE id IN ", orderIds),
            queryLongs("SELECT id, customer_id FROM aftersale_cases WHERE id IN ", aftersaleCaseIds),
            queryPhoneNames("SELECT phone, name FROM customers WHERE phone IN ", phones),
            queryPhoneNames("SELECT phone, rider_name FROM rider_profiles WHERE phone IN ", phones),
            queryPhoneNames("SELECT phone, display_name FROM users WHERE phone IN ", phones)
        );
    }

    private void collectIds(Pattern pattern, String path, Set<Long> ids) {
        Matcher matcher = pattern.matcher(path);
        if (matcher.find() && matcher.group(1).chars().allMatch(Character::isDigit)) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
    }

    private Map<Long, String> queryNames(String sqlPrefix, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] params = ids.toArray();
        Map<Long, String> names = new HashMap<>();
        jdbcTemplate.query(
            sqlPrefix + "(" + placeholders + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> names.put(rs.getLong(1), rs.getString(2)),
            params
        );
        return names;
    }

    private Map<Long, Long> queryLongs(String sqlPrefix, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] params = ids.toArray();
        Map<Long, Long> values = new HashMap<>();
        jdbcTemplate.query(
            sqlPrefix + "(" + placeholders + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> values.put(rs.getLong(1), rs.getLong(2)),
            params
        );
        return values;
    }

    /** 按手机号反查姓名（客户/骑手/后台账号删除后按手机号重建时，旧日志仍能定位到当前记录） */
    private Map<String, String> queryPhoneNames(String sqlPrefix, Set<String> phones) {
        if (phones.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(phones.size(), "?"));
        Object[] params = phones.toArray();
        Map<String, String> names = new HashMap<>();
        jdbcTemplate.query(
            sqlPrefix + "(" + placeholders + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> names.put(rs.getString(1), rs.getString(2)),
            params
        );
        return names;
    }

    /** 一批日志共享的名称缓存 */
    public record NameCache(
        Map<Long, String> customerNames,
        Map<Long, String> riderNames,
        Map<Long, String> adminUserNames,
        Map<Long, Long> orderCustomerIds,
        Map<Long, Long> aftersaleCustomerIds,
        Map<String, String> customerNamesByPhone,
        Map<String, String> riderNamesByPhone,
        Map<String, String> adminUserNamesByPhone
    ) {
    }
}
