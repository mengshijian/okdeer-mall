package com.okdeer.mall.activity.coupons.service.impl;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.okdeer.base.common.enums.Disabled;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.mall.ApplicationTests;
import com.okdeer.mall.activity.coupons.entity.ActivitySale;
import com.okdeer.mall.activity.coupons.entity.ActivitySaleGoods;
import com.okdeer.mall.activity.coupons.enums.ActivitySaleStatus;
import com.okdeer.mall.activity.coupons.enums.ActivityTypeEnum;
import com.okdeer.mall.activity.coupons.service.ActivitySaleELServiceApi;

public class ActivitySaleServiceImplTest extends ApplicationTests {
	
	@Autowired
	private ActivitySaleELServiceApi service;
	
	@Test
	public void testSave() {
		
		ActivitySale activitySale = new ActivitySale();
		String saleId = UuidUtils.getUuid();
		activitySale.setId(saleId);
		activitySale.setName("低价抢购-未同步库存");
		activitySale.setStatus(ActivitySaleStatus.ing.getValue());
		activitySale.setLimit(2);
		activitySale.setStoreId("56583c03276511e6aaff00163e010eb1");
		activitySale.setStartTime(new Date());
		activitySale.setEndTime(activitySale.getStartTime());
		activitySale.setCreateTime(new Date());
		activitySale.setCreateUserId("15dbcb06276411e6aaff00163e010eb1");
		activitySale.setUpdateUserId("15dbcb06276411e6aaff00163e010eb1");
		activitySale.setUpdateTime(activitySale.getCreateTime());
		activitySale.setDisabled(Disabled.valid);
		activitySale.setType(ActivityTypeEnum.LOW_PRICE);
		
		ActivitySaleGoods goods1 = new ActivitySaleGoods();
		goods1.setId(UuidUtils.getUuid());
		goods1.setSaleId(saleId);
		goods1.setStoreSkuId("8a94e420593f4ec701593f5545dc0012");
		goods1.setDisabled(Disabled.valid);
		goods1.setTradeMax(1);
		goods1.setSaleStock(1);
		goods1.setCreateTime(new Date());
		goods1.setUpdateTime(goods1.getCreateTime());
		goods1.setCreateUserId("15dbcb06276411e6aaff00163e010eb1");
		goods1.setUpdateUserId(goods1.getCreateUserId());
		List<ActivitySaleGoods> goods = new ArrayList<ActivitySaleGoods>();
		goods.add(goods1);
		try {
			service.save(activitySale, goods);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	@Test
	public void testUpdate() {
		try {
			String saleGoodsId = "8a94e71f5968724c01596872523b0004";
//			service.closeSaleGoods(saleGoodsId);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testFindActivitySaleByStoreId() {
		fail("Not yet implemented");
	}
}
