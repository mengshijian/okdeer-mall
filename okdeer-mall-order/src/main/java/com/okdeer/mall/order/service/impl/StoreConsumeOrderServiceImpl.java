package com.okdeer.mall.order.service.impl;

import static com.okdeer.mall.order.constant.mq.PayMessageConstant.TAG_PAY_TRADE_MALL;
import static com.okdeer.mall.order.constant.mq.PayMessageConstant.TOPIC_BALANCE_PAY_TRADE;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.rocketmq.client.producer.LocalTransactionExecuter;
import com.alibaba.rocketmq.client.producer.LocalTransactionState;
import com.alibaba.rocketmq.client.producer.TransactionCheckListener;
import com.alibaba.rocketmq.client.producer.TransactionSendResult;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.github.pagehelper.PageHelper;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.okdeer.api.pay.enums.BusinessTypeEnum;
import com.okdeer.api.pay.enums.RefundTypeEnum;
import com.okdeer.api.pay.pay.dto.PayRefundDto;
import com.okdeer.api.pay.tradeLog.dto.BalancePayTradeDto;
import com.okdeer.archive.goods.store.entity.GoodsStoreSkuService;
import com.okdeer.archive.goods.store.service.GoodsStoreSkuServiceServiceApi;
import com.okdeer.archive.store.entity.StoreInfo;
import com.okdeer.archive.store.entity.StoreInfoExt;
import com.okdeer.archive.store.service.IStoreInfoExtServiceApi;
import com.okdeer.archive.store.service.StoreInfoServiceApi;
import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.base.common.utils.PageUtils;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.base.framework.mq.RocketMQProducer;
import com.okdeer.base.framework.mq.RocketMQTransactionProducer;
import com.okdeer.base.framework.mq.RocketMqResult;
import com.okdeer.base.framework.mq.message.MQMessage;
import com.okdeer.mall.activity.coupons.service.ActivitySaleRecordService;
import com.okdeer.mall.common.consts.Constant;
import com.okdeer.mall.common.utils.DateUtils;
import com.okdeer.mall.common.utils.RobotUserUtil;
import com.okdeer.mall.common.utils.TradeNumUtil;
import com.okdeer.mall.member.member.entity.MemberConsigneeAddress;
import com.okdeer.mall.member.member.enums.AddressDefault;
import com.okdeer.mall.member.member.service.MemberConsigneeAddressServiceApi;
import com.okdeer.mall.order.constant.mq.PayMessageConstant;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.entity.TradeOrderItem;
import com.okdeer.mall.order.entity.TradeOrderItemDetail;
import com.okdeer.mall.order.entity.TradeOrderPay;
import com.okdeer.mall.order.entity.TradeOrderRefunds;
import com.okdeer.mall.order.entity.TradeOrderRefundsItem;
import com.okdeer.mall.order.entity.TradeOrderRefundsItemDetail;
import com.okdeer.mall.order.entity.TradeOrderRefundsLog;
import com.okdeer.mall.order.enums.ActivityBelongType;
import com.okdeer.mall.order.enums.ConsumeStatusEnum;
import com.okdeer.mall.order.enums.ConsumerCodeStatusEnum;
import com.okdeer.mall.order.enums.OrderAppStatusAdaptor;
import com.okdeer.mall.order.enums.OrderComplete;
import com.okdeer.mall.order.enums.OrderItemStatusEnum;
import com.okdeer.mall.order.enums.OrderStatusEnum;
import com.okdeer.mall.order.enums.PayTypeEnum;
import com.okdeer.mall.order.enums.PayWayEnum;
import com.okdeer.mall.order.enums.RefundsStatusEnum;
import com.okdeer.mall.order.mapper.TradeOrderItemDetailMapper;
import com.okdeer.mall.order.mapper.TradeOrderItemMapper;
import com.okdeer.mall.order.mapper.TradeOrderMapper;
import com.okdeer.mall.order.mapper.TradeOrderRefundsItemDetailMapper;
import com.okdeer.mall.order.mapper.TradeOrderRefundsItemMapper;
import com.okdeer.mall.order.mapper.TradeOrderRefundsLogMapper;
import com.okdeer.mall.order.mapper.TradeOrderRefundsMapper;
import com.okdeer.mall.order.service.GenerateNumericalService;
import com.okdeer.mall.order.service.StockOperateService;
import com.okdeer.mall.order.service.StoreConsumeOrderService;
import com.okdeer.mall.order.service.TradeMessageService;
import com.okdeer.mall.order.service.TradeOrderActivityService;
import com.okdeer.mall.order.service.TradeOrderRefundsCertificateService;
import com.okdeer.mall.order.vo.ExpireStoreConsumerOrderVo;
import com.okdeer.mall.order.vo.TradeOrderRefundsCertificateVo;
import com.okdeer.mall.order.vo.UserTradeOrderDetailVo;
import com.okdeer.mall.system.mq.RollbackMQProducer;
import com.okdeer.mall.system.utils.ConvertUtil;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * ClassName: ConsumerCodeOrderServiceImpl
 * 
 * @Description: 到店消费接口实现类
 * @author zengjizu
 * @date 2016年9月20日
 *
 *       ================================================================================================= 
 *       Task ID              Date               Author               Description
 *       ----------------+----------------+-------------------+------------------------------------------- 
 *       v1.1.0             2016-9-20            zengjz             增加查询消费码订单列表
 *       V1.1.0             2016-10-8            zhaoqc             新增通过消费码消费状态判断订单能否投诉
 *       13960             2016-10-10            wusw               修改通过订单消费码状态判断订单是否支持投诉
 *       v1.2.0            2016-11-15            zengjz             删除一些无用的代码
 *        v1.2.0            2016-11-28          zengjz             根据退款单项id查询订单明细列表
 */
@Service
public class StoreConsumeOrderServiceImpl implements StoreConsumeOrderService {

	private static final Logger logger = LoggerFactory.getLogger(StoreConsumeOrderServiceImpl.class);

	@Autowired
	private TradeOrderMapper tradeOrderMapper;

	@Autowired
	private TradeOrderItemMapper tradeOrderItemMapper;

	@Autowired
	private TradeOrderItemDetailMapper tradeOrderItemDetailMapper;

	@Autowired
	private TradeOrderRefundsMapper tradeOrderRefundsMapper;

	@Autowired
	private TradeOrderRefundsItemMapper tradeOrderRefundsItemMapper;
	
	@Autowired
	private TradeOrderRefundsItemDetailMapper tradeOrderRefundsItemDetailMapper;

	@Reference(version = "1.0.0", check = false)
	private IStoreInfoExtServiceApi storeInfoExtService;

	@Reference(version = "1.0.0", check = false)
	private MemberConsigneeAddressServiceApi memberConsigneeAddressService;

	@Reference(version = "1.0.0", check = false)
	private GoodsStoreSkuServiceServiceApi goodsStoreSkuServiceServiceApi;
	/**
	 * 单号生成器
	 */
	@Resource
	private GenerateNumericalService generateNumericalService;

	@Autowired
	private RocketMQTransactionProducer rocketMQTransactionProducer;

	@Reference(version = "1.0.0", check = false)
	private StoreInfoServiceApi storeInfoService;

	@Autowired
	private TradeOrderActivityService tradeOrderActivityService;

	/**
	 * 回滚消息生产者
	 */
	@Resource
	private RollbackMQProducer rollbackMQProducer;

	/**
	 * 特惠活动记录信息service
	 */
	@Autowired
	private ActivitySaleRecordService activitySaleRecordService;

	@Resource
	private TradeOrderRefundsCertificateService tradeOrderRefundsCertificateService;

	@Resource
	private TradeOrderRefundsLogMapper tradeOrderRefundsLogMapper;

	/**
	 * 消息发送
	 */
	@Autowired
	private TradeMessageService tradeMessageService;
	
	/**
	 * 库存操作service
	 */
	@Resource
	private StockOperateService stockOperateService;
	
	@Autowired
	private RocketMQProducer rocketMQProducer;

	@Override
	public PageUtils<TradeOrder> findStoreConsumeOrderList(Map<String, Object> map, Integer pageNo, Integer pageSize) {
		PageHelper.startPage(pageNo, pageSize, true, false);

		String status = (String) map.get("status");
		List<TradeOrder> list = new ArrayList<TradeOrder>();

		if (status != null) {
			// 订单状态
			List<String> orderStatus = new ArrayList<String>();

			if (status.equals(String.valueOf(Constant.ONE))) {
				// 查询未支付的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.UNPAID.ordinal()));
				orderStatus.add(String.valueOf(OrderStatusEnum.BUYER_PAYING.ordinal()));
				map.put("orderStatus", orderStatus);
			} else if (status.equals(String.valueOf(Constant.TWO))) {
				// 查询已经取消的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.CANCELED.ordinal()));
				orderStatus.add(String.valueOf(OrderStatusEnum.CANCELING.ordinal()));
				map.put("orderStatus", orderStatus);
			} else if (status.equals(String.valueOf(Constant.THREE))) {
				// 查询未消费的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.HAS_BEEN_SIGNED.ordinal()));
				map.put("orderStatus", orderStatus);
				map.put("consumerCodeStatus", ConsumerCodeStatusEnum.WAIT_CONSUME.ordinal());
			} else if (status.equals(String.valueOf(Constant.FOUR))) {
				// 查询已过期的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.HAS_BEEN_SIGNED.ordinal()));
				map.put("orderStatus", orderStatus);
				map.put("consumerCodeStatus", ConsumerCodeStatusEnum.EXPIRED.ordinal());
			} else if (status.equals(String.valueOf(Constant.FIVE))) {
				// 查询已消费的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.HAS_BEEN_SIGNED.ordinal()));
				map.put("orderStatus", orderStatus);
				map.put("consumerCodeStatus", ConsumerCodeStatusEnum.WAIT_EVALUATE.ordinal());
			} else if (status.equals(String.valueOf(Constant.SIX))) {
				// 查询已退款的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.HAS_BEEN_SIGNED.ordinal()));
				map.put("orderStatus", orderStatus);
				map.put("consumerCodeStatus", ConsumerCodeStatusEnum.REFUNDED.ordinal());
			} else if (status.equals(String.valueOf(Constant.SEVEN))) {
				// 查询已完成的消费码订单
				orderStatus.add(String.valueOf(OrderStatusEnum.HAS_BEEN_SIGNED.ordinal()));
				map.put("orderStatus", orderStatus);
				map.put("consumerCodeStatus", ConsumerCodeStatusEnum.COMPLETED.ordinal());
			}
			list = tradeOrderMapper.selectStoreConsumeOrderList(map);
		}

		for (TradeOrder vo : list) {
			List<TradeOrderItem> items = tradeOrderItemMapper.selectTradeOrderItem(vo.getId());
			vo.setTradeOrderItem(items);
		}
		return new PageUtils<TradeOrder>(list);
	}

	@Override
	public JSONObject findStoreConsumeOrderDetail(String orderId) throws Exception {

		UserTradeOrderDetailVo userTradeOrderDetailVo = tradeOrderMapper.findStoreConsumeOrderById(orderId);

		List<TradeOrderItem> tradeOrderItems = tradeOrderItemMapper.selectTradeOrderItemOrRefund(orderId);
		// 判断订单是否评价appraise大于0，已评价
		Integer appraise = tradeOrderItemMapper.selectTradeOrderItemIsAppraise(orderId);
		// 查询店铺扩展信息
		JSONObject json = new JSONObject();
		try {
			// 1 订单信息
			json.put("orderId", userTradeOrderDetailVo.getId() == null ? "" : userTradeOrderDetailVo.getId());

			ConsumerCodeStatusEnum consumerCodeStatusEnum = OrderAppStatusAdaptor.convertAppStoreConsumeOrderStatus(
					userTradeOrderDetailVo.getStatus(), userTradeOrderDetailVo.getConsumerCodeStatus());

			json.put("orderStatus", consumerCodeStatusEnum.ordinal());
			json.put("orderStatusName", consumerCodeStatusEnum.getValue());

			// 2 订单支付倒计时计算
			long remainingTime = (userTradeOrderDetailVo.getCreateTime().getTime() + 1800000
					- System.currentTimeMillis()) / 1000;
			if (remainingTime < 0) {
				remainingTime = 0;
			}
			json.put("remainingTime", remainingTime <= 0 ? 0: remainingTime);
			// 支付信息
			TradeOrderPay payInfo = userTradeOrderDetailVo.getTradeOrderPay();

			if (payInfo != null) {
				// 0:余额支付 1:支付宝 2:微信支付
				json.put("payMethod", payInfo.getPayType().ordinal());
				json.put("orderPayTime", DateUtils.formatDate(payInfo.getPayTime(), "yyyy-MM-dd HH:mm:ss"));
				// 是否支持投诉 0：不支持 1:支持
				// Begin 13960 add by wusw 20161010
				// 订单消费码状态为待评价、已过期、已退款时，支持投诉
				if (userTradeOrderDetailVo.getConsumerCodeStatus() == ConsumerCodeStatusEnum.WAIT_EVALUATE
						|| userTradeOrderDetailVo.getConsumerCodeStatus() == ConsumerCodeStatusEnum.EXPIRED
						|| userTradeOrderDetailVo.getConsumerCodeStatus() == ConsumerCodeStatusEnum.REFUNDED) {
					json.put("isSupportComplain", 1);
				} else {
					json.put("isSupportComplain", 0);
				}
				// End 13960 add by wusw 20161010
			} else {
				json.put("isSupportComplain", 0);
			}

			// 交易号
			json.put("tradeNum",
					userTradeOrderDetailVo.getTradeNum() == null ? "" : userTradeOrderDetailVo.getTradeNum());
			json.put("remark", userTradeOrderDetailVo.getRemark() == null ? "" : userTradeOrderDetailVo.getRemark());
			json.put("orderAmount",
					userTradeOrderDetailVo.getTotalAmount() == null ? "0" : userTradeOrderDetailVo.getTotalAmount());
			json.put("actualAmount",
					userTradeOrderDetailVo.getActualAmount() == null ? "0" : userTradeOrderDetailVo.getActualAmount());
			json.put("orderNo", userTradeOrderDetailVo.getOrderNo() == null ? "" : userTradeOrderDetailVo.getOrderNo());
			json.put("cancelReason",
					userTradeOrderDetailVo.getReason() == null ? "" : userTradeOrderDetailVo.getReason());
			json.put("orderSubmitOrderTime", userTradeOrderDetailVo.getCreateTime() != null
					? DateUtils.formatDate(userTradeOrderDetailVo.getCreateTime(), "yyyy-MM-dd HH:mm:ss") : "");

			json.put("activityType", userTradeOrderDetailVo.getActivityType() == null ? ""
					: userTradeOrderDetailVo.getActivityType().ordinal());
			json.put("preferentialPrice", userTradeOrderDetailVo.getPreferentialPrice() == null ? ""
					: userTradeOrderDetailVo.getPreferentialPrice().subtract(userTradeOrderDetailVo.getPinMoney()).toString());
			json.put("pinMoney", ConvertUtil.format(userTradeOrderDetailVo.getPinMoney()));
			// 订单评价类型0：未评价，1：已评价
			json.put("orderIsComment", appraise > 0 ? Constant.ONE : Constant.ZERO);
			// 订单投诉状态
			json.put("compainStatus", userTradeOrderDetailVo.getCompainStatus() == null ? ""
					: userTradeOrderDetailVo.getCompainStatus().ordinal());

			json.put("leaveMessage", userTradeOrderDetailVo.getRemark());
			// 订单状态为已取消且 更新时间不为null时设置正常的取消时间
			if (userTradeOrderDetailVo.getStatus() == OrderStatusEnum.CANCELED
					&& userTradeOrderDetailVo.getUpdateTime() != null) {
				json.put("cancelTime",
						DateUtils.formatDate(userTradeOrderDetailVo.getUpdateTime(), "yyyy-MM-dd HH:mm:ss"));
			} else {
				json.put("cancelTime", "");
			}
			// 店铺信息
			getStoreInfo(json, userTradeOrderDetailVo);
			// 商品信息
			getTradeItemInfo(json, tradeOrderItems);
			// 订单明细信息
			getTradeOrderItemDetail(json, userTradeOrderDetailVo, tradeOrderItems.get(0), orderId);
		} catch (ServiceException e) {
			throw new RuntimeException("查询店铺信息出错");
		}
		return json;
	}

	/**
	 * @Description: 获取店铺信息
	 * @param json
	 *            返回的json对象
	 * @param userTradeOrderDetailVo
	 *            订单信息
	 * @throws ServiceException
	 *             抛出异常
	 * @author zengjizu
	 * @date 2016年9月24日
	 */
	private void getStoreInfo(JSONObject json, UserTradeOrderDetailVo userTradeOrderDetailVo) throws ServiceException {
		StoreInfo storeInfo = userTradeOrderDetailVo.getStoreInfo();
		String storeName = "";
		String storeMobile = "";
		String address = "";
		String storeId = "";
		if (storeInfo != null) {
			storeId = storeInfo.getId();
			storeName = storeInfo.getStoreName();
			// storeMobile = storeInfo.getMobile();

			// 获取店铺客服电话
			StoreInfoExt storeInfoExt = storeInfoExtService.getByStoreId(storeId);
			if (storeInfoExt != null) {
				storeMobile = storeInfoExt.getServicePhone();
			}

			// 确认订单时，没有将地址保存到trade_order_logistics订单物流表，暂时取收货地址表的默认地址
			MemberConsigneeAddress memberConsigneeAddress = new MemberConsigneeAddress();
			memberConsigneeAddress.setUserId(storeId);
			memberConsigneeAddress.setIsDefault(AddressDefault.YES);

			List<MemberConsigneeAddress> memberAddressList = memberConsigneeAddressService
					.getList(memberConsigneeAddress);
			if (memberAddressList != null && memberAddressList.size() > 0) {
				MemberConsigneeAddress memberAddress = memberAddressList.get(0);
				address = memberAddress.getArea() + memberAddress.getAddress();
			}
		}

		json.put("orderShopid", storeId);
		json.put("orderShopName", storeName);
		json.put("orderShopMobile", storeMobile);
		json.put("orderExtractShopName", storeName);
		json.put("orderShopAddress", address);
		json.put("storeLogo", storeInfo == null || storeInfo.getLogoUrl() == null ? "" : storeInfo.getLogoUrl());

	}

	/**
	 * @Description: 获取订单项信息
	 * @param json
	 *            返回json对象
	 * @param tradeOrderItems
	 *            订单项
	 * @author zengjizu
	 * @date 2016年9月24日
	 */
	private void getTradeItemInfo(JSONObject json, List<TradeOrderItem> tradeOrderItems) {

		// TradeOrderItem tradeOrderItem = tradeOrderItems.get(0);

		JSONArray itemArry = new JSONArray();

		if (tradeOrderItems != null && tradeOrderItems.size() > 0) {
			JSONObject itemObject = null;
			GoodsStoreSkuService goodsStoreSkuService = null;
			for (TradeOrderItem tradeOrderItem : tradeOrderItems) {
				itemObject = new JSONObject();
				itemObject.put("itemId", tradeOrderItem.getId());
				itemObject.put("productId",
						tradeOrderItem.getStoreSkuId() == null ? "" : tradeOrderItem.getStoreSkuId());
				itemObject.put("mainPicPrl",
						tradeOrderItem.getMainPicPrl() == null ? "" : tradeOrderItem.getMainPicPrl());
				itemObject.put("skuName", tradeOrderItem.getSkuName() == null ? "" : tradeOrderItem.getSkuName());
				itemObject.put("unitPrice",
						tradeOrderItem.getUnitPrice() == null ? "0" : tradeOrderItem.getUnitPrice());
				itemObject.put("quantity", tradeOrderItem.getQuantity() == null ? "0" : tradeOrderItem.getQuantity());
				itemObject.put("skuTotalAmount",
						tradeOrderItem.getTotalAmount() == null ? "0" : tradeOrderItem.getTotalAmount());
				itemObject.put("skuActualAmount",
						tradeOrderItem.getActualAmount() == null ? "0" : tradeOrderItem.getActualAmount());
				itemObject.put("skuPreferPrice",
						tradeOrderItem.getPreferentialPrice() == null ? "0" : tradeOrderItem.getPreferentialPrice());
				itemObject.put("unit",
						tradeOrderItem.getUnit() == null ? "" : tradeOrderItem.getUnit());

				goodsStoreSkuService = goodsStoreSkuServiceServiceApi
						.selectByStoreSkuId(tradeOrderItem.getStoreSkuId());

				if (goodsStoreSkuService != null) {
					// 是否需要预约0：不需要，1：需要
					itemObject.put("isPrecontract", goodsStoreSkuService.getIsAppointment().ordinal());
					itemObject.put("appointmentHour", goodsStoreSkuService.getAppointmentHour());
					// 是否支持退订0：不支持，1：支持
					itemObject.put("isUnsubscribe", goodsStoreSkuService.getIsUnsubscribe().ordinal());

					String startDate = DateUtils.formatDate(goodsStoreSkuService.getStartTime(), "yyyy-MM-dd");
					String endDate = DateUtils.formatDate(goodsStoreSkuService.getEndTime(), "yyyy-MM-dd");
					itemObject.put("orderInDate", startDate + "至" + endDate);
					itemObject.put("notAvailableDate", goodsStoreSkuService.getInvalidDate());
				} else {
					// 是否需要预约0：不需要，1：需要
					itemObject.put("isPrecontract", 0);
					itemObject.put("appointmentHour", 0);
					// 是否支持退订0：不支持，1：支持
					itemObject.put("isUnsubscribe", 0);
					itemObject.put("orderInDate", "");
					itemObject.put("notAvailableDate", "");
				}

				itemArry.add(itemObject);

				itemObject = null;
				goodsStoreSkuService = null;
			}
		}
		json.put("items", itemArry);
	}

	/**
	 * @Description: 获取订单明细列表信息
	 * @param json
	 *            返回的json对象
	 * @param userTradeOrderDetailVo
	 *            订单信息
	 * @param tradeOrderItem
	 *            订单项信息
	 * @param orderId
	 *            订单id
	 * @author zengjizu
	 * @date 2016年9月24日
	 */
	private void getTradeOrderItemDetail(JSONObject json, UserTradeOrderDetailVo userTradeOrderDetailVo,
			TradeOrderItem tradeOrderItem, String orderId) {

		// 消费码列表
		List<TradeOrderItemDetail> detailList = tradeOrderItemDetailMapper.selectByOrderItemDetailByOrderId(orderId);

		JSONArray consumeCodeList = new JSONArray();

		if (CollectionUtils.isNotEmpty(detailList)) {
			JSONObject detail = null;
			for (TradeOrderItemDetail tradeOrderItemDetail : detailList) {
				detail = new JSONObject();

				detail.put("consumeId", tradeOrderItemDetail.getId());
				detail.put("consumeCode", tradeOrderItemDetail.getConsumeCode());
				// 0：未消费，1：已消费，2：已退款，3：已过期
				detail.put("consumeStatus", tradeOrderItemDetail.getStatus().ordinal());
				if (tradeOrderItemDetail.getUseTime() != null) {
					detail.put("consumeTime",
							DateUtils.formatDate(tradeOrderItemDetail.getUseTime(), "yyyy-MM-dd HH:mm:ss"));
				} else {
					detail.put("consumeTime", "");
				}

				if (tradeOrderItem != null) {
					detail.put("consumePrice", tradeOrderItemDetail.getActualAmount());
				} else {
					detail.put("consumePrice", "0");
				}

				if (tradeOrderItemDetail.getStatus() == ConsumeStatusEnum.refund) {
					detail.put("refundTime",
							DateUtils.formatDate(tradeOrderItemDetail.getUpdateTime(), "yyyy-MM-dd HH:mm:ss"));
				} else {
					detail.put("refundTime", "");
				}
				consumeCodeList.add(detail);
				detail = null;
			}
		}
		json.put("consumeCodeList", consumeCodeList);
	}

	@Override
	public PageUtils<TradeOrderRefunds> findUserRefundOrderList(Map<String, Object> params, Integer pageNo,
			Integer pageSize) {
		PageHelper.startPage(pageNo, pageSize, true, false);
		List<TradeOrderRefunds> list = tradeOrderRefundsMapper.getListByParams(params);
		return new PageUtils<TradeOrderRefunds>(list);
	}

	@Override
	public TradeOrderRefunds getRefundOrderDetail(String refundId) {
		TradeOrderRefunds refunds = tradeOrderRefundsMapper.findStoreConsumeOrderDetailById(refundId);
		List<TradeOrderRefundsItem> itemList = tradeOrderRefundsItemMapper
				.getTradeOrderRefundsItemByRefundsId(refunds.getId());
		refunds.setTradeOrderRefundsItem(itemList);
		return refunds;
	}

	@Override
	public List<TradeOrderItemDetail> getStoreConsumeOrderDetailList(String orderId, int status) {
		List<TradeOrderItemDetail> list = tradeOrderItemDetailMapper.selectItemDetailByOrderIdAndStatus(orderId,
				status);

		return list;
	}

	@Override
	public List<ExpireStoreConsumerOrderVo> findExpireOrder() {

		return tradeOrderItemDetailMapper.findExpireList();
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void handleExpireOrder(TradeOrder order) throws Exception {

		List<TradeOrderItem> orderItem = tradeOrderItemMapper.selectOrderItemListById(order.getId());
		order.setTradeOrderItem(orderItem);
		if (orderItem != null && !Iterables.isEmpty(orderItem)) {

			for (TradeOrderItem item : order.getTradeOrderItem()) {
				if (item.getServiceAssurance() != null && item.getServiceAssurance() > 0) {
					logger.info("超时未消费，系统自动退款,订单号：" + order.getOrderNo());
					// 超时未消费，系统自动申请退款
					refundOrder(order, item);
				} else {
					// 不支持退款，将消费码状态改为已经过期，同时要将商家的冻结金额还回给平台
					expireOrder(order, item);
				}
			}
		}
	}
	
	@Transactional(rollbackFor = Exception.class)
	private void expireOrder(TradeOrder order, TradeOrderItem item) throws Exception {

		// 构建余额支付（或添加交易记录）对象
		Message msg = new Message(TOPIC_BALANCE_PAY_TRADE, TAG_PAY_TRADE_MALL,
				buildBalancePayTrade(order, item).getBytes(Charsets.UTF_8));
		// 发送事务消息
		TransactionSendResult sendResult = rocketMQTransactionProducer.send(msg, order, new LocalTransactionExecuter() {

			@Override
			public LocalTransactionState executeLocalTransactionBranch(Message msg, Object object) {
				try {
					// 执行同意退款操作
					updateOrderStatus(order, item);
				} catch (Exception e) {
					logger.error("执行同意退款操作异常", e);
					return LocalTransactionState.ROLLBACK_MESSAGE;
				}
				return LocalTransactionState.COMMIT_MESSAGE;
			}
		}, new TransactionCheckListener() {
			@Override
			public LocalTransactionState checkLocalTransactionState(MessageExt msg) {
				return LocalTransactionState.COMMIT_MESSAGE;
			}
		});
		RocketMqResult.returnResult(sendResult);
	}
	
	/**
	 * @Description: 构建到店消费过期退款到平台的消息
	 * @param order 订单信息
	 * @param item 订单项信息
	 * @return
	 * @throws ServiceException
	 * @author zengjizu
	 * @date 2016年11月15日
	 */
	private String buildBalancePayTrade(TradeOrder order, TradeOrderItem item) throws Exception {
		// 退款金额
		BigDecimal refundAmount = new BigDecimal("0.00");
		// 退款优惠金额
		BigDecimal refundPrefeAmount = new BigDecimal("0.00");
		// 查询未退款的消费码列表
		List<TradeOrderItemDetail> detailList = tradeOrderItemDetailMapper
				.selectItemDetailByItemIdAndStatus(item.getId(), ConsumeStatusEnum.noConsume.ordinal());

		if (CollectionUtils.isNotEmpty(detailList)) {
			for (TradeOrderItemDetail tradeOrderItemDetail : detailList) {
				if (tradeOrderItemDetail.getOrderItemId().equals(item.getId())) {
					refundAmount = refundAmount.add(tradeOrderItemDetail.getActualAmount());
					refundPrefeAmount = refundPrefeAmount.add(tradeOrderItemDetail.getPreferentialPrice());
				}
			}
		}

		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(refundAmount);
		payTradeVo.setIncomeUserId("1");
		payTradeVo.setPayUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("[" + order.getOrderNo() + "]到店消费订单过期");
		payTradeVo.setBusinessType(BusinessTypeEnum.CONSUME_CODE_EXPIRE);
		payTradeVo.setExt("GQ" + TradeNumUtil.getTradeNum());

		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		// payTradeVo.setRemark("关联订单号：" + order.getOrderNo());
		// 优惠额退款 判断是否有优惠劵
		ActivityBelongType activityResource = tradeOrderActivityService.findActivityType(order);
		if (activityResource == ActivityBelongType.OPERATOR
				|| activityResource == ActivityBelongType.AGENT && (refundPrefeAmount.compareTo(BigDecimal.ZERO) > 0)) {
			payTradeVo.setPrefeAmount(refundPrefeAmount);
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
		}
		// 接受返回消息的tag
		payTradeVo.setTag(null);
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.fromObject(payTradeVo).toString();
	}
	
	/**
	 * @Description: 构建云钱包调用消息
	 * @param order 订单信息
	 * @param orderRefunds 退款单信息
	 * @return
	 * @throws Exception
	 * @author zengjizu
	 * @date 2016年10月11日
	 */
	private String buildBalancePayTrade(TradeOrder order, TradeOrderRefunds orderRefunds) throws Exception {

		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(orderRefunds.getTotalAmount());
		payTradeVo.setIncomeUserId(orderRefunds.getUserId());
		payTradeVo.setPayUserId(storeInfoService.getBossIdByStoreId(orderRefunds.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setRefundNo(orderRefunds.getRefundNo());
		payTradeVo.setTitle("订单退款(余额支付)，退款交易号：" + orderRefunds.getRefundNo());
		payTradeVo.setBusinessType(BusinessTypeEnum.REFUND_ORDER);
		payTradeVo.setServiceFkId(orderRefunds.getId());
		payTradeVo.setServiceNo(orderRefunds.getOrderNo());
		payTradeVo.setRemark("关联订单号：" + orderRefunds.getOrderNo());
		// 优惠额退款 判断是否有优惠劵
		ActivityBelongType activityResource = tradeOrderActivityService.findActivityType(order);
		if (activityResource == ActivityBelongType.OPERATOR || activityResource == ActivityBelongType.AGENT
				&& (orderRefunds.getTotalPreferentialPrice().compareTo(BigDecimal.ZERO) > 0)) {
			payTradeVo.setPrefeAmount(orderRefunds.getTotalPreferentialPrice());
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
		}
		// 接受返回消息的tag
		payTradeVo.setTag(PayMessageConstant.TAG_PAY_RESULT_REFUND);
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.fromObject(payTradeVo).toString();
	}

	@Transactional(rollbackFor = Exception.class)
	private void updateOrderStatus(TradeOrder order, TradeOrderItem item) throws Exception {
		List<String> rpcIdList = new ArrayList<String>();
		try {
			// 更新消费码为全部过期状态
			int result = tradeOrderItemDetailMapper.updateStatusWithExpire(item.getId());
			if (result < 1) {
				// 如果没有更新到，说明已经处理过了，需要回滚本次事务
				throw new Exception("更新消费码状态失败");
			}
			// 更新订单的的消费状态为已过期
			order.setConsumerCodeStatus(ConsumerCodeStatusEnum.EXPIRED);
			// 更新订单状态
			tradeOrderMapper.updateOrderStatus(order);
			//消费过期的数量
			item.setQuantity(result);
			// 回收库存
			order.setTradeOrderItem(Lists.newArrayList(item));
			stockOperateService.recycleStockByOrder(order, rpcIdList);
		} catch (Exception e) {
			rollbackMQProducer.sendStockRollbackMsg(rpcIdList);
			throw e;
		}
	}
	
	
	@Transactional(rollbackFor = Exception.class)
	private void refundOrder(TradeOrder order, TradeOrderItem item) throws Exception {

		TradeOrderRefunds orderRefunds = new TradeOrderRefunds();
		String refundsId = UuidUtils.getUuid();
		orderRefunds.setId(refundsId);
		orderRefunds.setRefundNo(generateNumericalService.generateOrderNo("XT"));
		orderRefunds.setOrderId(order.getId());
		orderRefunds.setOrderNo(order.getOrderNo());
		orderRefunds.setStoreId(order.getStoreId());
		orderRefunds.setOperator(RobotUserUtil.getRobotUser().getId());
		// 退款原因
		orderRefunds.setRefundsReason("到店消费订单超时未消费，系统自动退款");
		// 说明
		orderRefunds.setRefundsStatus(RefundsStatusEnum.SELLER_REFUNDING);
		orderRefunds.setStatus(OrderItemStatusEnum.ALL_REFUND);
		orderRefunds.setType(order.getType());
		// 退款单来源
		orderRefunds.setOrderResource(order.getOrderResource());
		orderRefunds.setOrderNo(order.getOrderNo());
		// 支付类型
		if (order.getTradeOrderPay() != null) {
			orderRefunds.setPaymentMethod(order.getTradeOrderPay().getPayType());
		} else if (order.getPayWay() == PayWayEnum.CASH_DELIERY) {
			orderRefunds.setPaymentMethod(PayTypeEnum.CASH);
		}
		orderRefunds.setUserId(order.getUserId());
		orderRefunds.setCreateTime(new Date());
		orderRefunds.setUpdateTime(new Date());
		// 退款金额
		BigDecimal refundAmount = new BigDecimal("0.00");
		// 退款优惠金额
		BigDecimal refundPrefeAmount = new BigDecimal("0.00");

		// 退款数量
		int quantity = 0;

		// 查询未退款的消费码列表
		List<TradeOrderItemDetail> detailList = tradeOrderItemDetailMapper
				.selectItemDetailByItemIdAndStatus(item.getId(), ConsumeStatusEnum.noConsume.ordinal());

		if (CollectionUtils.isNotEmpty(detailList)) {
			for (TradeOrderItemDetail tradeOrderItemDetail : detailList) {
				if (tradeOrderItemDetail.getOrderItemId().equals(item.getId())) {
					quantity++;
					refundAmount = refundAmount.add(tradeOrderItemDetail.getActualAmount());
					refundPrefeAmount = refundPrefeAmount.add(tradeOrderItemDetail.getPreferentialPrice());
				}
			}
		}

		TradeOrderRefundsItem refundsItem = new TradeOrderRefundsItem();
		refundsItem.setId(UuidUtils.getUuid());
		refundsItem.setRefundsId(refundsId);
		refundsItem.setOrderItemId(item.getId());
		refundsItem.setPropertiesIndb(item.getPropertiesIndb());
		refundsItem.setQuantity(quantity);
		refundsItem.setAmount(refundAmount);
		refundsItem.setPreferentialPrice(refundPrefeAmount);
		refundsItem.setBarCode(item.getBarCode());
		refundsItem.setMainPicUrl(item.getMainPicPrl());
		refundsItem.setSkuName(item.getSkuName());
		refundsItem.setSpuType(item.getSpuType());
		refundsItem.setStyleCode(item.getStyleCode());
		refundsItem.setPreferentialPrice(item.getPreferentialPrice());
		refundsItem.setStatus(OrderItemStatusEnum.ALL_REFUND);
		refundsItem.setStoreSkuId(item.getStoreSkuId());
		refundsItem.setUnitPrice(item.getUnitPrice());
		refundsItem.setWeight(item.getWeight());

		List<TradeOrderRefundsItem> items = Lists.newArrayList(refundsItem);
		orderRefunds.setTradeOrderRefundsItem(items);
		orderRefunds.setTotalAmount(refundAmount);
		orderRefunds.setTotalPreferentialPrice(refundPrefeAmount);
		// 调用退款操作
		this.refundConsumeCode(order, orderRefunds, buildRefundCertificate(refundsId), detailList);
	}

	private TradeOrderRefundsCertificateVo buildRefundCertificate(String refundsId) {
		TradeOrderRefundsCertificateVo certificate = new TradeOrderRefundsCertificateVo();
		String certificateId = UuidUtils.getUuid();
		certificate.setId(certificateId);
		certificate.setRefundsId(refundsId);
		certificate.setCreateTime(new Date());
		// 买家用户ID buyerUserId
		certificate.setOperator(RobotUserUtil.getRobotUser().getId());
		certificate.setRemark("到店消费商品超时未消费，系统自动退款");
		return certificate;
	}
	
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void refundConsumeCode(TradeOrder order, TradeOrderRefunds orderRefunds,
			TradeOrderRefundsCertificateVo certificate, List<TradeOrderItemDetail> waitRefundDetailList)
			throws Exception {
		orderRefunds.setTradeNum(TradeNumUtil.getTradeNum());
		ActivityBelongType activityResource = tradeOrderActivityService.findActivityType(order);

		// 是否需要算优惠金额
		boolean bln = activityResource == ActivityBelongType.OPERATOR || activityResource == ActivityBelongType.AGENT;

		List<TradeOrderRefundsItem> tradeOrderRefundsItemList = orderRefunds.getTradeOrderRefundsItem();
		BigDecimal totalIncome = BigDecimal.ZERO;
		for (TradeOrderRefundsItem tradeOrderRefundsItem : tradeOrderRefundsItemList) {
			if (bln) {
				tradeOrderRefundsItem
						.setIncome(tradeOrderRefundsItem.getAmount().add(tradeOrderRefundsItem.getPreferentialPrice()));
			} else {
				tradeOrderRefundsItem.setIncome(tradeOrderRefundsItem.getAmount());
			}
			totalIncome = totalIncome.add(tradeOrderRefundsItem.getIncome());
		}
		orderRefunds.setTotalIncome(totalIncome);
		
		// 增加退款单操作记录
		tradeOrderRefundsLogMapper
				.insertSelective(new TradeOrderRefundsLog(orderRefunds.getId(), orderRefunds.getOperator(),
						orderRefunds.getRefundsStatus().getName(), orderRefunds.getRefundsStatus().getValue()));
		
		if (PayTypeEnum.ALIPAY == orderRefunds.getPaymentMethod()
				|| PayTypeEnum.WXPAY == orderRefunds.getPaymentMethod()) {
			// 支付宝和微信原路返回
			orderRefunds.setRefundsStatus(RefundsStatusEnum.SELLER_REFUNDING);
			this.updateWalletByThird(order, orderRefunds, certificate, waitRefundDetailList);
			// Begin V2.4 added by maojj 2017-05-25
			PayRefundDto payRefundDto = new PayRefundDto();
			payRefundDto.setTradeAmount(orderRefunds.getTotalAmount());
			payRefundDto.setServiceId(orderRefunds.getId());
			payRefundDto.setServiceNo(orderRefunds.getOrderNo());
			payRefundDto.setRemark(String.format("关联订单号【%s】",orderRefunds.getOrderNo()));
			payRefundDto.setRefundType(RefundTypeEnum.REFUND_SERVICE_ORDER);
			payRefundDto.setTradeNum(order.getTradeNum());
			payRefundDto.setRefundNum(orderRefunds.getRefundNo());
			MQMessage msg = new MQMessage(PayMessageConstant.TOPIC_REFUND, (Serializable)payRefundDto);
			msg.setKey(orderRefunds.getId());
			rocketMQProducer.sendMessage(msg);
			// End V2.4 added by maojj 2017-05-25
		} else {
			// 余额退款
			this.updateWallet(order, orderRefunds, certificate, waitRefundDetailList);
		}
	
	}

	@Transactional(rollbackFor = Exception.class)
	private boolean updateWalletByThird(TradeOrder order, TradeOrderRefunds orderRefunds,
			TradeOrderRefundsCertificateVo certificate, List<TradeOrderItemDetail> waitRefundDetailList)
			throws Exception {
		// 构建余额支付（或添加交易记录）对象
		Message msg = new Message(TOPIC_BALANCE_PAY_TRADE, TAG_PAY_TRADE_MALL,
				buildBalanceThirdPayTrade(order, orderRefunds).getBytes(Charsets.UTF_8));
		// 发送事务消息
		TransactionSendResult sendResult = rocketMQTransactionProducer.send(msg, orderRefunds,
				new LocalTransactionExecuter() {

					@Override
					public LocalTransactionState executeLocalTransactionBranch(Message msg, Object object) {
						try {
							// 更新订单信息
							updateOrderInfo(order, orderRefunds, certificate, waitRefundDetailList);
						} catch (Exception e) {
							logger.error("执行同意退款操作异常", e);
							return LocalTransactionState.ROLLBACK_MESSAGE;
						}
						return LocalTransactionState.COMMIT_MESSAGE;
					}
				}, new TransactionCheckListener() {

					@Override
					public LocalTransactionState checkLocalTransactionState(MessageExt msg) {
						return LocalTransactionState.COMMIT_MESSAGE;
					}
				});
		return RocketMqResult.returnResult(sendResult);
	}

	/**
	 * @desc 执行退款更新云钱包余额
	 */
	private boolean updateWallet(TradeOrder order, TradeOrderRefunds orderRefunds,
			TradeOrderRefundsCertificateVo certificate, List<TradeOrderItemDetail> waitRefundDetailList)
			throws Exception {

		orderRefunds.setRefundsStatus(RefundsStatusEnum.SELLER_REFUNDING);
		// 构建余额支付（或添加交易记录）对象
		Message msg = new Message(TOPIC_BALANCE_PAY_TRADE, TAG_PAY_TRADE_MALL,
				buildBalancePayTrade(order, orderRefunds).getBytes(Charsets.UTF_8));
		// 发送事务消息
		TransactionSendResult sendResult = rocketMQTransactionProducer.send(msg, orderRefunds,
				new LocalTransactionExecuter() {

					@Override
					public LocalTransactionState executeLocalTransactionBranch(Message msg, Object object) {
						try {
							// 更新订单信息
							updateOrderInfo(order, orderRefunds, certificate, waitRefundDetailList);
						} catch (Exception e) {
							logger.error("执行同意退款操作异常", e);
							return LocalTransactionState.ROLLBACK_MESSAGE;
						}
						return LocalTransactionState.COMMIT_MESSAGE;
					}
				}, new TransactionCheckListener() {

					@Override
					public LocalTransactionState checkLocalTransactionState(MessageExt msg) {
						return LocalTransactionState.COMMIT_MESSAGE;
					}
				});
		return RocketMqResult.returnResult(sendResult);

	}

	/**
	 * @Description: 构建云钱包调用消息
	 * @param order 订单信息
	 * @param orderRefunds 退款单信息
	 * @return
	 * @throws Exception
	 * @author zengjizu
	 * @date 2016年10月11日
	 */
	private String buildBalanceThirdPayTrade(TradeOrder order, TradeOrderRefunds orderRefunds) throws Exception {

		BigDecimal amount = orderRefunds.getTotalAmount();

		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(amount);
		payTradeVo.setIncomeUserId(orderRefunds.getUserId());
		payTradeVo.setPayUserId(storeInfoService.getBossIdByStoreId(orderRefunds.getStoreId()));
		payTradeVo.setTradeNum(orderRefunds.getTradeNum());
		payTradeVo.setTitle("订单退款，退款交易号：" + orderRefunds.getRefundNo());

		payTradeVo.setBusinessType(BusinessTypeEnum.AGREEN_REFUND);
		payTradeVo.setServiceFkId(orderRefunds.getId());
		payTradeVo.setServiceNo(orderRefunds.getOrderNo());
		payTradeVo.setRemark("关联订单号：" + orderRefunds.getOrderNo());
		// 优惠额退款 判断是否有优惠劵
		ActivityBelongType activityResource = tradeOrderActivityService.findActivityType(order);
		if (activityResource == ActivityBelongType.OPERATOR || activityResource == ActivityBelongType.AGENT
				&& (orderRefunds.getTotalPreferentialPrice().compareTo(BigDecimal.ZERO) > 0)) {
			payTradeVo.setPrefeAmount(orderRefunds.getTotalPreferentialPrice());
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
		}
		// 接受返回消息的tag
		payTradeVo.setTag(null);
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.fromObject(payTradeVo).toString();
	}

	@Transactional(rollbackFor = Exception.class)
	private void updateOrderInfo(TradeOrder order, TradeOrderRefunds orderRefunds,
			TradeOrderRefundsCertificateVo certificate, List<TradeOrderItemDetail> waitRefundDetailList)
			throws Exception {

		List<String> rpcIdList = new ArrayList<String>();
		try {
			// 保存退款单
			tradeOrderRefundsMapper.insertSelective(orderRefunds);
			// 批量保存退款单项
			tradeOrderRefundsItemMapper.insert(orderRefunds.getTradeOrderRefundsItem());
			
			List<TradeOrderRefundsItemDetail> tradeOrderRefundsItemDetailList = Lists.newArrayList();
			TradeOrderRefundsItemDetail tradeOrderRefundsItemDetail = null;
			for (TradeOrderItemDetail detail : waitRefundDetailList) {
				tradeOrderRefundsItemDetail = new TradeOrderRefundsItemDetail();
				tradeOrderRefundsItemDetail.setId(UuidUtils.getUuid());
				tradeOrderRefundsItemDetail.setOrderItemDetailId(detail.getId());
				tradeOrderRefundsItemDetail.setRefundItemId(orderRefunds.getTradeOrderRefundsItem().get(0).getId());
				tradeOrderRefundsItemDetailList.add(tradeOrderRefundsItemDetail); 
			}
			//保存退款单明细表
			tradeOrderRefundsItemDetailMapper.batchAdd(tradeOrderRefundsItemDetailList);
			// 保存退款凭证
			tradeOrderRefundsCertificateService.addCertificate(certificate);

			// 更新到店消费的退款单信息
			this.updateStoreConsumeRefunds(order, orderRefunds, waitRefundDetailList);

			// 特惠活动释放限购数量
			for (TradeOrderRefundsItem refundsItem : orderRefunds.getTradeOrderRefundsItem()) {
				Map<String, Object> params = Maps.newHashMap();
				params.put("orderId", orderRefunds.getOrderId());
				params.put("storeSkuId", refundsItem.getStoreSkuId());
				activitySaleRecordService.updateDisabledByOrderId(params);
			}
			// 回收库存
			stockOperateService.recycleStockByRefund(order, orderRefunds, rpcIdList);
			// 发消息给ERP生成库存单据 added by maojj
			
			// 发送短信
			tradeMessageService.sendSmsByAgreePay(orderRefunds, order.getPayWay());
		} catch (Exception e) {
			rollbackMQProducer.sendStockRollbackMsg(rpcIdList);
			throw e;
		}
	}

	@Transactional(rollbackFor = Exception.class)
	private void updateStoreConsumeRefunds(TradeOrder order, TradeOrderRefunds orderRefunds,
			List<TradeOrderItemDetail> waitRefundDetailList) throws Exception {

		List<String> refundDetailIds = Lists.newArrayList();
		for (TradeOrderItemDetail tradeOrderItemDetail : waitRefundDetailList) {
			// 更新消费码状态
			int result = tradeOrderItemDetailMapper.updateStatusWithRefundById(tradeOrderItemDetail.getId());
			if (result < 1) {
				throw new Exception("消费码状态误");
			}
			refundDetailIds.add(tradeOrderItemDetail.getId());
		}
		List<String> ids = Lists.newArrayList();

		for (TradeOrderRefundsItem refundsItem : orderRefunds.getTradeOrderRefundsItem()) {
			// 判断订单项是否全部完成了
			List<TradeOrderItemDetail> detailList = tradeOrderItemDetailMapper
					.selectByOrderItemId(refundsItem.getOrderItemId());
			boolean isAllChange = true;
			for (TradeOrderItemDetail tradeOrderItemDetail : detailList) {
				if (tradeOrderItemDetail.getStatus() == ConsumeStatusEnum.noConsume
						&& !refundDetailIds.contains(tradeOrderItemDetail.getId())) {
					isAllChange = false;
				}
			}
			if (isAllChange) {
				// 更新订单项状态为已完成
				TradeOrderItem tradeOrderItem = tradeOrderItemMapper.selectOrderItemById(refundsItem.getOrderItemId());
				tradeOrderItem.setIsComplete(OrderComplete.YES);
				ids.add(tradeOrderItem.getId());
			}
			detailList = null;
		}

		if (ids.size() > 0) {
			tradeOrderItemMapper.updateCompleteById(ids);
		}

		// 更新订单状态
		List<TradeOrderItemDetail> detailList = tradeOrderItemDetailMapper
				.selectByOrderItemDetailByOrderId(orderRefunds.getOrderId());

		// 是否有过期的消费码
		boolean isHasExpired = false;
		// 是否有未消费的
		boolean isHasWaitConsume = false;
		// 是否有已经消费的
		boolean isHasConsumed = false;
		if (CollectionUtils.isNotEmpty(detailList)) {
			for (TradeOrderItemDetail tradeOrderItemDetail : detailList) {
				if (refundDetailIds.contains(tradeOrderItemDetail.getId())) {
					//如果是这次退款的消费码直接继续判断
					continue;
				}
				if (tradeOrderItemDetail.getStatus() == ConsumeStatusEnum.expired) {
					isHasExpired = true;
				}
				if (tradeOrderItemDetail.getStatus() == ConsumeStatusEnum.noConsume) {
					isHasWaitConsume = true;
				}
				if (tradeOrderItemDetail.getStatus() == ConsumeStatusEnum.consumed) {
					isHasConsumed = true;
				}
			}
		}

		if (isHasExpired) {
			//如果已经有过期的就将消费码状态改为已过期
			order.setConsumerCodeStatus(ConsumerCodeStatusEnum.EXPIRED);
		} else {
			if (isHasWaitConsume) {
				//有待消费的就改为待消费
				order.setConsumerCodeStatus(ConsumerCodeStatusEnum.WAIT_CONSUME);
			} else {
				if (isHasConsumed) {
					// 变成已经消费
					order.setConsumerCodeStatus(ConsumerCodeStatusEnum.WAIT_EVALUATE);
				} else {
					//全部退款
					order.setConsumerCodeStatus(ConsumerCodeStatusEnum.REFUNDED);
				}
			}
		}
		tradeOrderMapper.updateByPrimaryKeySelective(order);
	}

	//begin V1.2.0 add by zengjz 20161128
	@Override
	public List<TradeOrderItemDetail> findRefundTradeOrderItemDetailList(String refundItemId) {
		
		
		return tradeOrderItemDetailMapper.findRefundTradeOrderItemDetailList(refundItemId);
	}
	//end V1.2.0 add by zengjz 20161128
}
