package com.okdeer.mall.activity.serviceGoodsRecommend.job;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dangdang.ddframe.job.api.JobExecutionMultipleShardingContext;
import com.dangdang.ddframe.job.plugin.job.type.simple.AbstractSimpleElasticJob;
import com.okdeer.mall.activity.serviceGoodsRecommend.entity.ActivityServiceGoodsRecommend;
import com.okdeer.mall.activity.serviceGoodsRecommend.enums.ActivityServiceGoodsRecommendStatus;
import com.okdeer.mall.activity.serviceGoodsRecommend.service.ActivityServiceGoodsRecommendApi;
import com.okdeer.mall.common.utils.RobotUserUtil;

/**
 * @pr mall
 * @desc 修改代金券活动活动状态job
 * @author zhangkn
 * @date 2016年1月28日 下午1:59:59
 * @copyright ©2005-2020 yschome.com Inc. All rights reserved
 */
@Service
public class ActivityServiceGoodsRecommendJob extends AbstractSimpleElasticJob {

	private static final Logger log = LoggerFactory.getLogger(ActivityServiceGoodsRecommendJob.class);

	@Autowired
	private ActivityServiceGoodsRecommendApi recommendService;
	
	
	//本来放在service事务里面,后来改为单个活动为一个事务,所以把循环放在这里
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void process(JobExecutionMultipleShardingContext arg0) {
		try{
			log.info("服务商品推荐定时器开始");
			
			List<ActivityServiceGoodsRecommend> accList = recommendService.listByJob();
			if(accList != null && accList.size() > 0){
				
				List<String> listIdNoStart = new ArrayList<String>();
				List<String> listIdIng = new ArrayList<String>();
				
				for(ActivityServiceGoodsRecommend a : accList){
					//未开始的 
					if(a.getStatus() == ActivityServiceGoodsRecommendStatus.noStart.getValue()){
						listIdNoStart.add(a.getId());
					}
					//进行中的改为已结束的
					else if(a.getStatus() == ActivityServiceGoodsRecommendStatus.ing.getValue()){
						listIdIng.add(a.getId());
					}
				}
				
				String updateUserId = RobotUserUtil.getRobotUser().getId();
				Date updateTime = new Date();
				
				//改为进行中
				if(listIdNoStart != null && listIdNoStart.size() > 0){
					for(String id : listIdNoStart){
						try{
							recommendService.updateBatchStatus(id,  ActivityServiceGoodsRecommendStatus.ing.getValue(), updateUserId, updateTime);
						}catch(Exception e){
							log.error("服务商品推荐"+id+"job异常 改为进行中:",e);
						}
						
					}
				}
				//改为已经结束
				if(listIdIng != null && listIdIng.size() > 0){
					for(String id : listIdIng){
						try{
							recommendService.updateBatchStatus(id,  ActivityServiceGoodsRecommendStatus.end.getValue(), updateUserId, updateTime);
						}catch(Exception e){
							log.error("服务商品推荐"+id+"job异常 改为已经结束:",e);
						}
					}
				}
			}
			log.info("服务商品推荐定时器结束");
		}catch(Exception e){
			log.error("服务商品推荐job异常",e);
		}
		
	}
}
