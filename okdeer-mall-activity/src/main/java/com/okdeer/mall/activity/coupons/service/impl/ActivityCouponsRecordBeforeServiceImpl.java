package com.okdeer.mall.activity.coupons.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.okdeer.mall.activity.coupons.mapper.ActivityCouponsRecordBeforeMapper;
import com.okdeer.mall.activity.coupons.service.ActivityCouponsRecordBeforeService;
import com.okdeer.mall.activity.dto.ActivityCouponsRecordBeforeParamDto;

@Service
public class ActivityCouponsRecordBeforeServiceImpl 
		implements ActivityCouponsRecordBeforeService {

	@Autowired
	private ActivityCouponsRecordBeforeMapper activityCouponsRecordBeforeMapper;

	@Override
	public <T> int add(T arg0) throws Exception {
		return activityCouponsRecordBeforeMapper.insertSelective(arg0);
	}

	@Override
	public int delete(String id) throws Exception {
		return activityCouponsRecordBeforeMapper.deleteByPrimaryKey(id);
	}

	@Override
	public <T> T findById(String id) throws Exception {
		return activityCouponsRecordBeforeMapper.selectByPrimaryKey(id);
	}

	@Override
	public <T> int update(T arg0) throws Exception {
		return activityCouponsRecordBeforeMapper.updateByPrimaryKeySelective(arg0);
	}

	@Override
	public int selectCountByParam(ActivityCouponsRecordBeforeParamDto activityCouponsRecordBeforeParam) {
		return activityCouponsRecordBeforeMapper.getCountByDayParams(activityCouponsRecordBeforeParam);
	}

}
