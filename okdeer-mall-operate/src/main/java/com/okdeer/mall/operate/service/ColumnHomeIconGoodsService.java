/** 
 *@Project: okdeer-mall-operate 
 *@Author: tangzj02
 *@Date: 2016年12月28日 
 *@Copyright: ©2014-2020 www.okdeer.com Inc. All rights reserved. 
 */
package com.okdeer.mall.operate.service;

import java.util.List;

import com.okdeer.base.service.IBaseService;
import com.okdeer.mall.operate.entity.ColumnHomeIconGoods;

/**
 * ClassName: ColumnHomeIconGoodsService 
 * @Description: 首页ICON与商品关联服务
 * @author tangzj02
 * @date 2016年12月30日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 * 	        友门鹿2.0       2016-12-30        tangzj02                     添加
 */

public interface ColumnHomeIconGoodsService extends IBaseService {

	/**
	 * @Description: 根据首页ICONID 查询数据
	 * @return List<ColumnHomeIconGoods>  
	 * @throws Exception
	 * @author tangzj02
	 * @date 2016年12月28日
	 */
	List<ColumnHomeIconGoods> findListByHomeIconId(String homeIconId) throws Exception;

	/**
	 * @Description: 根据首页ICON ID集合 查询数据
	 * @return List<ColumnHomeIconGoods>  
	 * @throws Exception
	 * @author tangzj02
	 * @date 2017年01月05日
	 */
	List<ColumnHomeIconGoods> findListByHomeIconIds(List<String> homeIconIds) throws Exception;

	/**
	 * @Description: 根据首页ICONID删除数据
	 * @param iconId   
	 * @return int 删除记录  
	 * @author tangzj02
	 * @throws Exception
	 * @date 2016年12月28日
	 */
	int deleteByHomeIconId(String homeIconId) throws Exception;

	/**
	 * @Description: 批量插入数据
	 * @param list   
	 * @return int 出插入记录数
	 * @throws Exception
	 * @author tangzj02
	 * @date 2016年12月28日
	 */
	int insertMore(List<ColumnHomeIconGoods> list) throws Exception;

}
