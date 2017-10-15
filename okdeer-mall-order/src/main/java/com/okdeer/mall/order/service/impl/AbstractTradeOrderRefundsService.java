
package com.okdeer.mall.order.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import com.okdeer.archive.store.enums.ResultCodeEnum;
import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.common.exception.MallApiException;
import com.okdeer.mall.common.dto.Response;
import com.okdeer.mall.order.bo.TradeOrderRefundContextBo;
import com.okdeer.mall.order.dto.TradeOrderApplyRefundParamDto;
import com.okdeer.mall.order.dto.TradeOrderApplyRefundResultDto;
import com.okdeer.mall.order.entity.TradeOrder;
import com.okdeer.mall.order.entity.TradeOrderRefunds;
import com.okdeer.mall.order.mapper.TradeOrderRefundsMapper;
import com.okdeer.mall.order.service.TradeOrderRefundProcessService;
import com.okdeer.mall.order.service.TradeOrderRefundsService;
import com.okdeer.mall.order.service.TradeOrderService;

public abstract class AbstractTradeOrderRefundsService implements TradeOrderRefundsService {

	@Autowired
	protected TradeOrderService tradeOrderService;

	@Autowired
	protected TradeOrderRefundsMapper tradeOrderRefundsMapper;

	@Autowired
	protected TradeOrderRefundBuildFactory tradeOrderRefundBuildFactory;

	@Override
	public Response<TradeOrderApplyRefundResultDto> processApplyRefund(
			TradeOrderApplyRefundParamDto tradeOrderApplyRefundParamDto) throws MallApiException {
		Response<TradeOrderApplyRefundResultDto> response = new Response<>();
		try {
			TradeOrderApplyRefundResultDto tradeOrderApplyRefundResultDto = new TradeOrderApplyRefundResultDto();
			TradeOrder tradeOrder = tradeOrderApplyRefundParamDto.getTradeOrder();
			if (tradeOrder == null) {
				tradeOrder = tradeOrderService.selectById(tradeOrderApplyRefundParamDto.getOrderId());
			}
			// 订单不存在
			if (tradeOrder == null) {
				response.setResult(ResultCodeEnum.ILLEGAL_PARAM);
				return response;
			}
			TradeOrderRefundContextBo tradeOrderRefundContext = new TradeOrderRefundContextBo();
			tradeOrderRefundContext.setTradeOrder(tradeOrder);

			TradeOrderRefundProcessService tradeOrderRefundProcessService = tradeOrderRefundBuildFactory
					.getTradeOrderRefundProcessService(tradeOrder.getType());

			boolean checkResult = tradeOrderRefundProcessService.checkApplyRefund(tradeOrderApplyRefundParamDto,
					response, tradeOrderRefundContext);
			// 校验不通过，则返回错误信息
			if (!checkResult) {
				return response;
			}
			// 创建退款单
			tradeOrderRefundProcessService.createRefundInfo(tradeOrderApplyRefundParamDto, tradeOrderRefundContext);
			Assert.notNull(tradeOrderRefundContext.getTradeOrderRefunds());
			Assert.hasText(tradeOrderRefundContext.getTradeOrderRefunds().getId(), "退款单id不能为空");
			tradeOrderApplyRefundResultDto.setRefundId(tradeOrderRefundContext.getTradeOrderRefunds().getId());
			response.setData(tradeOrderApplyRefundResultDto);
			response.setResult(ResultCodeEnum.SUCCESS);
			return response;
		} catch (ServiceException e) {
			throw new MallApiException(e);
		}
	}

	protected TradeOrderRefundContextBo createTradeOrderRefundContext(String refundId) throws ServiceException {
		TradeOrderRefundContextBo tradeOrderRefundContextBo = new TradeOrderRefundContextBo();
		TradeOrderRefunds tradeOrderRefunds = tradeOrderRefundsMapper.findInfoById(refundId);
		Assert.notNull(tradeOrderRefunds);
		tradeOrderRefundContextBo.setTradeOrderRefunds(tradeOrderRefunds);
		TradeOrder tradeOrder = tradeOrderService.selectById(tradeOrderRefunds.getOrderId());
		Assert.notNull(tradeOrder);
		tradeOrderRefundContextBo.setTradeOrder(tradeOrder);
		return tradeOrderRefundContextBo;
	}
}
