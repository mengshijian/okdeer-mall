package com.okdeer.mall.ele.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.okdeer.base.common.utils.mapper.JsonMapper;
import com.okdeer.mall.base.BaseServiceTest;
import com.okdeer.mall.express.api.ExpressApi;
import com.okdeer.mall.express.dto.ExpressCarrierDto;
import com.okdeer.mall.express.dto.ExpressOrderInfoDto;
import com.okdeer.mall.express.dto.ResultMsgDto;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ExpressServiceImpl extends BaseServiceTest {

    @Autowired
    private ExpressService expressService;

    @Reference(version = "1.0.0", check = false)
    private ExpressApi expressApi;

    @Test
    public void expressPushTest() {
        try {
            //expressService.saveExpressOrder("8a94e7545cd93f77015cd940590d0003");
        } catch (Exception e) {
            System.out.println("推送订单异常" + e);
        }
    }

    @Test
    public void expressCarrierTest() {
        try {
            ResultMsgDto<ExpressCarrierDto> resultMsgDto = expressService.findExpressCarrier("XS100010017062600008");
            System.out.println(JsonMapper.nonDefaultMapper().toJson(resultMsgDto));
        } catch (Exception e) {
            System.out.println("获取骑手信息异常" + e);
        }
    }

    @Test
    public void expressOrderInfoTest() {
        try {
            ResultMsgDto<ExpressOrderInfoDto> resultMsgDto = expressApi.findExpressOrderInfo("XS100010017062600008");
            System.out.println(JsonMapper.nonDefaultMapper().toJson(resultMsgDto));
        } catch (Exception e) {
            System.out.println("获取第三方订单信息异常" + e);
        }
    }

    @Test
    public void expressCancelTest(){
        try {
            ResultMsgDto<String> resultMsgDto = expressService.cancelExpressOrder("XS100010017050400012");
            System.out.println(JsonMapper.nonDefaultMapper().toJson(resultMsgDto));
        } catch (Exception e) {
            System.out.println("取消第三方订单信息异常" + e);
        }
    }
}