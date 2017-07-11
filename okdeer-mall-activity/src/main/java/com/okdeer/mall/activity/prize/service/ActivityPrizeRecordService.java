/** 
 *@Project: okdeer-mall-activity 
 *@Author: xuzq01
 *@Date: 2016年12月8日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */    
package com.okdeer.mall.activity.prize.service;

import java.util.List;
import java.util.Map;

import com.okdeer.base.common.utils.PageUtils;
import com.okdeer.base.service.IBaseService;
import com.okdeer.mall.activity.prize.entity.ActivityPrizeRecord;
import com.okdeer.mall.activity.prize.entity.ActivityPrizeRecordVo;

/**
 * ClassName: ActivityPrizeRecordApiImpl 
 * @Description: 中奖记录表
 * @author xuzq01
 * @date 2016年12月8日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *		v1.2.3			2016年12月8日		xuzq01				中奖记录表Service
 */

public interface ActivityPrizeRecordService extends IBaseService {

	public List<ActivityPrizeRecordVo> findByUserId(String userId,String activityId);
	
	/**
	 * @Description 根据传递查询前count条中奖记录
	 * @author tuzhd
	 * @param count
	 * @return
	 */
	List<ActivityPrizeRecordVo> findPrizeRecord(String activityAdvertId,Integer count);
	
	int findCountByPrizeId(String prizeId);
	
	public int addPrizeRecord(String collectId,String userId,String luckDrawId,String prizeId,int isOffer);

	/**
	 * @Description: TODO
	 * @param activityPrizeRecordVo
	 * @param pageNumber
	 * @param pageSize
	 * @return   
	 * @author xuzq01
	 * @date 2017年4月10日
	 */
	public PageUtils<ActivityPrizeRecordVo> findPrizeRecordList(ActivityPrizeRecordVo activityPrizeRecordVo,
			int pageNumber, int pageSize);
	
	/**
	 * @Description: 批量更新发放状态
	 * @param map   
	 * @return void  
	 * @author tuzhd
	 * @date 2017年6月20日
	 */
	public void updateBathOffer(Map<String,Object> map);
}
