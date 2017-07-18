package com.okdeer.mall.ele.subscriber;

import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.okdeer.archive.store.dto.StoreInfoDto;
import com.okdeer.base.framework.mq.annotation.RocketMQListener;
import com.okdeer.base.framework.mq.message.MQMessage;
import com.okdeer.mall.ele.service.ExpressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ClassName: ExpressChainStoreSubscriber
 *
 * @author wangf01
 * @Description: 第三方添加门店信息
 * @date 2017年6月23日
 * <p>
 * =================================================================================================
 * Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 */
@Service
public class ExpressChainStoreSubscriber {

    @Autowired
    private ExpressService expressService;

    /**
     * 日志log
     */
    private static final Logger logger = LoggerFactory.getLogger(ExpressChainStoreSubscriber.class);

    @RocketMQListener(topic = "topic_express_chain_store", tag = "*")
    public ConsumeConcurrentlyStatus subscribeMessage(MQMessage mqMessage) {
        String tag = mqMessage.getTags();
        if (tag == null || tag.equals("")) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        try {
            StoreInfoDto paramDto = (StoreInfoDto) mqMessage.getContent();
            expressService.saveChainStore(paramDto);
        } catch (Exception e) {
            logger.error("第三方添加门店信息异常", e);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
