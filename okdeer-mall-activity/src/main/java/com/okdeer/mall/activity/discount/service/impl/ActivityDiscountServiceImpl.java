package com.okdeer.mall.activity.discount.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.dubbo.config.annotation.Reference;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.Lists;
import com.okdeer.archive.goods.base.entity.GoodsSpuCategory;
import com.okdeer.archive.goods.base.service.GoodsSpuCategoryServiceApi;
import com.okdeer.archive.goods.dto.StoreSkuParamDto;
import com.okdeer.archive.goods.store.dto.StoreSkuDto;
import com.okdeer.archive.goods.store.service.GoodsStoreSkuServiceApi;
import com.okdeer.archive.store.entity.StoreInfo;
import com.okdeer.archive.store.service.StoreInfoServiceApi;
import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.base.common.utils.DateUtils;
import com.okdeer.base.common.utils.PageUtils;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.base.common.utils.mapper.BeanMapper;
import com.okdeer.base.dal.IBaseMapper;
import com.okdeer.base.service.BaseServiceImpl;
import com.okdeer.bdp.address.entity.Address;
import com.okdeer.bdp.address.service.IAddressService;
import com.okdeer.common.entity.ReturnInfo;
import com.okdeer.mall.activity.bo.ActLimitRelBuilder;
import com.okdeer.mall.activity.bo.ActivityParamBo;
import com.okdeer.mall.activity.bo.FavourParamBO;
import com.okdeer.mall.activity.discount.entity.ActivityBusinessRel;
import com.okdeer.mall.activity.discount.entity.ActivityDiscount;
import com.okdeer.mall.activity.discount.entity.ActivityDiscountCondition;
import com.okdeer.mall.activity.discount.enums.ActivityBusinessType;
import com.okdeer.mall.activity.discount.enums.ActivityDiscountStatus;
import com.okdeer.mall.activity.discount.enums.ActivityDiscountType;
import com.okdeer.mall.activity.discount.mapper.ActivityBusinessRelMapper;
import com.okdeer.mall.activity.discount.mapper.ActivityDiscountConditionMapper;
import com.okdeer.mall.activity.discount.mapper.ActivityDiscountMapper;
import com.okdeer.mall.activity.discount.service.ActivityDiscountService;
import com.okdeer.mall.activity.dto.ActivityBusinessRelDto;
import com.okdeer.mall.activity.dto.ActivityInfoDto;
import com.okdeer.mall.activity.dto.ActivityParamDto;
import com.okdeer.mall.activity.service.FavourFilterStrategy;
import com.okdeer.mall.activity.service.MaxFavourStrategy;
import com.okdeer.mall.common.utils.RobotUserUtil;
import com.okdeer.mall.order.vo.Discount;
import com.okdeer.mall.order.vo.FullSubtract;

/**
 * 满减(满折)活动service实现类
 * @pr yscm
 * @desc 满减(满折)活动service实现类
 * @author zengj
 * @date 2016年1月26日 下午2:23:11
 * @copyright ©2005-2020 yschome.com Inc. All rights reserved
 *
 * =================================================================================================
 *     Task ID			  Date			     Author		      Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *		重构V4.1 			2016-07-22			zengj			查询店铺有效的满减
 */
@Service
public class ActivityDiscountServiceImpl extends BaseServiceImpl implements ActivityDiscountService {

	@Resource
	private ActivityDiscountMapper activityDiscountMapper;
	
	@Resource
	private ActivityDiscountConditionMapper activityDiscountConditionMapper;
	
	@Resource
	private ActivityBusinessRelMapper activityBusinessRelMapper;
	
	@Resource
	private MaxFavourStrategy maxFavourStrategy;
	
	@Reference(version = "1.0.0",check=false)
	private IAddressService addressApi;
	
	@Reference(version = "1.0.0",check=false)
	private StoreInfoServiceApi storeInfoServiceApi;
	
	@Reference(version = "1.0.0",check=false)
	private GoodsStoreSkuServiceApi goodsStoreSkuServiceApi;
	
	@Reference(version = "1.0.0",check=false)
	private GoodsSpuCategoryServiceApi goodsSpuCategoryServiceApi;
	
	@Override
	public IBaseMapper getBaseMapper() {
		return activityDiscountMapper;
	}
	
	@Override
	@Transactional(rollbackFor=Exception.class)
	public ReturnInfo update(ActivityInfoDto actInfoDto) {
		// 初始化ReturnInfo
		ReturnInfo retInfo = new ReturnInfo();
		// 活动信息
		ActivityDiscount actInfo = actInfoDto.getActivityInfo();
		String activityId = actInfo.getId();
		ActivityDiscount currentAct = activityDiscountMapper.findById(activityId);
		if(currentAct.getStatus() != ActivityDiscountStatus.noStart){
			retInfo.setFlag(false);
			retInfo.setMessage("该满减活动处于" + currentAct.getStatus().getValue() + "状态下，不能修改！");
		}
		// 同一时间、同一地区、同一店铺活动唯一性检查。
		if(!checkUnique(actInfoDto)){
			retInfo.setFlag(false);
			retInfo.setMessage("创建失败，选定范围指定时间内已存在活动，请重新选择范围或更改时间！");
			return retInfo;
		}
		// 优惠条件列表
		List<ActivityDiscountCondition> conditionList = parseConditionList(actInfoDto);
		// 限制条件列表
		List<ActivityBusinessRel> limitList = parseLimitList(actInfoDto);
		if(actInfo.getType() != ActivityDiscountType.PIN_MONEY){
			actInfo.setGrantType(0);
		}
		// 修改活动信息
		activityDiscountMapper.update(actInfo);
		// 删除活动下的优惠条件
		activityDiscountConditionMapper.deleteByActivityId(activityId);
		// 删除活动下的业务关联关系
		activityBusinessRelMapper.deleteByActivityId(activityId);
		// 批量新增优惠条件列表
		activityDiscountConditionMapper.batchAdd(conditionList);
		// 批量新增活动限制信息
		if(CollectionUtils.isNotEmpty(limitList)){
			activityBusinessRelMapper.batchAdd(limitList);
		}
		return retInfo;
	}
	
	@Override
	@Transactional(rollbackFor=Exception.class)
	public ReturnInfo add(ActivityInfoDto actInfoDto) {
		// 初始化ReturnInfo
		ReturnInfo retInfo = new ReturnInfo();
		// 同一时间、同一地区、同一店铺活动唯一性检查。
		if(!checkUnique(actInfoDto)){
			retInfo.setFlag(false);
			retInfo.setMessage("创建失败，选定范围指定时间内已存在活动，请重新选择范围或更改时间！");
			return retInfo;
		}
		// 活动信息
		ActivityDiscount actInfo = actInfoDto.getActivityInfo();
		// 生成唯一主键
		actInfo.setId(UuidUtils.getUuid());
		// 活动状态默认为未开始
		actInfo.setStatus(ActivityDiscountStatus.noStart);
		if(actInfo.getType() != ActivityDiscountType.PIN_MONEY){
			actInfo.setGrantType(0);
		}
		// 优惠条件列表
		List<ActivityDiscountCondition> conditionList = parseConditionList(actInfoDto);
		// 限制条件列表
		List<ActivityBusinessRel> limitList = parseLimitList(actInfoDto);
		// 新增活动信息
		activityDiscountMapper.add(actInfo);
		// 批量新增优惠条件列表
		activityDiscountConditionMapper.batchAdd(conditionList);
		// 批量新增活动限制信息
		if(CollectionUtils.isNotEmpty(limitList)){
			activityBusinessRelMapper.batchAdd(limitList);
		}
		return retInfo;
	}
	
	/**
	 * @Description: 活动唯一性校验
	 * @param actInfoDto
	 * @return   
	 * @author maojj
	 * @date 2017年4月21日
	 */
	private boolean checkUnique(ActivityInfoDto actInfoDto){
		ActivityDiscount actInfo = actInfoDto.getActivityInfo();
		ActivityParamBo paramBo = BeanMapper.map(actInfo, ActivityParamBo.class);
		if(StringUtils.isNotEmpty(actInfo.getId())){
			paramBo.setExcludedId(actInfo.getId());
		}
		if(StringUtils.isNotEmpty(actInfoDto.getLimitRangeIds())){
			String[] tempArr = actInfoDto.getLimitRangeIds().split(",");
			paramBo.setLimitRangeIds(Arrays.asList(tempArr));
		}
		int count = activityDiscountMapper.countConflict(paramBo);
		if(count >= 1){
			return false;
		}else{
			return true;
		}
	}
	
	private List<ActivityDiscountCondition> parseConditionList(ActivityInfoDto actInfoDto){
		List<ActivityDiscountCondition> conditionList = actInfoDto.getConditionList();
		ActivityDiscountType activityType = actInfoDto.getActivityInfo().getType();
		String activityId = actInfoDto.getActivityInfo().getId();
		// 最小区间
		int minSection = 0;
		// 最大区间
		int maxSection = 0;
		// 上一个最大区间值
		int lastMaxSection = 0;
		int sort = 0;
		for(ActivityDiscountCondition condition : conditionList){
			condition.setId(UuidUtils.getUuid());
			condition.setDiscountId(activityId);
			if(activityType == ActivityDiscountType.PIN_MONEY){
				// 如果是零钱活动，需要根据百分比计算百分比区间.百分比必须为整数，所以百分比区间值按照100进行划分。
				// 如优惠条件百分比分配为10%，40%，50%。则对应区间为：[1~10],[11~50],[51~100]
				minSection = lastMaxSection + 1;
				maxSection = lastMaxSection + condition.getRate();
				lastMaxSection = maxSection;
				condition.setMinSection(minSection);
				condition.setMaxSection(maxSection);
				condition.setArrive(BigDecimal.valueOf(0.00));
				condition.setDiscount(BigDecimal.valueOf(0.00));
			}else{
				// 设置默认值
				condition.setMinAmount(BigDecimal.valueOf(0.00));
				condition.setMaxAmount(BigDecimal.valueOf(0.00));
				condition.setRate(0);
				condition.setMinSection(0);
				condition.setMaxSection(0);
			}
			condition.setSort(sort++);
		}
		return conditionList;
	}
	
	/**
	 * @Description: 解析限制条件列表，为限制条件生成主键Id
	 * @param actInfoDto
	 * @return   
	 * @author maojj
	 * @date 2017年4月18日
	 */
	private List<ActivityBusinessRel> parseLimitList(ActivityInfoDto actInfoDto){
		// 限制条件列表
		List<ActivityBusinessRel> limitList = Lists.newArrayList();
		List<ActivityBusinessRelDto<Object>> relDtoList = actInfoDto.getRelDtoList();
		String activityId = actInfoDto.getActivityInfo().getId();
		// 活动业务关联关系（限制关系）
		ActivityBusinessRel limitRel = null;
		int sort = 0;
		for(ActivityBusinessRelDto<?> relDto : relDtoList){
			limitRel = BeanMapper.map(relDto, ActivityBusinessRel.class);
			limitRel.setId(UuidUtils.getUuid());
			limitRel.setActivityId(activityId);
			limitRel.setSort(sort++);
			limitList.add(limitRel);
		}
		return limitList;
	}

	@Override
	@Transactional(rollbackFor=Exception.class)
	public void updateStatus() {
		// 当前时间
		Date currentDate = DateUtils.getSysDate();
		// 查询需要更新状态的集合
		List<ActivityDiscount> actList = activityDiscountMapper.findNeedUpdateList(currentDate);
		if(CollectionUtils.isEmpty(actList)){
			return;
		}
		// 状态变更调度，需要将未开始的进行开始，已关闭的进行关闭。
		ActivityParamBo paramBo = new ActivityParamBo();
		paramBo.setUpdateTime(currentDate);
		paramBo.setUpdateUserId(RobotUserUtil.getRobotUser().getId());
		// 需要设置为进行中的活动Id列表
		List<String> ingList = Lists.newArrayList();
		// 需要设置为已结速的活动Id列表
		List<String> endList = Lists.newArrayList();
		for(ActivityDiscount act : actList){
			if(act.getStatus() == ActivityDiscountStatus.ing){
				ingList.add(act.getId());
			}else if(act.getStatus() == ActivityDiscountStatus.end){
				endList.add(act.getId());
			}
		}
		// 更新进行中的活动
		paramBo.setStatus(ActivityDiscountStatus.ing);
		paramBo.setActivityIds(ingList);
		activityDiscountMapper.updateStatus(paramBo);
		// 更新需要结束的活动
		paramBo.setStatus(ActivityDiscountStatus.end);
		paramBo.setActivityIds(endList);
		activityDiscountMapper.updateStatus(paramBo);
	}

	@Override
	public List<Discount> findValidDiscount(FavourParamBO paramBo, FavourFilterStrategy favourFilter) throws Exception {
		List<Discount> discountList = activityDiscountMapper.findValidDiscount(paramBo);
		if(CollectionUtils.isEmpty(discountList)){
			return discountList;
		}
		// 对集合进行数据迭代
		Iterator<Discount> it = discountList.iterator();
		Discount discount = null;
		while(it.hasNext()){
			discount = it.next();
			if(!favourFilter.accept(discount)){
				// 如果过滤器不接受该优惠，则将该优惠从列表中移除
				it.remove();
			}else{
				discount.setMaxFavourStrategy(maxFavourStrategy.calMaxFavourRule(discount, paramBo.getTotalAmount()));
			}
		}
		return discountList;
	}

	@Override
	public List<FullSubtract> findValidFullSubtract(FavourParamBO paramBo, FavourFilterStrategy favourFilter)
			throws Exception {
		List<FullSubtract> fullSubtractList = activityDiscountMapper.findValidFullSubtract(paramBo);
		if(CollectionUtils.isEmpty(fullSubtractList)){
			return fullSubtractList;
		}
		// 对集合进行数据迭代
		Iterator<FullSubtract> it = fullSubtractList.iterator();
		FullSubtract fullSubtract = null;
		while(it.hasNext()){
			fullSubtract = it.next();
			if(!favourFilter.accept(fullSubtract)){
				// 如果过滤器不接受该优惠，则将该优惠从列表中移除
				it.remove();
			}else{
				fullSubtract.setMaxFavourStrategy(maxFavourStrategy.calMaxFavourRule(fullSubtract, paramBo.getTotalAmount()));
			}
		}
		return fullSubtractList;
	}

	@Override
	public List<Map<String, Object>> findActivityDiscountByStoreId(Map<String, Object> params) {
		return activityDiscountMapper.findActivityDiscountByStoreId(params);
	}

	@Override
	public List<ActivityDiscount> findListByParam(ActivityParamDto paramDto) {
		if(paramDto.getPageNumber() != null && paramDto.getPageSize() != null){
			PageHelper.startPage(paramDto.getPageNumber(),paramDto.getPageSize());
		}
		return activityDiscountMapper.findListByParam(paramDto);
	}

	@Override
	public ReturnInfo batchClose(ActivityParamBo paramBo) {
		ReturnInfo retInfo = new ReturnInfo();
		ActivityParamDto paramDto = new ActivityParamDto();
		paramDto.setActivityIds(paramBo.getActivityIds());
		// 检查需要关闭的活动状态
		List<ActivityDiscount> actList = activityDiscountMapper.findListByParam(paramDto);
		for(ActivityDiscount act : actList){
			if(act.getStatus() != ActivityDiscountStatus.noStart 
					&& act.getStatus() != ActivityDiscountStatus.ing){
				// 活动只能关闭未开始和已进行中的
				retInfo.setFlag(false);
				retInfo.setMessage("选中的活动中存在已关闭活动，请重新选择!");
				return retInfo;
			}
		}
		// 关闭活动
		activityDiscountMapper.updateStatus(paramBo);
		return retInfo;
	}

	@Override
	public ActivityInfoDto findInfoById(String id) throws ServiceException {
		// 活动基本信息
		ActivityDiscount activityInfo = activityDiscountMapper.findById(id);
		// 活动优惠条件信息
		List<ActivityDiscountCondition> conditionList = activityDiscountConditionMapper.findByActivityId(id);
		// 活动业务限制信息
		List<ActivityBusinessRel> relList = activityBusinessRelMapper.findByActivityId(id);
		// 解析业务限制信息
		ActLimitRelBuilder limitBuilder = parseRelList(relList);
		ActivityInfoDto actInfoDto = new ActivityInfoDto();
		actInfoDto.setActivityInfo(activityInfo);
		actInfoDto.setActivityType(activityInfo.getType().ordinal());
		actInfoDto.setConditionList(conditionList);
		if(limitBuilder != null){
			actInfoDto.setRelDtoList(limitBuilder.retrieveResult());
			actInfoDto.setLimitRangeIds(limitBuilder.getLimitRangeIds());
		}
		return actInfoDto;
	}
	
	private  ActLimitRelBuilder parseRelList(List<ActivityBusinessRel> relList) throws ServiceException{
		if(CollectionUtils.isEmpty(relList)){
			return null;
		}
		ActLimitRelBuilder builder = new ActLimitRelBuilder();
		// 加载当前业务列表
		builder.loadBusiRelList(relList);
		// 地址列表
		List<Address> areaList = Lists.newArrayList();
		// 店铺列表
		List<StoreInfo> storeInfoList = null;
		// 分类列表
		List<GoodsSpuCategory> spuCtgList = null;
		// 商品列表
		List<StoreSkuDto> storeSkuList = null;
		// 遍历具体业务信息
		for(Map.Entry<ActivityBusinessType, List<String>> entry : builder.getRelIdMap().entrySet()){
			ActivityBusinessType busiType = entry.getKey();
			List<String> busiIds = entry.getValue();
			switch (busiType) {
				case CITY:
				case PROVINCE:
					findAreaList(areaList,busiIds,busiType);
					break;
				case STORE:
					storeInfoList = storeInfoServiceApi.selectByIds(busiIds);
					postStoreList(storeInfoList);
					break;
				case SKU:
					storeSkuList = findStoreSkuList(busiIds);
					break;
				case SKU_CATEGORY:
					spuCtgList = goodsSpuCategoryServiceApi.selectByIds(busiIds);
					break;

				default:
					break;
			}
		}
		builder.loadAreaList(areaList);
		builder.loadStoreList(storeInfoList);
		builder.loadSpuCtgList(spuCtgList);
		builder.loadStoreSkuList(storeSkuList);
		return builder;
	}
	
	/**
	 * @Description: 查询地区列表
	 * @param areaList
	 * @param areaIds   
	 * @author maojj
	 * @date 2017年4月20日
	 */
	private void findAreaList(List<Address> areaList,List<String> areaIds,ActivityBusinessType busiType){
		Address addr = null;
		for(String areaId : areaIds){
			addr = addressApi.getAddressById(Long.parseLong(areaId));
			if(busiType == ActivityBusinessType.PROVINCE){
				areaList.addAll(addressApi.getChildrenList(Long.parseLong(areaId)));
			}
			areaList.add(addr);
		}
	}
	
	/**
	 * @Description: 查询店铺商品列表
	 * @param storeSkuIds
	 * @return   
	 * @author maojj
	 * @date 2017年4月20日
	 */
	private List<StoreSkuDto> findStoreSkuList(List<String> storeSkuIds){
		StoreSkuParamDto paramDto = new StoreSkuParamDto();
		paramDto.setPageNumber(1);
		paramDto.setPageSize(storeSkuIds.size());
		paramDto.setStoreSkuIds(storeSkuIds);
		PageUtils<StoreSkuDto> storeSkuPage = goodsStoreSkuServiceApi.findStoreSkuList(paramDto);
		return storeSkuPage.getList();
	}
	
	private void postStoreList(List<StoreInfo> storeInfoList){
		if(CollectionUtils.isEmpty(storeInfoList)){
			return;
		}
		for(StoreInfo storeInfo : storeInfoList){
			if(StringUtils.isNotEmpty(storeInfo.getCityId())){
				Address address = addressApi.getAddressById(Long.parseLong(storeInfo.getCityId()));
				storeInfo.setAddress(address.getName());
			}
		}
	}

	@Override
	public List<ActivityInfoDto> findByStore(ActivityParamDto paramDto) throws Exception {
		List<String> activityIds = activityDiscountMapper.findByStore(paramDto);
		List<ActivityInfoDto> actInfoList = Lists.newArrayList();
		ActivityInfoDto actInfo = null;
		for(String activityId : activityIds){
			actInfo = this.findInfoById(activityId);
			if(actInfo != null){
				actInfoList.add(actInfo);
			}
		}
		return actInfoList;
	}
}
