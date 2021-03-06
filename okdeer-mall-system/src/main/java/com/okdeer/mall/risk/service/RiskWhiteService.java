/** 
 *@Project: okdeer-mall-system 
 *@Author: xuzq01
 *@Date: 2016年11月14日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */    
package com.okdeer.mall.risk.service;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.okdeer.base.common.utils.PageUtils;
import com.okdeer.base.service.IBaseService;
import com.okdeer.mall.risk.dto.RiskWhiteDto;
import com.okdeer.mall.risk.entity.RiskWhite;

/**
 * ClassName: RiskWhiteService 
 * @Description: 白名单管理service
 * @author xuzq01
 * @date 2016年11月14日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *		v1.2			2016年11月14日		xuzq01				白名单管理service
 */

public interface RiskWhiteService extends IBaseService{
	
	/**
	 * 
	 * @Description: 
	 * @param whiteManagerDto
	 * @param pageNumber
	 * @param pageSize
	 * @return   
	 * @author xuzq01
	 * @date 2016年11月16日
	 */
	public PageUtils<RiskWhite> findWhiteList(RiskWhiteDto whiteManagerDto, Integer pageNumber,
			Integer pageSize);

	/**
	 * @Description: 
	 * @param ids
	 * @param updateUserId
	 * @param updateTime   
	 * @author xuzq01
	 * @date 2016年11月16日
	 */
	public void deleteBatchByIds(List<String> ids, String updateUserId, Date updateTime);

	/**
	 * @Description: 
	 * @param riskWhiteList   
	 * @author xuzq01
	 * @date 2016年11月17日
	 */
	public void addBatch(List<RiskWhite> riskWhiteList);
	
	/**
	 * 
	 * @Description: 所有白名单
	 * @return   
	 * @author xuzq01
	 * @date 2016年11月21日
	 */
	public Set<String> findAllWhite();
	
}
