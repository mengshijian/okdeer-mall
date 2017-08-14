/** 
 *@Project: okdeer-mall-order 
 *@Author: guocp
 *@Date: 2017年8月10日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */    
package com.okdeer.mall.order.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.pagehelper.PageHelper;
import com.okdeer.base.common.utils.PageUtils;
import com.okdeer.base.dal.IBaseMapper;
import com.okdeer.base.service.BaseServiceImpl;
import com.okdeer.mall.order.bo.TradePinMoneyUseBo;
import com.okdeer.mall.order.dto.TradePinMoneyQueryDto;
import com.okdeer.mall.order.entity.TradePinMoneyUse;
import com.okdeer.mall.order.mapper.TradePinMoneyUseMapper;
import com.okdeer.mall.order.service.TradePinMoneyUseService;

/**
 * ClassName: TradePinMoneyObtainServiceImpl 
 * @Description: 零花钱使用服务实现
 * @author guocp
 * @date 2017年8月10日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *
 */
@Service
public class TradePinMoneyUseServiceImpl extends BaseServiceImpl implements TradePinMoneyUseService {

	@Autowired
	private TradePinMoneyUseMapper tradePinMoneyUseMapper;
	
	@Override
	public IBaseMapper getBaseMapper() {
		return tradePinMoneyUseMapper;
	}

	@Override
	public PageUtils<TradePinMoneyUse> findPage(String userId, int pageNumber, int pageSize) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public PageUtils<TradePinMoneyUseBo> findUsePageList(TradePinMoneyQueryDto paramDto, int pageNumber, int pageSize) {
		PageHelper.startPage(pageNumber, pageSize, true, false);
		List<TradePinMoneyUseBo> list = tradePinMoneyUseMapper.fingUsePageList(paramDto);
		return new PageUtils<TradePinMoneyUseBo>(list);
	}

	@Override
	public Integer findUseListCount(TradePinMoneyQueryDto paramDto) {
		return tradePinMoneyUseMapper.findUseListCount(paramDto);
	}

}