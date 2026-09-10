package com.jzqs.app.customer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;

import com.jzqs.app.customer.api.CustomerAddressActionResponse;
import com.jzqs.app.customer.api.CustomerAddressUpsertRequest;
import com.jzqs.app.customer.api.CustomerDetailResponse;
import com.jzqs.app.customer.api.CustomerProfileCreateRequest;
import com.jzqs.app.customer.api.CustomerProfileCreateResponse;
import com.jzqs.app.customer.api.CustomerSubscriptionDetailResponse;
import com.jzqs.app.customer.mapper.CustomerMapper;
import com.jzqs.app.customer.mapper.MealWalletMapper;
import com.jzqs.app.customer.mapper.WalletTransactionMapper;
import com.jzqs.app.customer.model.entity.CustomerEntity;
import com.jzqs.app.customer.model.entity.MealWalletEntity;
import com.jzqs.app.customer.model.entity.WalletTransactionEntity;
import com.jzqs.app.dispatch.service.impl.DispatchAddressChangeNotifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CustomerAssetServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private MealWalletMapper mealWalletMapper;

    @Mock
    private WalletTransactionMapper walletTransactionMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DispatchAddressChangeNotifier dispatchAddressChangeNotifier;

    @InjectMocks
    private CustomerAssetServiceImpl customerAssetService;

    @Test
    void shouldExposeMerchantRemarkFieldInSubscriptionsWhenLoadingCustomerDetail() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(382L);
        customer.setName("竹子");
        customer.setPhone("13800000382");
        customer.setActive(true);

        MealWalletEntity wallet = new MealWalletEntity();
        wallet.setId(1L);
        wallet.setCustomerId(382L);
        wallet.setTotalMeals(10);
        wallet.setReservedMeals(2);
        wallet.setConsumedMeals(3);
        wallet.setActive(true);

        when(customerMapper.selectById(382L)).thenReturn(customer);
        when(mealWalletMapper.selectOne(any())).thenReturn(wallet);
        when(walletTransactionMapper.selectList(any())).thenReturn(List.of());
        when(jdbcTemplate.query(
            eq("SELECT id, contact_name, contact_phone, address_line, door_number, area_code, is_default, latitude, longitude FROM customer_addresses WHERE customer_id = ? AND active = TRUE ORDER BY is_default DESC, id ASC"),
            any(RowMapper.class),
            eq(382L)
        )).thenReturn(List.of());
        when(jdbcTemplate.query(
            eq("SELECT id, lunch_enabled, dinner_enabled, start_date, end_date, merchant_remark, is_priority_follow, paused FROM subscription_rules WHERE customer_id = ? ORDER BY id DESC"),
            any(RowMapper.class),
            eq(382L)
        )).thenReturn(List.of(
            new CustomerSubscriptionDetailResponse(8L, true, false, "2026-06-10", "2026-06-20", "重点关注", false, false)
        ));

        CustomerDetailResponse detail = customerAssetService.customerDetail(382L);

        assertThat(detail.subscriptions()).hasSize(1);
        assertThat(detail.subscriptions().get(0).merchantRemark()).isEqualTo("重点关注");
    }

    @Test
    void shouldExposeUnifiedCustomerNotesInDetailWithoutLegacyFields() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(382L);
        customer.setName("竹子");
        customer.setPhone("13800000382");
        customer.setActive(true);
        customer.setCreatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));

        MealWalletEntity wallet = new MealWalletEntity();
        wallet.setId(1L);
        wallet.setCustomerId(382L);
        wallet.setTotalMeals(10);
        wallet.setReservedMeals(2);
        wallet.setConsumedMeals(3);
        wallet.setActive(true);

        when(customerMapper.selectById(382L)).thenReturn(customer);
        when(mealWalletMapper.selectOne(any())).thenReturn(wallet);
        when(walletTransactionMapper.selectList(any())).thenReturn(List.of());
        lenient().when(jdbcTemplate.query(
            eq("SELECT id, contact_name, contact_phone, address_line, door_number, area_code, is_default, latitude, longitude FROM customer_addresses WHERE customer_id = ? AND active = TRUE ORDER BY is_default DESC, id ASC"),
            any(RowMapper.class),
            eq(382L)
        )).thenReturn(List.of());
        lenient().when(jdbcTemplate.query(
            eq("""
                SELECT id, note_type, scope_type, content, start_at, end_at, is_active
                FROM customer_notes
                WHERE customer_id = ? AND is_active = TRUE
                ORDER BY note_type, scope_type, display_order, id
                """),
            any(RowMapper.class),
            eq(382L)
        )).thenReturn(List.of(
            new com.jzqs.app.customer.api.CustomerNoteItemResponse(1L, "USER", "LONG_TERM", "少饭", null, null, true),
            new com.jzqs.app.customer.api.CustomerNoteItemResponse(2L, "MERCHANT", "LONG_TERM", "重点关注", null, null, true),
            new com.jzqs.app.customer.api.CustomerNoteItemResponse(3L, "MERCHANT", "TIME_BOXED", "周卡体验", null, null, true)
        ));

        CustomerDetailResponse detail = customerAssetService.customerDetail(382L);

        assertThat(detail.merchantRemark()).isNull();
        assertThat(detail.wallet()).isNotNull();
        assertThat(detail.addresses()).isEmpty();
        assertThat(detail.subscriptions()).isEmpty();
        assertThat(detail.transactions()).isEmpty();
    }

    @Test
    void shouldCreateCustomerAndGrantInitialMealsWhenRequested() {
        when(customerMapper.selectCount(any())).thenReturn(0L);
        when(customerMapper.insert(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity entity = invocation.getArgument(0);
            entity.setId(520L);
            return 1;
        });
        when(mealWalletMapper.selectOne(any())).thenReturn(null);
        when(mealWalletMapper.insert(any(MealWalletEntity.class))).thenAnswer(invocation -> {
            MealWalletEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(jdbcTemplate.queryForObject(
            "SELECT total_meals - consumed_meals FROM meal_wallets WHERE id = ?",
            Integer.class,
            900L
        )).thenReturn(5);
        // 建档授权初始餐次走 jdbcTemplate.update 原子自增，需 stub 返回成功
        when(jdbcTemplate.update(any(), any(Object[].class))).thenReturn(1);

        CustomerProfileCreateResponse result = customerAssetService.createCustomerProfile(
            new CustomerProfileCreateRequest(
                "新客户",
                "13600000066",
                null,
                null,
                "高新区测试地址 66 号",
                new BigDecimal("30.545420"),
                new BigDecimal("104.062500"),
                5,
                "首充赠送",
                30,
                null,
                null,
                null,
                null
            )
        );

        assertThat(result.customerId()).isEqualTo(520L);
        assertThat(result.status()).isEqualTo("CREATED");
        verify(jdbcTemplate).update(
            eq("""
            INSERT INTO customer_addresses (
                customer_id, contact_name, contact_phone, address_line, door_number, area_code, is_default, latitude, longitude
            ) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
            """),
            eq(520L),
            eq("新客户"),
            eq("13600000066"),
            eq("高新区测试地址 66 号"),
            eq((String) null),
            eq(""),
            eq(new BigDecimal("30.545420")),
            eq(new BigDecimal("104.062500"))
        );
        // 初始加餐已改为 jdbcTemplate 原子自增，不再调用 mealWalletMapper.updateById
        verify(walletTransactionMapper).insert(argThat((WalletTransactionEntity tx) ->
            tx.getWalletId().equals(900L)
                && "GRANT".equals(tx.getTransactionType())
                && tx.getMealDelta() == 5
                && "首充赠送".equals(tx.getRemark())
        ));
    }

    @Test
    void shouldCreateCustomerAddressAndClearOtherDefaultsWhenRequested() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(382L);
        customer.setName("竹子");
        customer.setPhone("13800000382");
        customer.setActive(true);
        when(customerMapper.selectById(382L)).thenReturn(customer);

        CustomerAddressActionResponse result = customerAssetService.createCustomerAddress(
            382L,
            new CustomerAddressUpsertRequest(
                "前台",
                "13800000382",
                "高新区科技园A座8层",
                null,
                "高新区",
                true,
                new BigDecimal("30.545420"),
                new BigDecimal("104.062500")
            )
        );

        assertThat(result.customerId()).isEqualTo(382L);
        assertThat(result.status()).isEqualTo("CREATED");
        verify(jdbcTemplate).update(eq("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?"), eq(382L));
    }

    @Test
    void shouldUpdateCustomerAddressWithoutClearingDefaultsWhenNotRequested() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(382L);
        customer.setName("竹子");
        customer.setPhone("13800000382");
        customer.setActive(true);
        when(customerMapper.selectById(382L)).thenReturn(customer);
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ? AND active = TRUE"),
            eq(Integer.class),
            eq(18L),
            eq(382L)
        )).thenReturn(1);

        CustomerAddressActionResponse result = customerAssetService.updateCustomerAddress(
            382L,
            18L,
            new CustomerAddressUpsertRequest(
                "后门",
                "13900000382",
                "天府三街B座",
                null,
                "高新区",
                false,
                new BigDecimal("30.545420"),
                new BigDecimal("104.062500")
            )
        );

        assertThat(result.customerId()).isEqualTo(382L);
        assertThat(result.addressId()).isEqualTo(18L);
        assertThat(result.status()).isEqualTo("UPDATED");
        verify(jdbcTemplate, never()).update(eq("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = ?"), eq(382L));
        verify(jdbcTemplate).update(
            eq("""
                UPDATE customer_addresses
                SET contact_name = ?, contact_phone = ?, address_line = ?, door_number = ?, area_code = ?, is_default = ?, latitude = ?, longitude = ?
                WHERE id = ? AND customer_id = ?
                """),
            eq("竹子"),
            eq("13800000382"),
            eq("天府三街B座"),
            eq((String) null),
            eq("高新区"),
            eq(false),
            eq(new BigDecimal("30.545420")),
            eq(new BigDecimal("104.062500")),
            eq(18L),
            eq(382L)
        );
        // 客户管理改址会静默改变订单配送目标，必须通知已承接的骑手
        verify(dispatchAddressChangeNotifier).notifyCustomerAddressChanged(
            eq(18L),
            isNull(),
            eq(DispatchAddressChangeNotifier.SOURCE_ADDRESS_BOOK_UPDATED)
        );
    }

    @Test
    void shouldRejectCustomerAddressWithoutCoordinates() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(382L);
        customer.setName("竹子");
        customer.setPhone("13800000382");
        customer.setActive(true);
        when(customerMapper.selectById(382L)).thenReturn(customer);

        // 新建不带坐标 → 拒绝（V35 地址重置后一律地图选点，杜绝未定位地址）
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerAssetService.createCustomerAddress(
            382L,
            new CustomerAddressUpsertRequest(
                "前台", "13800000382", "高新区科技园A座8层", null, "高新区", true, null, null
            )
        )).isInstanceOf(com.jzqs.app.common.error.BusinessException.class);

        // 只传纬度不传经度（不成对）→ 同样拒绝
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerAssetService.createCustomerAddress(
            382L,
            new CustomerAddressUpsertRequest(
                "前台", "13800000382", "高新区科技园A座8层", null, "高新区", true, new BigDecimal("30.545420"), null
            )
        )).isInstanceOf(com.jzqs.app.common.error.BusinessException.class);
    }

    @Test
    void shouldRejectCustomerProfileWithoutCoordinates() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerAssetService.createCustomerProfile(
            new CustomerProfileCreateRequest(
                "新客户", "13600000066", null, null, "高新区测试地址 66 号",
                null, null, 5, "首充赠送", 30, null, null, null, null
            )
        )).isInstanceOf(com.jzqs.app.common.error.BusinessException.class);
    }

    @Test
    void shouldAttachSubscriptionRuleToNewlyCreatedAddress() {
        // 地址重置会把 subscription_rules.default_address_id 置空；后台重新录入地址
        // 时必须自动回挂，否则固定订餐将一直没有地址（预览列为空、无法生成订单）。
        CustomerEntity customer = new CustomerEntity();
        customer.setId(382L);
        customer.setName("竹子");
        customer.setPhone("13800000382");
        customer.setActive(true);
        when(customerMapper.selectById(382L)).thenReturn(customer);

        customerAssetService.createCustomerAddress(
            382L,
            new CustomerAddressUpsertRequest(
                "前台", "13800000382", "高新区科技园A座8层", null, "高新区", true,
                new BigDecimal("30.545420"), new BigDecimal("104.062500")
            )
        );

        verify(jdbcTemplate).update(
            eq("UPDATE subscription_rules SET default_address_id = ? WHERE customer_id = ? AND default_address_id IS NULL"),
            any(), eq(382L)
        );
    }
}
