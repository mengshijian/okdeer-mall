/**
 * @Title: AlipayStatusSubscriber.java
 * @Package com.okdeer.mall.trade.order.pay
 * @Description: (用一句话描述该文件做什么)
 * @author A18ccms A18ccms_gmail_com
 * @date 2016年3月30日 下午7:39:54
 * @version V1.0
 */

package com.okdeer.mall.order.pay;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.client.producer.SendStatus;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.okdeer.api.pay.luzgorder.dto.PayLuzgOrderDto;
import com.okdeer.api.pay.pay.dto.PayRefundDto;
import com.okdeer.api.pay.pay.dto.PayResponseDto;
import com.okdeer.api.pay.service.PayLuzgOrderApi;
import com.okdeer.archive.store.entity.StoreMemberRelation;
import com.okdeer.archive.store.service.IStoreMemberRelationServiceApi;
import com.okdeer.base.common.utils.StringUtils;
import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.base.framework.mq.AbstractRocketMQSubscriber;
import com.okdeer.base.framework.mq.RocketMQProducer;
import com.okdeer.base.framework.mq.message.MQMessage;
import com.okdeer.mall.order.builder.TradeOrderPayBuilder;
import com.okdeer.mall.order.constant.mq.OrderMessageConstant;
import com.okdeer.mall.order.constant.mq.PayMessageConstant;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.entity.TradeOrderGroupRelation;
import com.okdeer.mall.order.enums.OrderStatusEnum;
import com.okdeer.mall.order.enums.OrderTypeEnum;
import com.okdeer.mall.order.enums.PayTypeEnum;
import com.okdeer.mall.order.enums.SendMsgType;
import com.okdeer.mall.order.mapper.TradeOrderGroupRelationMapper;
import com.okdeer.mall.order.mapper.TradeOrderMapper;
import com.okdeer.mall.order.mq.TradeOrderSubScriberHandler;
import com.okdeer.mall.order.pay.callback.AbstractPayResultHandler;
import com.okdeer.mall.order.pay.callback.PayResultHandlerFactory;
import com.okdeer.mall.order.service.TradeMessageServiceApi;
import com.okdeer.mall.order.vo.SendMsgParamVo;
import com.okdeer.mall.util.RedisLock;

/**
 * @ClassName: AlipayStatusSubscriber
 * @Description: 订单状态写入消息, 第三方支付
 * @author yangq
 * @date 2016年3月30日 下午7:39:54
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *     重构4.1          2016年7月16日                              zengj			服务店订单支付回调
 *     12002           2016年8月5日                                zengj			增加服务店订单下单成功增加销量
 *     重构4.1          2016年8月16日                              zengj			支付成功回调判断订单状态是不是买家支付中
 *     重构4.1          2016年8月24日                              maojj			支付成功，如果订单是到店自提，则生成提货码
 *     重构4.1          2016年9月22日                              zhaoqc         从V1.0.0移动代码 
 *     V1.1.0          2016年9月29日                             zhaoqc         新增到店消费订单处理         
 *     V1.1.0			2016-10-15			   wushp				邀请注册首单返券        
 */
@Service
public class ThirdStatusSubscriber extends AbstractRocketMQSubscriber
		implements PayMessageConstant, OrderMessageConstant {

	private static final Logger logger = LoggerFactory.getLogger(ThirdStatusSubscriber.class);

	@Resource
	private PayResultHandlerFactory payResultHandlerFactory;

	@Resource
	private TradeOrderMapper tradeOrderMapper;

	@Resource
	private TradeOrderSubScriberHandler tradeOrderSubScriberHandler;

	@Reference(version = "1.0.0", check = false)
	private PayLuzgOrderApi payLuzgOrderApi;

	@Reference(version = "1.0.0", check = false)
	private TradeMessageServiceApi tradeMessageServiceApi;

	@Reference(version = "1.0.0", check = false)
	private IStoreMemberRelationServiceApi storeMemberRelationApi;

	@Resource
	private TradeOrderGroupRelationMapper tradeOrderGroupRelationMapper;

	@Resource
	private RedisLock redisLock;

	@Resource
	private TradeOrderPayBuilder tradeOrderPayBuilder;

	@Autowired
	private RocketMQProducer rocketMQProducer;

	@Override
	public String getTopic() {
		return TOPIC_PAY;
	}

	@Override
	public String getTags() {
		return TAG_ORDER + JOINT + TAG_POST_ORDER + JOINT + TAG_LUZG_ORDER;
	}

	@Override
	public ConsumeConcurrentlyStatus subscribeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
		String tradeNum = null;
		TradeOrder tradeOrder = null;
		AbstractPayResultHandler handler = null;
		String lockKey = null;
		try {
			String msg = new String(msgs.get(0).getBody(), Charsets.UTF_8);
			logger.info("订单支付状态消息:{}", msg);
			PayResponseDto respDto = JsonMapper.nonEmptyMapper().fromJson(msg, PayResponseDto.class);
			tradeNum = respDto.getTradeNum();
			if (StringUtils.isEmpty(tradeNum)) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}

			// 如果是鹿掌柜的tag
			if (msgs.get(0).getTags().indexOf(TAG_LUZG_ORDER) >= 0) {
				handlerLzg(respDto);
			} else {
				tradeOrder = tradeOrderMapper.selectByParamsTrade(tradeNum);
				// 判断订单是否已经取消
				boolean result = checkOrderIsCanceled(tradeOrder);
				if (result) {
					return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
				}
				// Begin V2.6.3 added by maojj 2017-10-27
				if (tradeOrder.getType() == OrderTypeEnum.GROUP_ORDER) {
					// 如果是团购订单，查询订单团单关联关系
					TradeOrderGroupRelation orderGroupRel = tradeOrderGroupRelationMapper
							.findByOrderId(tradeOrder.getId());
					if (orderGroupRel != null) {
						// 如果是拼团，处理团单时，要加锁
						lockKey = String.format("GROUP:%s", orderGroupRel.getGroupOrderId());
					}
				}
				// End V2.6.3 added by maojj 2017-10-27
				handler = payResultHandlerFactory.getByOrder(tradeOrder);
				if (StringUtils.isEmpty(lockKey) || redisLock.tryLock(lockKey, 10)) {
					handler.handler(tradeOrder, respDto);
				} else {
					// 如果要获取锁，但是没有成功取得锁，则延时消费
					return ConsumeConcurrentlyStatus.RECONSUME_LATER;
				}
			}
		} catch (Exception e) {
			logger.error("订单支付状态消息处理失败", e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		} finally {
			if (StringUtils.isNotEmpty(lockKey)) {
				redisLock.unLock(lockKey);
			}
		}

		// end add by wushp 20161015
		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}

	/**
	 * @Description: 校验订单是否取消
	 * @param tradeOrder
	 * @author zengjizu
	 * @throws Exception 
	 * @date 2017年12月8日
	 */
	private boolean checkOrderIsCanceled(TradeOrder tradeOrder) throws Exception {
		if (tradeOrder.getStatus() == OrderStatusEnum.CANCELED) {
			TradeOrder updateTradeOrder = new TradeOrder();
			updateTradeOrder.setId(tradeOrder.getId());
			updateTradeOrder.setStatus(OrderStatusEnum.CANCELING);
			tradeOrderMapper.updateOrderStatus(updateTradeOrder);
			
			PayRefundDto payRefundDto = tradeOrderPayBuilder.buildPayRefundDto(tradeOrder);
			MQMessage<PayRefundDto> msg = new MQMessage<>(PayMessageConstant.TOPIC_REFUND, payRefundDto);
			msg.setKey(tradeOrder.getId());
			// 发送消息
			SendResult sendResult;
			try {
				sendResult = rocketMQProducer.sendMessage(msg);
				if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
					throw new Exception("取消订单第三方支付发送消息失败");
				}
			} catch (Exception e) {
				throw e;
			}
			return true;
		}
		return false;
	}

	/**
	 * @Description: 处理鹿掌柜的逻辑
	 * @param respDto
	 * @throws Exception
	 * @author zhangkn
	 * @date 2017年8月16日
	 */
	private void handlerLzg(PayResponseDto respDto) throws Exception {
		logger.info("鹿掌柜支付:{}", respDto);
		PayLuzgOrderDto lzgOrderDto = payLuzgOrderApi.findByTradeNum(respDto.getTradeNum());
		if (lzgOrderDto != null) {
			SendMsgParamVo sendMsgParamVo = new SendMsgParamVo();
			// 鹿掌柜的金额
			sendMsgParamVo.setLzgAmount(respDto.getTradeAmount());
			// 收款人用户id
			sendMsgParamVo.setUserId(lzgOrderDto.getPayeeUserId());
			// 订单id
			sendMsgParamVo.setOrderId(lzgOrderDto.getId());
			sendMsgParamVo.setSendMsgType(SendMsgType.lzgGathering.ordinal());
			sendMsgParamVo.setPayType(PayTypeEnum.enumValueOf(lzgOrderDto.getPayType().ordinal()));

			// 通过userId得到店铺id
			Map<String, Object> map = Maps.newHashMap();
			map.put("sysUserId", sendMsgParamVo.getUserId());
			// 0总账号3店长
			map.put("memberTypeList", Arrays.asList("0,3".split(",")));
			// 0否 1 是
			map.put("isPickUp", 1);
			List<StoreMemberRelation> smrList = storeMemberRelationApi.findByParams(map);
			if (CollectionUtils.isNotEmpty(smrList)) {
				sendMsgParamVo.setStoreId(smrList.get(0).getStoreId());
				tradeMessageServiceApi.sendSellerAppMessage(sendMsgParamVo, SendMsgType.lzgGathering);
			}
		}
	}
}