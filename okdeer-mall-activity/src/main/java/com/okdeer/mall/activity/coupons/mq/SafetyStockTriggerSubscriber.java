package com.okdeer.mall.activity.coupons.mq;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.okdeer.archive.goods.spu.enums.SpuTypeEnum;
import com.okdeer.archive.goods.store.entity.GoodsStoreSku;
import com.okdeer.archive.goods.store.entity.GoodsStoreSkuStock;
import com.okdeer.archive.goods.store.service.GoodsStoreSkuServiceApi;
import com.okdeer.archive.stock.service.GoodsStoreSkuStockApi;
import com.okdeer.archive.store.service.ISysUserAndExtServiceApi;
import com.okdeer.base.common.utils.DateUtils;
import com.okdeer.base.common.utils.StringUtils;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.base.common.utils.mapper.BeanMapper;
import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.base.framework.mq.annotation.RocketMQListener;
import com.okdeer.base.framework.mq.message.MQMessage;
import com.okdeer.jxc.stock.entity.GoodsStock;
import com.okdeer.jxc.stock.service.StockUpdateServiceApi;
import com.okdeer.mall.activity.coupons.bo.ActivitySaleRemindBo;
import com.okdeer.mall.activity.coupons.entity.ActivitySaleGoods;
import com.okdeer.mall.activity.coupons.mq.constants.SafetyStockTriggerTopic;
import com.okdeer.mall.activity.coupons.service.ActivitySaleGoodsServiceApi;
import com.okdeer.mall.activity.coupons.service.ActivitySaleRemindService;
import com.okdeer.mcm.entity.SmsVO;
import com.okdeer.mcm.service.ISmsService;

/**
 * 
 * ClassName: SafetyStockTriggerSubscriber 
 * @Description: 订阅活动安全库存触发消息
 * @author tangy
 * @date 2017年2月23日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *     2.0.0          2017年2月23日                               tangy
 */
@Service
public class SafetyStockTriggerSubscriber {

	private static final Logger log = LoggerFactory.getLogger(SafetyStockTriggerSubscriber.class);

    /**
     * 安全库存提醒短信
     */
    @Value("${sms.sale.security.stock}")
    private String saleSecurityStock;
    
    @Value("${mcm.sys.code}")
    private String mcmSysCode;

    @Value("${mcm.sys.token}")
    private String mcmSysToken;
    
	/**
	 * 安全库存联系关联人
	 */
	@Autowired
	private ActivitySaleRemindService activitySaleRemindService;
	
	/**
	 * 特惠活动商品
	 */
	@Reference(version = "1.0.0", check = false)
	private ActivitySaleGoodsServiceApi activitySaleGoodsServiceApi;
	
	/**
	 * SysUserAndExtService注入
	 */
	@Reference(version="1.0.0", check = false)
	private ISysUserAndExtServiceApi sysUserAndExtService;
	
	/**
	 * GoodsStoreSkuServiceApi注入
	 */
	@Reference(version="1.0.0", check = false)
	private GoodsStoreSkuServiceApi goodsStoreSkuServiceApi;
	
	/**
	 * GoodsStoreSkuStockServiceApi注入
	 */
	@Reference(version="1.0.0", check = false)
	private GoodsStoreSkuStockApi goodsStoreSkuStockApi;
	
	@Reference(version="1.0.0", check = false)
	private StockUpdateServiceApi stockUpdateServiceApi;
	
    /**
     * 短信接口
     */
    @Reference(version = "1.0.0", check = false)
    ISmsService smsService;

	@SuppressWarnings("unchecked")
	@RocketMQListener(topic = SafetyStockTriggerTopic.TOPIC_SAFETY_STOCK_TRIGGER, tag = "*")
	public ConsumeConcurrentlyStatus trigger(MQMessage enMessage) {
		Map<String, String> storeSkuIdMap =  (Map<String, String>) enMessage.getContent();
		log.info("活动安全库存信息：{}", JsonMapper.nonEmptyMapper().toJson(storeSkuIdMap));
		if (storeSkuIdMap != null && storeSkuIdMap.size() > 0) {
			// 判断是否需要短信提醒
			for(String storeSkuId: storeSkuIdMap.keySet()){ 
				sendSafetyWarning(storeSkuId, storeSkuIdMap.get(storeSkuId)); 
			}
		}	
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}
	
	/**
	 * 
	 * @Description: 活动安全库存预警
	 * @param storeSkuId   活动商品id
	 * @param saleId       活动id
	 * @return void  
	 * @author tangy
	 * @date 2017年2月22日
	 */
	private void sendSafetyWarning(String storeSkuId, String saleId){
		try {
			log.info("活动商品安全库存预警:{},{}", storeSkuId, saleId);
			ActivitySaleGoods saleGoods = new ActivitySaleGoods();
			saleGoods.setStoreSkuId(storeSkuId);
			saleGoods.setSaleId(saleId);
			ActivitySaleGoods activitySaleGoods = activitySaleGoodsServiceApi.selectByObject(saleGoods);
			//判断是否有设置安全库存
			if (activitySaleGoods != null && activitySaleGoods.getSecurityStock() != null 
					&& activitySaleGoods.getSecurityStock().intValue() > 0) {
				//是否已提醒
				if (activitySaleGoods.getIsRemind() != null && activitySaleGoods.getIsRemind().intValue() > 0) {
					return;
				}
				
				// 商品库存
				GoodsStoreSkuStock stock = findByStoreSkuId(storeSkuId);
				 
				//是否达到提醒条件，安全库存大于活动剩余库存
				if (stock != null && stock.getSellable() != null 
						&& activitySaleGoods.getSecurityStock().intValue() > stock.getSellable().intValue()) {
					getSaleReminds(activitySaleGoods);
				}
			}
		} catch (Exception e) {
			log.error("活动安全库存预警异常,{}", e);
			return;
		}
	}
	
	/**
	 * 
	 * @Description: 查询商品库存
	 * @param storeSkuId  店铺商品id
	 * @return GoodsStoreSkuStock  
	 * @author tangy
	 * @date 2017年3月2日
	 */
	private GoodsStoreSkuStock findByStoreSkuId(String storeSkuId) throws Exception{
		// 商品信息
		GoodsStoreSku goodsStoreSku = goodsStoreSkuServiceApi.getById(storeSkuId);
		// 商品库存
		GoodsStoreSkuStock stock = null;
		if (goodsStoreSku != null) {
			if (goodsStoreSku.getSpuTypeEnum() == SpuTypeEnum.assembleSpu) {
				// 组合商品查询商城数据库
	            stock = goodsStoreSkuStockApi.findByStoreSkuId(storeSkuId);
			} else {
				// 查询零售库存信息，避免数据同步延时不准确
				GoodsStock goodsStock = stockUpdateServiceApi.getGoodsStockInfo(storeSkuId);
				stock = BeanMapper.map(goodsStock, GoodsStoreSkuStock.class);
			}
		}
		return stock;
	}
	
	
	/**
	 * 
	 * @Description: 获取活动安全库存联系人
	 * @param activitySaleGoods
	 * @throws Exception   
	 * @return void  
	 * @author tangy
	 * @date 2017年3月1日
	 */
	private void getSaleReminds(ActivitySaleGoods activitySaleGoods) throws Exception{
		//活动安全库存联系人
		List<ActivitySaleRemindBo> saleRemind = activitySaleRemindService.findActivitySaleRemindBySaleId(activitySaleGoods.getSaleId());
		if (CollectionUtils.isNotEmpty(saleRemind)) {
			//短信提醒联系人
			List<String> phoneList = new ArrayList<String>();
			for (ActivitySaleRemindBo activitySaleRemindBo : saleRemind) {
				if (StringUtils.isNoneBlank(activitySaleRemindBo.getPhone())) {
					phoneList.add(activitySaleRemindBo.getPhone());
				}
			}
			//是否有需要发送提醒短信的联系人
			if (CollectionUtils.isNotEmpty(phoneList)) {
				activitySaleGoods.setIsRemind(1);
			    activitySaleGoodsServiceApi.updateActivitySaleGoods(activitySaleGoods);
				sendMessg(phoneList);
			}
		}
	}

	/**
	 * 
	 * @Description: 发送提醒短信
	 * @param phonenos   需要发送的手机号码
	 * @return void  
	 * @author tangy
	 * @date 2017年2月21日
	 */
	private void sendMessg(List<String> phonenos) {
		//发送提醒短信
		String content = saleSecurityStock;
		List<SmsVO> list = new ArrayList<SmsVO>();
		for (String phoneno : phonenos) {
			SmsVO smsVo = createSmsVo(phoneno, content);
			list.add(smsVo);
		}
		this.smsService.sendSms(list);
	}

	/**
	 * 
	 * @Description:  创建短信发送对象
	 * @param mobile  手机号码
	 * @param content 短信内容
	 * @return SmsVO  
	 * @author tangy
	 * @date 2017年2月21日
	 */
	private SmsVO createSmsVo(String mobile, String content) {
		SmsVO smsVo = new SmsVO();
		smsVo.setId(UuidUtils.getUuid());
		smsVo.setUserId(mobile);
		smsVo.setIsTiming(0);
		smsVo.setToken(mcmSysToken);
		smsVo.setSysCode(mcmSysCode);
		smsVo.setMobile(mobile);
		smsVo.setContent(content);
		smsVo.setSmsChannelType(3);
		smsVo.setSendTime(DateUtils.formatDateTime(new Date()));
		return smsVo;
	}

}
