/** 
 *@Project: okdeer-mall-api 
 *@Author: xuzq01
 *@Date: 2016年11月4日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */    
package com.okdeer.mall.risk.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.alibaba.dubbo.config.annotation.Service;
import com.github.pagehelper.PageHelper;
import com.okdeer.base.common.utils.PageUtils;
import com.okdeer.base.dal.IBaseMapper;
import com.okdeer.base.service.BaseServiceImpl;
import com.okdeer.mall.risk.dto.WhiteManagerDto;
import com.okdeer.mall.risk.entity.RiskWhite;
import com.okdeer.mall.risk.mapper.RiskWhiteMapper;
import com.okdeer.mall.risk.service.IWhiteListService;

/**
 * ClassName: ISkinManagerServiceApi 
 * @Description: TODO
 * @author xuzq01
 * @date 2016年11月4日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *
 */
@Service
public class WhiteListServiceImpl extends BaseServiceImpl implements IWhiteListService{
	
	private static final Logger LOGGER = Logger.getLogger(WhiteListServiceImpl.class);
	
	/**
	 * 获取皮肤mapper
	 */
	@Autowired
	RiskWhiteMapper riskWhiteMapper;
	/**
	 * (non-Javadoc)
	 * @see com.okdeer.mall.risk.service.IWhiteListService#findWhiteList(com.okdeer.mall.risk.dto.WhiteManagerDto, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public PageUtils<RiskWhite> findWhiteList(WhiteManagerDto whiteManagerDto, Integer pageNumber,
			Integer pageSize) {
		PageHelper.startPage(pageNumber, pageSize, true);
		List<RiskWhite> result = riskWhiteMapper.findWhiteList(whiteManagerDto);
		if (result == null) {
			result = new ArrayList<RiskWhite>();
		}
		return new PageUtils<RiskWhite>(result);
	}

	/**
	 * (non-Javadoc)
	 * @see com.okdeer.mall.risk.service.IWhiteListService#selectWhiteByAccount(com.okdeer.mall.risk.dto.WhiteManagerDto)
	 */
	@Override
	public int selectWhiteByAccount(String account) {
		return riskWhiteMapper.selectWhiteByAccount(account);
	}

	/**
	 * (non-Javadoc)
	 * @see com.okdeer.base.service.BaseServiceImpl#getBaseMapper()
	 */
	@Override
	public IBaseMapper getBaseMapper() {
		return riskWhiteMapper;
	}

	/**
	 * (non-Javadoc)
	 * @see com.okdeer.mall.risk.service.IWhiteListService#deleteBatchByIds(java.util.List, java.lang.String, java.util.Date)
	 */
	@Override
	public void deleteBatchByIds(List<String> ids, String updateUserId, Date updateTime) {
		riskWhiteMapper.deleteBatchByIds(ids,updateUserId,updateTime);
		
	}
	
}