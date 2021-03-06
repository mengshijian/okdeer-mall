
package com.okdeer.mall.order.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.rocketmq.client.producer.LocalTransactionExecuter;
import com.alibaba.rocketmq.client.producer.LocalTransactionState;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.client.producer.SendStatus;
import com.alibaba.rocketmq.client.producer.TransactionCheckListener;
import com.alibaba.rocketmq.client.producer.TransactionSendResult;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.okdeer.api.pay.enums.ApplicationEnum;
import com.okdeer.api.pay.enums.BusinessTypeEnum;
import com.okdeer.api.pay.enums.PayTradeServiceTypeEnum;
import com.okdeer.api.pay.enums.PayTradeTypeEnum;
import com.okdeer.api.pay.enums.RefundTypeEnum;
import com.okdeer.api.pay.enums.SystemEnum;
import com.okdeer.api.pay.pay.dto.PayRefundDto;
import com.okdeer.api.pay.pay.dto.PayReqestDto;
import com.okdeer.api.pay.service.IPayServiceApi;
import com.okdeer.api.pay.tradeLog.dto.BalancePayTradeDto;
import com.okdeer.archive.store.service.StoreInfoServiceApi;
import com.okdeer.base.common.enums.WhetherEnum;
import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.base.framework.mq.RocketMQProducer;
import com.okdeer.base.framework.mq.RocketMQTransactionProducer;
import com.okdeer.base.framework.mq.RocketMqResult;
import com.okdeer.base.framework.mq.message.MQMessage;
import com.okdeer.mall.activity.coupons.service.ActivityCollectCouponsService;
import com.okdeer.mall.activity.coupons.service.ActivityCouponsService;
import com.okdeer.mall.activity.discount.service.ActivityDiscountService;
import com.okdeer.mall.common.utils.TradeNumUtil;
import com.okdeer.mall.order.bo.PayInfo;
import com.okdeer.mall.order.bo.PayTradeExt;
import com.okdeer.mall.order.constant.mq.PayMessageConstant;
import com.okdeer.mall.order.dto.PayInfoParamDto;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.entity.TradeOrderItem;
import com.okdeer.mall.order.entity.TradeOrderPay;
import com.okdeer.mall.order.entity.TradeOrderRefundsItem;
import com.okdeer.mall.order.enums.OrderComplete;
import com.okdeer.mall.order.enums.OrderStatusEnum;
import com.okdeer.mall.order.enums.OrderTypeEnum;
import com.okdeer.mall.order.enums.PayTypeEnum;
import com.okdeer.mall.order.enums.PayWayEnum;
import com.okdeer.mall.order.mapper.TradeOrderItemMapper;
import com.okdeer.mall.order.mapper.TradeOrderMapper;
import com.okdeer.mall.order.mapper.TradeOrderPayMapper;
import com.okdeer.mall.order.mapper.TradeOrderRefundsItemMapper;
import com.okdeer.mall.order.service.TradeOrderActivityService;
import com.okdeer.mall.order.service.TradeOrderPayService;

/**
 * ClassName: TradeOrderPayServiceImpl 
 * @Description: 支付想关的调用
 * @author zengjizu
 * @date 2016年11月18日
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *     v1.2.0            2016-11-18         zengjz           增加服务订单确认调用云钱包方法、提取除共用的设置优惠信息的方法
 */
@Service
public class TradeOrderPayServiceImpl implements TradeOrderPayService {

	public static final Logger logger = LoggerFactory.getLogger(TradeOrderPayServiceImpl.class);

	/**
	 * 友门鹿云钱包账户
	 */
	@Value("${yscWalletAccount}")
	private String yscWalletAccount;

	@Resource
	private TradeOrderPayMapper tradeOrderPayMapper;

	@Resource
	private TradeOrderMapper tradeOrderMapper;

	@Resource
	private TradeOrderItemMapper tradeOrderItemMapper;

	@Resource
	private TradeOrderRefundsItemMapper tradeOrderRefundsItemMapper;

	@Reference(version = "1.0.0", check = false)
	private IPayServiceApi payServiceApi;

	@Resource
	private RocketMQTransactionProducer rocketMQTransactionProducer;

	@Resource
	private RocketMQProducer rocketMQProducer;

	@Resource
	private ActivityDiscountService activityDiscountService;

	@Resource
	private ActivityCollectCouponsService activityCollectCouponsService;

	@Resource
	private TradeOrderActivityService tradeOrderActivityService;

	@Reference(version = "1.0.0", check = false)
	private StoreInfoServiceApi storeInfoService;

	@Resource
	private ActivityCouponsService activityCouponsService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void insertSelective(TradeOrderPay tradeOrderPay) throws ServiceException {

		tradeOrderPayMapper.insertSelective(tradeOrderPay);

	}

	@Override
	public TradeOrderPay selectByOrderId(String orderId) throws ServiceException {
		return tradeOrderPayMapper.selectByOrderId(orderId);
	}

	/**
	 * 
	 * @return
	 */
	private int updateOrderInfo(TradeOrder tradeOrder) {
		return tradeOrderMapper.updateOrderStatus(tradeOrder);
	}

	/**
	 * 取消订单付款
	 */
	@Override
	public boolean cancelOrderPay(TradeOrder tradeOrder) throws Exception {
		// 判断非在线支付
		if (tradeOrder.getPayWay() != PayWayEnum.PAY_ONLINE) {
			return true;
		}
		// 判断非取消中状态和拒绝中的状态则不需要退款
		if (OrderTypeEnum.SERVICE_STORE_ORDER != tradeOrder.getType()
				&& OrderStatusEnum.CANCELING != tradeOrder.getStatus()
				&& OrderStatusEnum.REFUSING != tradeOrder.getStatus()) {
			// 服务订单已经取消也需要退违约金
			return true;
		}

		TradeOrderPay orderPay = this.selectByOrderId(tradeOrder.getId());
		if (orderPay.getPayType() == com.okdeer.mall.order.enums.PayTypeEnum.WALLET) {
			String tradesPaymentJson = buildBalanceCancelPay(tradeOrder);
			Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
					tradesPaymentJson.getBytes(Charsets.UTF_8));
			// 发送消息
			SendResult sendResult = rocketMQProducer.send(msg);
			if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
				throw new Exception("取消订单余额支付发送消息失败");
			}
		} else if (tradeOrder.getType() == OrderTypeEnum.SERVICE_STORE_ORDER
				&& tradeOrder.getIsBreach() == WhetherEnum.whether
				&& (com.okdeer.mall.order.enums.PayTypeEnum.ALIPAY == orderPay.getPayType()
						|| com.okdeer.mall.order.enums.PayTypeEnum.WXPAY == orderPay.getPayType())) {
			// 如果是上门服务订单，并且违约了，还是第三方支付订单，需要赔偿违约金给商家
			// 构建支付违约金信息
			String tradesPaymentJson = buildBreachMoneyPay(tradeOrder, orderPay.getPayType());

			Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
					tradesPaymentJson.getBytes(Charsets.UTF_8));
			// 发送消息
			SendResult sendResult = rocketMQProducer.send(msg);
			if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
				throw new Exception("取消订单违约金收入发送消息失败");
			}
		}

		// Begin V2.5 added by maojj 2017-07-05
		if ((tradeOrder.getStatus() == OrderStatusEnum.REFUSED || tradeOrder.getStatus() == OrderStatusEnum.REFUSING)
				&& tradeOrder.getFare().compareTo(BigDecimal.ZERO) == 1) {
			String farePayJson = buildFarePayWithRefuse(tradeOrder);
			// 拒收不退运费，运费转入店铺可用
			Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
					farePayJson.getBytes(Charsets.UTF_8));
			SendResult sendResult = rocketMQProducer.send(msg);
			if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
				throw new Exception("确认订单付款发送消息失败");
			}
		}
		// End V2.5 added by maojj 2017-07-05

		// Begin V2.4 added by maojj 2017-05-20
		if (orderPay.getPayType() != com.okdeer.mall.order.enums.PayTypeEnum.WALLET) {
			PayRefundDto payRefundDto = buildPayRefundDto(tradeOrder);
			MQMessage msg = new MQMessage(PayMessageConstant.TOPIC_REFUND, (Serializable) payRefundDto);
			msg.setKey(tradeOrder.getId());
			// 发送消息
			SendResult sendResult = rocketMQProducer.sendMessage(msg);
			if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
				throw new Exception("取消订单第三方支付发送消息失败");
			}
		}
		// End V2.4 added by maojj 2017-05-20
		return true;
	}

	// Begin V2.4 added by maojj 2017-05-20
	private PayRefundDto buildPayRefundDto(TradeOrder order) {
		PayRefundDto payRefundDto = new PayRefundDto();
		if (order.getIsBreach() == WhetherEnum.whether) {
			// 如果订单需要支付违约金，则退款金额为：实付金额-收取的违约金
			payRefundDto.setTradeAmount(order.getActualAmount().subtract(order.getBreachMoney()));
		} else {
			payRefundDto.setTradeAmount(order.getActualAmount());
		}

		// Begin V2.5 added by maojj 2017-06-28
		if (order.getStatus() == OrderStatusEnum.REFUSED || order.getStatus() == OrderStatusEnum.REFUSING) {
			// 拒收的订单不退款运费 退款金额=实付金额-实付运费。实付运费=运费金额-实际运费优惠金额
			payRefundDto.setTradeAmount(
					payRefundDto.getTradeAmount().subtract(order.getFare().subtract(order.getRealFarePreferential())));
		}
		// End V2.5 added by maojj 2017-06-28
		payRefundDto.setServiceId(order.getId());
		payRefundDto.setServiceNo(order.getOrderNo());
		payRefundDto.setRemark(order.getOrderNo());
		payRefundDto.setRefundType(RefundTypeEnum.CANCEL_ORDER);
		payRefundDto.setTradeNum(order.getTradeNum());
		payRefundDto.setRefundNum(TradeNumUtil.getTradeNum());
		return payRefundDto;
	}
	// End V2.4 added by maojj 2017-05-20

	/**
	 * 构建取消订单支付对象
	 */
	private String buildBalanceCancelPay(TradeOrder order) throws Exception {
		// 平台优惠金额(不包括运费优惠)
		BigDecimal preferentialAmount = order.getPlatformPreferential().subtract(order.getStorePreferential())
				.subtract(order.getRealFarePreferential());
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		// Begin V1.2 modified by maojj 2016-11-09
		if (order.getIsBreach() == WhetherEnum.whether) {
			// 如果订单需要支付违约金，则退款金额为：实付金额-收取的违约金
			payTradeVo.setAmount(order.getActualAmount().subtract(order.getBreachMoney()));
			// 用于云钱包校验是否需要收取违约金
			payTradeVo.setCheckAmount(order.getActualAmount());
		} else {
			payTradeVo.setAmount(order.getActualAmount());
		}
		// End V1.2 modified by maojj 2016-11-09
		// Begin V2.5 added by maojj 2017-06-28
		if (order.getStatus() == OrderStatusEnum.REFUSED || order.getStatus() == OrderStatusEnum.REFUSING) {
			// 拒收的订单不退款运费 退款金额=实付金额-实付运费。实付运费=运费金额-实际运费优惠金额
			payTradeVo.setAmount(
					payTradeVo.getAmount().subtract(order.getFare().subtract(order.getRealFarePreferential())));
		}
		// End V2.5 added by maojj 2017-06-28
		payTradeVo.setIncomeUserId(order.getUserId());
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("取消订单(余额支付)，交易号：" + order.getTradeNum());

		if (order.getType() == OrderTypeEnum.SERVICE_STORE_ORDER) {
			payTradeVo.setBusinessType(BusinessTypeEnum.SERVICE_STORE_ORDER_CANCEL);
		} else {
			payTradeVo.setBusinessType(BusinessTypeEnum.CANCEL_ORDER);
		}
		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		// 支付人:友门鹿
		payTradeVo.setPayUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		// 优惠金额
		if (preferentialAmount != null && preferentialAmount.compareTo(BigDecimal.ZERO) >= 0) {
			// 优化活动发起人，比如代理商id或者运营商id
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
			payTradeVo.setPrefeAmount(preferentialAmount);
		}
		// 接受返回消息的tag
		payTradeVo.setTag(PayMessageConstant.TAG_PAY_RESULT_CANCEL);
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.toJSONString(payTradeVo);
	}

	/**
	 * @Description: 构建违约金消息
	 * @param order 订单信息
	 * @return
	 * @throws ServiceException
	 * @author zengjizu
	 * @date 2016年11月11日
	 */
	private String buildBreachMoneyPay(TradeOrder order, PayTypeEnum payType) throws ServiceException {
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(order.getBreachMoney());
		// 增加一个校验金额字段用于云钱包判断用户是否被全部扣除了实付金额
		payTradeVo.setCheckAmount(order.getActualAmount());
		payTradeVo.setExt(payType.getName());
		payTradeVo.setPayUserId(order.getUserId());
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("取消订单违约金收入[" + order.getTradeNum() + "]");
		payTradeVo.setBusinessType(BusinessTypeEnum.PAY_BREACH_FEE);
		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		// 接受返回消息的tag
		payTradeVo.setTag(null);
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.toJSONString(payTradeVo);
	}

	/**
	 * 确认订单付款
	 */
	@Override
	public boolean confirmOrderPay(TradeOrder tradeOrder) throws Exception {

		if (tradeOrder.getPayWay() != PayWayEnum.PAY_ONLINE) {
			return true;
		}
		// Begin 12205 add by zengj
		// 查询订单项信息
		List<TradeOrderItem> tradeOrderItemList = this.tradeOrderItemMapper.selectOrderItemListById(tradeOrder.getId());

		// 构建转可用支付对象json
		String payByUsableJson = buildBalanceConfirmPayByUsable(tradeOrder, tradeOrderItemList);
		// 如果转可用支付对象json不为空
		if (StringUtils.isNotBlank(payByUsableJson)) {
			logger.debug("转可用支付对象：{}", payByUsableJson);
			// 发送MQ消息
			Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
					payByUsableJson.getBytes(Charsets.UTF_8));
			SendResult sendResult = rocketMQProducer.send(msg);
			if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
				throw new Exception("确认订单付款发送消息失败");
			}
		}

		// 构建转冻结支付对象json
		String payByFreezeJson = buildBalanceConfirmPayByFreeze(tradeOrder, tradeOrderItemList);
		// 如果转冻结支付对象json不为空
		if (StringUtils.isNotBlank(payByFreezeJson)) {
			logger.debug("转冻结支付对象：{}", payByFreezeJson);
			// 发送MQ消息
			Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
					payByFreezeJson.getBytes(Charsets.UTF_8));

			// 发送消息
			SendResult sendResult = rocketMQProducer.send(msg);
			if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
				throw new Exception("确认订单付款发送消息失败");
			}
		}
		// End 12205 add by zengj
		return true;
	}

	/**
	 * 
	 * @Description: 构建确认收货支付对象-不可售后金额和配送费（直接转可用）
	 * V2.5版本，资金流发生改变。运费有两种方案，A方案（第三方配送），B方案（商家自送）
	 * @param order 订单对象
	 * @param tradeOrderItemList 订单项集合对象
	 * @return 支付对象
	 * @throws ServiceException   异常处理
	 * @author zengj
	 * @date 2016年8月6日
	 */
	private String buildBalanceConfirmPayByUsable(TradeOrder order, List<TradeOrderItem> tradeOrderItemList)
			throws Exception {
		// 交易可用金额。可用金额=实际支付运费+不可退订单项实付金额
		BigDecimal tradeAmount = order.getFare().subtract(order.getRealFarePreferential());
		// 平台优惠(不包括运费优惠)
		BigDecimal preferentialAmount = BigDecimal.valueOf(0.00);
		// 循环订单项列表
		if (!CollectionUtils.isEmpty(tradeOrderItemList)) {
			// 需要更新为已完成的订单项ID集合
			List<String> ordreItemIds = new ArrayList<String>();
			for (TradeOrderItem orderItem : tradeOrderItemList) {
				// 不可申请售后的商品金额和配送费直接转可用
				if (orderItem.getServiceAssurance() == null || orderItem.getServiceAssurance() == 0) {
					// 可用金额
					tradeAmount = tradeAmount.add(orderItem.getActualAmount());
					// 转可用订单项平台补贴
					preferentialAmount = preferentialAmount.add(orderItem.getPreferentialPrice())
							.subtract(orderItem.getStorePreferential());
					ordreItemIds.add(orderItem.getId());
				}
			}

			// 如果有需要更新为完成的订单项ID，更新订单项为已完成
			if (!CollectionUtils.isEmpty(ordreItemIds)) {
				this.tradeOrderItemMapper.updateCompleteById(ordreItemIds);
			}
		}
		// 支付交易扩展信息
		PayTradeExt payTradeExt = new PayTradeExt();
		payTradeExt.setCommissionRate(order.getCommisionRatio());
		// 设置运费支出
		payTradeExt.setFreight(order.getFare());
		payTradeExt.setFreightSubsidy(order.getRealFarePreferential());
		// 需要抽佣的总金额=总实付金额+总平台优惠金额-运费
		BigDecimal totalAmountInCommision = tradeAmount.add(preferentialAmount).add(order.getRealFarePreferential())
				.subtract(order.getFare());
		payTradeExt.setIsDeductFreight(true);
		// if (order.getDeliveryType() != 2){
		// // 不是商家自送，需要扣减运费
		// totalAmountInCommision = totalAmountInCommision.subtract(order.getFare());
		// }
		// 需要收取的佣金
		BigDecimal totalCommision = totalAmountInCommision.multiply(order.getCommisionRatio()).setScale(2,
				BigDecimal.ROUND_HALF_UP);
		if (order.getCommisionRatio().compareTo(BigDecimal.ZERO) == 1
				&& totalAmountInCommision.compareTo(BigDecimal.ZERO) == 1
				&& totalCommision.compareTo(BigDecimal.ZERO) == 0) {
			// 如果佣金比例>0,且需要收佣金额>0，当收佣金额*佣金比例四舍五入之后结果为0，则将需要收取的佣金金额设置为0.01元
			totalCommision = BigDecimal.valueOf(0.01);
		}
		payTradeExt.setCommission(totalCommision);
		// 如果订单金额为0且运费为0，则不用发消息
		// if (BigDecimal.ZERO.compareTo(tradeAmount) == 0 && BigDecimal.ZERO.compareTo(order.getFare()) == 0) {
		// return null;
		// }
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(tradeAmount);
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("确认收货(余额支付)，交易号：" + order.getTradeNum());
		payTradeVo.setBusinessType(BusinessTypeEnum.CONFIRM_ORDER_NOSERVICE);
		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		// 优惠金额
		if (preferentialAmount.compareTo(BigDecimal.ZERO) > 0) {
			// 优化活动发起人，比如代理商id或者运营商id
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
			payTradeVo.setPrefeAmount(preferentialAmount);
		}
		// 接受返回消息的tag
		payTradeVo.setTag(PayMessageConstant.TAG_PAY_RESULT_CONFIRM);
		payTradeVo.setExt(JsonMapper.nonDefaultMapper().toJson(payTradeExt));
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.toJSONString(payTradeVo);
	}

	/**
	 * @Description: 构建拒收运费支出资金流
	 * @param order
	 * @param tradeOrderItemList
	 * @return
	 * @throws Exception   
	 * @author maojj
	 * @date 2017年7月5日
	 */
	private String buildFarePayWithRefuse(TradeOrder order) throws Exception {
		// 交易可用金额。可用金额=实际支付运费
		BigDecimal tradeAmount = order.getFare().subtract(order.getRealFarePreferential());
		// 支付交易扩展信息
		PayTradeExt payTradeExt = new PayTradeExt();
		payTradeExt.setCommissionRate(order.getCommisionRatio());
		// 设置运费支出
		payTradeExt.setFreight(order.getFare());
		payTradeExt.setFreightSubsidy(order.getRealFarePreferential());
		// 需要扣减运费给平台 V2.6
		payTradeExt.setIsDeductFreight(true);
		payTradeExt.setCommission(BigDecimal.ZERO);
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(tradeAmount);
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("确认收货(余额支付)，交易号：" + order.getTradeNum());
		payTradeVo.setBusinessType(BusinessTypeEnum.CONFIRM_ORDER_NOSERVICE);
		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		payTradeVo.setExt(JsonMapper.nonDefaultMapper().toJson(payTradeExt));
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.toJSONString(payTradeVo);
	}

	/**
	 * 
	 * @Description: 构建确认收货支付对象-可退货的（转冻结）
	 * @param order 订单对象
	 * @param tradeOrderItemList 订单项集合对象
	 * @return 支付对象
	 * @throws ServiceException 处理异常   
	 * @author zengj
	 * @date 2016年8月6日
	 */
	private String buildBalanceConfirmPayByFreeze(TradeOrder order, List<TradeOrderItem> tradeOrderItemList)
			throws Exception {
		// 订单冻结总金额
		BigDecimal tradeAmount = BigDecimal.ZERO;
		// 平台优惠金额（不包括运费）
		BigDecimal preferentialAmount = BigDecimal.ZERO;
		// Begin 12205 add by zengj
		if (!CollectionUtils.isEmpty(tradeOrderItemList)) {
			for (TradeOrderItem orderItem : tradeOrderItemList) {
				// 可申请售后的商品金额转冻结
				if (orderItem.getServiceAssurance() != null && orderItem.getServiceAssurance() > 0) {
					tradeAmount = tradeAmount.add(orderItem.getActualAmount());
					preferentialAmount = preferentialAmount.add(orderItem.getPreferentialPrice())
							.subtract(orderItem.getStorePreferential());
				}
			}
		}
		// 如果订单金额为0，只能说明该订单所有商品都不可退货，所以直接转可用了
		if (BigDecimal.ZERO.compareTo(tradeAmount) == 0 && BigDecimal.ZERO.compareTo(preferentialAmount) == 0) {
			return null;
		}
		// End 12205 add by zengj
		PayTradeExt payTradeExt = new PayTradeExt();
		BigDecimal totalCommision = tradeAmount.add(preferentialAmount).multiply(order.getCommisionRatio()).setScale(2,
				BigDecimal.ROUND_HALF_UP);
		if (order.getCommisionRatio().compareTo(BigDecimal.ZERO) == 1
				&& tradeAmount.add(preferentialAmount).compareTo(BigDecimal.ZERO) == 1
				&& totalCommision.compareTo(BigDecimal.ZERO) == 0) {
			// 如果佣金比例>0,且需要收佣金额>0，当收佣金额*佣金比例四舍五入之后结果为0，则将需要收取的佣金金额设置为0.01元
			totalCommision = BigDecimal.valueOf(0.01);
		}
		payTradeExt.setCommission(totalCommision);
		payTradeExt.setCommissionRate(order.getCommisionRatio());
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(tradeAmount);
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("确认收货(余额支付)，交易号：" + order.getTradeNum());
		payTradeVo.setBusinessType(BusinessTypeEnum.CONFIRM_ORDER);
		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		// 优惠金额
		if (preferentialAmount != null && preferentialAmount.compareTo(BigDecimal.ZERO) > 0) {
			// 优化活动发起人，比如代理商id或者运营商id
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
			payTradeVo.setPrefeAmount(preferentialAmount);
		}
		payTradeVo.setRemark("无");
		// 接受返回消息的tag
		payTradeVo.setTag(PayMessageConstant.TAG_PAY_RESULT_CONFIRM);
		payTradeVo.setExt(JsonMapper.nonDefaultMapper().toJson(payTradeExt));
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.toJSONString(payTradeVo);
	}

	@Override
	public boolean updateBalanceByFinish(TradeOrder tradeOrder) throws Exception {
		if (tradeOrder.getPayWay() != PayWayEnum.PAY_ONLINE) {
			return true;
		}
		String msgStr = buildBalanceFinish(tradeOrder);
		// 为空说明没有金额需要解除冻结
		if (StringUtils.isBlank(msgStr)) {
			return true;
		}
		Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
				msgStr.getBytes(Charsets.UTF_8));
		// 发送消息
		SendResult sendResult = rocketMQProducer.send(msg);
		return sendResult.getSendStatus() == SendStatus.SEND_OK;
	}

	/**
	 * 构建确认收货支付对象
	 */
	private String buildBalanceFinish(TradeOrder order) throws Exception {
		// Begin add by zengj
		List<TradeOrderItem> orderItemList = this.tradeOrderItemMapper.selectOrderItemListById(order.getId());
		// End add by zengj
		// 总的可用金额--仅指用户实付金额
		BigDecimal totalAmount = BigDecimal.ZERO;
		// 总的退款金额
		BigDecimal refundAmount = BigDecimal.ZERO;
		// 平台优惠金额（不包括运费）
		BigDecimal preferentialAmount = BigDecimal.ZERO;
		// 需要更新为已完成的订单项ID集合
		List<String> orderItemIds = new ArrayList<String>();
		Map<String, String> tmpOrderItemMap = new HashMap<String, String>();
		// 循环订单项信息
		if (!CollectionUtils.isEmpty(orderItemList)) {
			for (TradeOrderItem orderItem : orderItemList) {
				// 判断订单项是否已完成，未完成则需要调用云钱包
				if (orderItem.getIsComplete() == null || orderItem.getIsComplete() == OrderComplete.NO) {
					totalAmount = totalAmount.add(orderItem.getActualAmount());
					// 平台优惠也需要转云钱包
					preferentialAmount = preferentialAmount
							.add(orderItem.getPreferentialPrice().subtract(orderItem.getStorePreferential()));
					tmpOrderItemMap.put(orderItem.getId(), orderItem.getId());
				}
			}
		}

		// 需要减掉退款的金额，退款金额会在退款操作的时候转占用
		List<TradeOrderRefundsItem> refundsItem = tradeOrderRefundsItemMapper.selectByOrderId(order.getId());
		// 退款项的平台优惠金额
		BigDecimal refundsItemFavour = BigDecimal.ZERO;
		for (TradeOrderRefundsItem item : refundsItem) {
			// 订单项退款优惠
			refundsItemFavour = item.getPreferentialPrice().subtract(item.getStorePreferential());
			// 店铺总可用金额=总金额-退款实付金额
			totalAmount = totalAmount.subtract(item.getAmount());
			// 总优惠金额=总平台优惠金额-退款的平台优惠金额
			preferentialAmount = preferentialAmount.subtract(refundsItemFavour);
			// 如果map中存在了该订单项ID，但是该订单项存在退款，该订单项不需要标记为已完成
			if (tmpOrderItemMap.containsKey(item.getOrderItemId())) {
				tmpOrderItemMap.remove(item.getOrderItemId());
			}
		}

		for (String key : tmpOrderItemMap.keySet()) {
			orderItemIds.add(key);
		}
		// 如果有需要更新为完成的订单项ID，更新订单项为已完成
		if (!CollectionUtils.isEmpty(orderItemIds)) {
			this.tradeOrderItemMapper.updateCompleteById(orderItemIds);
		}
		// 支付交易扩展信息
		PayTradeExt payTradeExt = new PayTradeExt();
		payTradeExt.setCommission(totalAmount.add(preferentialAmount).multiply(order.getCommisionRatio()).setScale(2,
				BigDecimal.ROUND_HALF_UP));
		payTradeExt.setCommissionRate(order.getCommisionRatio());
		payTradeExt.setRefundAmount(refundAmount);
		// 如果没有金额直接返回空
		if (BigDecimal.ZERO.compareTo(totalAmount) == 0 && BigDecimal.ZERO.compareTo(preferentialAmount) == 0) {
			return null;
		}
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(totalAmount);
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setTradeNum(order.getTradeNum());
		payTradeVo.setTitle("解冻余额，交易号：" + order.getTradeNum());
		payTradeVo.setBusinessType(BusinessTypeEnum.COMPLETE_ORDER);
		payTradeVo.setServiceFkId(order.getId());
		payTradeVo.setServiceNo(order.getOrderNo());
		// 优惠金额
		if (preferentialAmount != null && preferentialAmount.compareTo(BigDecimal.ZERO) > 0) {
			// 优化活动发起人，比如代理商id或者运营商id
			payTradeVo.setActivitier(tradeOrderActivityService.findActivityUserId(order));
			payTradeVo.setPrefeAmount(preferentialAmount);
		}
		// 接受返回消息的tag
		payTradeVo.setTag(null);
		payTradeVo.setExt(JsonMapper.nonDefaultMapper().toJson(payTradeExt));
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		return JSONObject.toJSONString(payTradeVo);
	}

	/**
	 * 判断是否有售后 
	 */
	@Override
	public boolean isServiceAssurance(TradeOrder order) {
		boolean noService = false;
		List<TradeOrderItem> items = order.getTradeOrderItem();
		if (items == null || Iterables.isEmpty(items)) {
			items = tradeOrderItemMapper.selectTradeOrderItem(order.getId());
		}
		for (TradeOrderItem item : items) {
			if (item.getServiceAssurance() != null && item.getServiceAssurance() > 0) {
				noService = true;
			}
		}
		return noService;
	}

	@Override
	public boolean wlletPay(String orderMoney, TradeOrder order) throws Exception {

		TradeOrderPay orderPay = new TradeOrderPay();

		orderPay.setId(UuidUtils.getUuid());
		orderPay.setCreateTime(new Date());
		orderPay.setOrderId(order.getId());
		orderPay.setPayAmount(order.getActualAmount());
		orderPay.setPayTime(new Date());
		orderPay.setPayType(com.okdeer.mall.order.enums.PayTypeEnum.WALLET);
		order.setTradeOrderPay(orderPay);
		order.setStatus(OrderStatusEnum.BUYER_PAYING);
		order.setCurrentStatus(OrderStatusEnum.UNPAID);

		BalancePayTradeDto payTrade = buildBalancePay(order);
		String json = JsonMapper.nonEmptyMapper().toJson(payTrade);
		logger.info("发送余额支付信息到云钱包:{}", json);

		Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
				json.getBytes(Charsets.UTF_8));

		// 发送事务消息
		TransactionSendResult sendResult = rocketMQTransactionProducer.send(msg, order, new LocalTransactionExecuter() {

			@Override
			public LocalTransactionState executeLocalTransactionBranch(Message msg, Object object) {
				int result = updateOrderInfo((TradeOrder) object);
				if (result < 1) {
					logger.error("订单状态不是待支付，支付失败！");
					return LocalTransactionState.ROLLBACK_MESSAGE;
				}
				return LocalTransactionState.COMMIT_MESSAGE;
			}
		}, new TransactionCheckListener() {

			public LocalTransactionState checkLocalTransactionState(MessageExt msg) {
				return LocalTransactionState.COMMIT_MESSAGE;
			}
		});
		return RocketMqResult.returnResult(sendResult);
	}

	/**
	 * 构建云钱包(余额)支付对象
	 */
	private BalancePayTradeDto buildBalancePay(TradeOrder order) throws Exception {
		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		setActiveAmount(order, payTradeVo);
		payTradeVo.setAmount(order.getActualAmount()); // 交易金额
		payTradeVo.setPayUserId(order.getUserId());// 用户id
		payTradeVo.setTradeNum(order.getTradeNum());// 交易号
		payTradeVo.setTitle("订单支付");// 标题
		if (order.getType() == OrderTypeEnum.STORE_CONSUME_ORDER) {
			payTradeVo.setBusinessType(BusinessTypeEnum.STORE_CONSUME_ORDER);
		} else {
			payTradeVo.setBusinessType(BusinessTypeEnum.ORDER_PAY);// 业务类型
		}
		payTradeVo.setTag(PayMessageConstant.TAG_PAY_RESULT_INSERT);// 接受返回消息的tag
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		payTradeVo.setServiceFkId(order.getId());// 服务单id
		payTradeVo.setServiceNo(order.getOrderNo());// 服务单号，例如订单号、退单号
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		payTradeVo.setStoreId(order.getStoreId());
		BigDecimal subsidies = calculPlatformPrefeAmount(order);
		if (subsidies.compareTo(BigDecimal.ZERO) > 0) {
			payTradeVo.setActivitier(yscWalletAccount);
			payTradeVo.setPrefeAmount(subsidies);
		}
		return payTradeVo;
	}

	// bigein add by zengjz 2016-11-18 增加服务订单确认调用云钱包方法
	@Override
	public void confirmStoreServiceOrderPay(TradeOrder tradeOrder) throws Exception {

		if (tradeOrder.getPayWay() != PayWayEnum.PAY_ONLINE) {
			return;
		}

		BalancePayTradeDto payTradeVo = new BalancePayTradeDto();
		payTradeVo.setAmount(tradeOrder.getActualAmount());
		payTradeVo.setIncomeUserId(storeInfoService.getBossIdByStoreId(tradeOrder.getStoreId()));
		payTradeVo.setTradeNum(tradeOrder.getTradeNum());
		payTradeVo.setTitle("订单确认服务完成[" + tradeOrder.getOrderNo() + "]");
		payTradeVo.setBusinessType(BusinessTypeEnum.CONFIRM_SERVICE_ORDER_FINSH);
		payTradeVo.setServiceFkId(tradeOrder.getId());
		payTradeVo.setServiceNo(tradeOrder.getOrderNo());
		// 接受返回消息的tag
		payTradeVo.setTag(null);
		// 设置优惠金额信息
		setActiveAmount(tradeOrder, payTradeVo);
		payTradeVo.setBatchNo(TradeNumUtil.getTradeNum());
		String sendJson = JSONObject.toJSONString(payTradeVo);
		// 发送MQ消息
		Message msg = new Message(PayMessageConstant.TOPIC_BALANCE_PAY_TRADE, PayMessageConstant.TAG_PAY_TRADE_MALL,
				sendJson.getBytes(Charsets.UTF_8));
		SendResult sendResult = rocketMQProducer.send(msg);
		if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
			throw new Exception("确认订单付款发送消息失败");
		}
	}
	// end add by zengjz 2016-11-18 增加服务订单确认调用云钱包方法

	/**
	 * @Description: 设置优惠金额
	 * @param order 订单信息
	 * @param payTradeVo 云钱包数据发送对象
	 * @author zengjizu
	 * @throws Exception 
	 * @date 2016年11月18日
	 */
	private void setActiveAmount(TradeOrder order, BalancePayTradeDto payTradeVo) throws Exception {
		// 优惠额退款 判断是否有优惠劵
		BigDecimal pice = BigDecimal.ZERO;
		if (order.getPlatformPreferential() != null && order.getPlatformPreferential().compareTo(BigDecimal.ZERO) > 0) {
			pice = pice.add(order.getPlatformPreferential());
		}
		if (order.getPinMoney() != null && order.getPinMoney().compareTo(BigDecimal.ZERO) > 0) {
			pice = pice.add(order.getPinMoney());
		}
		if (pice.compareTo(BigDecimal.ZERO) > 0) {
			payTradeVo.setPrefeAmount(pice);
			payTradeVo.setActivitier(yscWalletAccount);
		}
	}

	@Override
	public PayInfo getPayInfo(PayInfoParamDto payInfoParamDto) throws Exception {
		TradeOrder order = tradeOrderMapper.selectByPrimaryKey(payInfoParamDto.getOrderId());
		if (order.getStatus() != OrderStatusEnum.UNPAID) {
			throw new Exception("订单状态已经非待支付状态");
		}
		PayReqestDto payReqest = buildPayRequest(payInfoParamDto, order);
		payReqest.setExtParam(payInfoParamDto.getExtParam());
		logger.info("payReqest{}", payReqest);
		String result = payServiceApi.appPay(payReqest);
		logger.info("云钱包返回支付信息:{}", result);
		PayInfo payInfoDto = new PayInfo();
		payInfoDto.setOrderId(order.getId());
		payInfoDto.setPaymentType(payInfoParamDto.getPaymentType());
		payInfoDto.setData(result);
		return payInfoDto;
	}

	/**
	 * @Description: 构建云钱包请求参数（到店消费）
	 * @param payInfoParamDto
	 * @param order
	 * @return
	 * @throws ServiceException
	 * @author zengjizu
	 * @date 2017年1月4日
	 */
	private PayReqestDto buildPayRequest(PayInfoParamDto payInfoParamDto, TradeOrder order) throws Exception {
		// 设置订单信息
		PayReqestDto payReqest = new PayReqestDto();
		payReqest.setOpenid(payInfoParamDto.getOpenId());
		if ("1".equals(payInfoParamDto.getClientType())) {
			payReqest.setApplicationEnum(ApplicationEnum.WECHAT);
		} else {
			payReqest.setApplicationEnum(ApplicationEnum.CONVENIENCE_STORE);
		}

		BigDecimal subsidies = calculPlatformPrefeAmount(order);
		if (subsidies.compareTo(BigDecimal.ZERO) > 0) {
			payReqest.setActivitier(yscWalletAccount);
			payReqest.setPrefeAmount(subsidies);
		}
		// 买家ID
		payReqest.setUserId(order.getUserId());
		// 订单编号
		payReqest.setServiceNo(order.getOrderNo());
		// 交易号
		payReqest.setTradeNum(order.getTradeNum());
		// 交易名称
		payReqest.setTradeName("订单支付");
		// 交易金额
		payReqest.setTradeAmount(order.getActualAmount());
		// 用户端IP
		payReqest.setIp(payInfoParamDto.getIp());
		// 交易描述
		payReqest.setTradeDescription("订单支付");
		// 业务类型
		if (order.getType() == OrderTypeEnum.STORE_CONSUME_ORDER) {
			payReqest.setServiceType(PayTradeServiceTypeEnum.STORE_CONSUME_ORDER);
		} else {
			payReqest.setServiceType(PayTradeServiceTypeEnum.ORDER);
		}
		payReqest.setReceiver(storeInfoService.getBossIdByStoreId(order.getStoreId()));
		// 系统类型
		payReqest.setSystemEnum(SystemEnum.MALL);
		// 业务ID，如订单ID，服务ID
		payReqest.setServiceId(order.getId());
		payReqest.setStoreId(order.getStoreId());
		int paymentType = payInfoParamDto.getPaymentType();
		// 支付类型 0:云钱包,1:支付宝app支付,2:微信app支付, 6：微信公众号 7 微信h5 8:翼支付
		if (1 == paymentType) {
			payReqest.setTradeType(PayTradeTypeEnum.APP_ALIPAY);
		} else if (2 == paymentType) {
			payReqest.setTradeType(PayTradeTypeEnum.APP_WXPAY);
		} else if (6 == paymentType) {
			payReqest.setTradeType(PayTradeTypeEnum.WX_WXPAY);
		} else if (7 == paymentType) {
			payReqest.setTradeType(PayTradeTypeEnum.WAP_WXPAY);
		} else if (8 == paymentType) {
			payReqest.setTradeType(PayTradeTypeEnum.APP_BESTPAY);
		}
		return payReqest;
	}

	private BigDecimal calculPlatformPrefeAmount(TradeOrder order) {
		// 平台补贴
		BigDecimal subsidies = BigDecimal.ZERO;
		if (order.getPlatformPreferential() != null && order.getPlatformPreferential().compareTo(BigDecimal.ZERO) > 0) {
			subsidies.add(order.getPlatformPreferential());
		}
		if (order.getPinMoney() != null && order.getPinMoney().compareTo(BigDecimal.ZERO) > 0) {
			subsidies.add(order.getPinMoney());
		}
		return subsidies;
	}
}