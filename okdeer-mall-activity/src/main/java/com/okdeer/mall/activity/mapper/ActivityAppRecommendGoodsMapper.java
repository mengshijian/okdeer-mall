/*
 * Copyright(C) 2016-2021 okdeer.com Inc. All rights reserved
 * ActivityAppRecommendGoodsMapper.java
 * @Date 2016-12-30 Created
 * 注意：本内容仅限于友门鹿公司内部传阅，禁止外泄以及用于其他的商业目的
 */
package com.okdeer.mall.activity.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.okdeer.base.dal.IBaseMapper;
import com.okdeer.mall.activity.dto.AppRecommendGoodsParamDto;
import com.okdeer.mall.activity.entity.ActivityAppRecommendGoods;

public interface ActivityAppRecommendGoodsMapper extends IBaseMapper {

	/**
	 * @Description: 查询与APP端服务商品推荐的关联数据
	 * @param paramDto 查询参数
	 * @return List<ActivityAppRecommendGoods>  
	 * @throws Exception
	 * @author tangzj02
	 * @date 2016年12月30日
	 */
	List<ActivityAppRecommendGoods> findList(AppRecommendGoodsParamDto paramDto);

	/**
	 * @Description: 根据推荐ID查询A与APP端服务发票推荐的关联数据
	 * @param recommendId 服务商品推荐ID
	 * @return List<ActivityAppRecommendGoods>  
	 * @throws Exception
	 * @author tangzj02
	 * @date 2016年12月30日
	 */
	List<ActivityAppRecommendGoods> findListByRecommendId(String recommendId);

	/**
	 * @Description: 根据推荐ID删除数据
	 * @param id  服务商品推荐ID  
	 * @return int 成功删除数  
	 * @author tangzj02
	 * @date 2016年12月30日
	 */
	int deleteByRecommendId(String recommendId);

	/**
	 * @Description: 批量插入数据
	 * @param goodsList   
	 * @return int 成功插入记录数  
	 * @author tangzj02
	 * @date 2016年12月30日
	 */
	int insertMore(@Param("list") List<ActivityAppRecommendGoods> list);
}