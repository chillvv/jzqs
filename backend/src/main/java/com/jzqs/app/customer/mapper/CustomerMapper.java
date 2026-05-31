package com.jzqs.app.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jzqs.app.customer.model.entity.CustomerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerEntity> {
}
