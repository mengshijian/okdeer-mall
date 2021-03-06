/** 
 * @Copyright: Copyright ©2005-2020 yschome.com Inc. All rights reserved
 * @Project: yschome-mall 
 * @File: SysBuyerExtService.java 
 * @Date: 2015年11月26日 
 * 注意：本内容仅限于友门鹿公司内部传阅，禁止外泄以及用于其他的商业目的 
 */

package com.okdeer.mall.member.service;

import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.mall.member.member.entity.SysBuyerExt;


/**
 * 用户扩展表信息查询service
 * @pr yschome-mall
 * @author zhongyong
 * @date 2015年11月20日 下午5:13:29
 */
public interface SysBuyerExtService {

	/**
	 * 根据userId查询会员扩展表
	 * @param userId 请求参数
	 * @return 返回会员扩展实体
	 * @throwsServiceException
	 */
	SysBuyerExt findByUserId(String userId) throws ServiceException;
	
	
	/**
	 * 更改用户扩张表信息
	 *@author luosm
	 * @param sysBuyerExt 请求参数
	 */
	void updateByUserId(SysBuyerExt sysBuyerExt) throws ServiceException;
	
	/**
	 * DESC: 添加
	 * @author LIU.W
	 * @param sysBuyerExt
	 * @return
	 */
	int insertSelective(SysBuyerExt sysBuyerExt) throws ServiceException;
	
	
	/**
	 * @Description: 重置已经抽奖机会为0的用户，将抽奖机会重置为1次
	 * @throws
	 * @author tuzhd
	 * @date 2016年11月22日
	 */
	public void updateUserPrizeCount();
	
	/**
	 * @Description: 根据用户id 抽奖之后将其抽奖机会-1
	 * @throws
	 * @author tuzhd
	 * @date 2016年11月22日
	 */
	int updateCutPrizeCount(String userId);
	
	/**
	 * @Description: 给用户添加抽奖次数 count
	 * @param userId 用户id
	 * @param count  抽奖次数
	 * @author tuzhd
	 * @date 2016年12月13日
	 */
	public void updateAddPrizeCount(String userId,int count);
}
