package com.okdeer.mall.order.bo;  

/**
 * ClassName: GroupOrderRemarkConst 
 * @Description: 团购订单拼团失败备注常量
 * @author maojj
 * @date 2017年10月18日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *		友门鹿2.6.3 		2017年10月18日				maojj
 */
public class GroupOrderRemarkConst {

	public static final String GROUP_CLOSE = "团购活动关闭";
	
	public static final String GROUP_EXPIRE = "团购拼团过期";
	
	public static final String GROUP_FAIL = "团购拼团失败";
	
	public static final String GROUP_FAIL_OUT_DAY_LIMIT = "超出团购商品每日限购，拼团失败";
	
	public static final String GROUP_FAIL_OUT_TOTAL_LIMIT = "超出团购商品总限购，拼团失败";
	
	public static final String GROUP_FAIL_STOCK_NOT_ENOUGH= "团购商品库存不足，拼团失败";
}
