/** 
 *@Project: okdeer-mall-api 
 *@Author: xuzq01
 *@Date: 2017年4月12日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */    
package com.okdeer.mall.activity.advert.service;

import com.okdeer.base.service.IBaseService;
import com.okdeer.mall.activity.advert.entity.ActivityAdvertSale;

/**
 * ClassName: ActivityAdvertSaleService 
 * @Description: 销售活动及H5活动关联接口类
 * @author tuzhd
 * @date 2017年4月13日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 * 		V2.2.0			2017-4-13			tuzhd			 销售活动及H5活动关联接口类
 */

public interface ActivityAdvertSaleService extends IBaseService {

	/**
	 * @Description: 根据活动id及模板编号查询关联的销售类型 
	 * @return ActivityAdvertSale  
	 * @author tuzhd
	 * @date 2017年4月13日
	 */
    ActivityAdvertSale findSaleByIdNo(int modelNo,String activityAdvertId);
    
    /**
	 * @Description: 新增销售类型 
	 * @param ActivityAdvertSale 店铺销售活动
	 * @author tuzhd
	 * @date 2017年4月13日
	 */
	int addSale(ActivityAdvertSale sale);
	
	/**
	 * @Description: 删除关联抽店铺促销信息by活动id
	 * @param activityAdvertId 活动id
	 * @return int  
	 * @throws
	 * @author tuzhd
	 * @date 2017年4月19日
	 */
	int deleteByActivityAdvertId(String activityAdvertId);
}
