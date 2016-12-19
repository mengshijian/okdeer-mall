/** 
 *@Project: okdeer-mall-activity 
 *@Author: xuzq01
 *@Date: 2016年12月8日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */    
package com.okdeer.mall.activity.prize.api.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.alibaba.dubbo.config.annotation.Service;
import com.okdeer.mall.activity.prize.entity.ActivityPrizeRecord;
import com.okdeer.mall.activity.prize.service.ActivityPrizeRecordApi;
import com.okdeer.mall.activity.prize.service.ActivityPrizeRecordService;

/**
 * ClassName: ActivityPrizeRecordApiImpl 
 * @Description: 中奖记录表Service实现类
 * @author xuzq01
 * @date 2016年12月8日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *		v1.2.3			2016年12月8日		xuzq01				中奖记录表Service实现类
 */
@Service(version="1.0.0")
public class ActivityPrizeRecordApiImpl implements ActivityPrizeRecordApi{
	
	/**
	 * 中奖记录表Service
	 */
	@Autowired
	ActivityPrizeRecordService activityPrizeRecordService;
	
	/**
	 * @Description: TODO
	 * @param userId
	 * @param activityId 活动id 广告活动id 以后会是对应
	 * @return   
	 * @return List<ActivityPrizeRecord>  
	 * @throws
	 * @author tuzhd
	 * @date 2016年12月15日
	 */
	@Override
	public List<ActivityPrizeRecord> findByUserId(String userId,String activityId) {
		return activityPrizeRecordService.findByUserId(userId,activityId);
	}

	@Override
	public List<ActivityPrizeRecord> findPrizeRecord() {
		return activityPrizeRecordService.findPrizeRecord();
	}

	@Override
	public int findCountByPrizeId(String prizeId) {
		return activityPrizeRecordService.findCountByPrizeId(prizeId);
	}


}
