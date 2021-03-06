
package com.okdeer.mall.system.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.google.common.collect.Maps;
import com.okdeer.archive.store.entity.StoreDetailVo;
import com.okdeer.archive.store.entity.StoreInfo;
import com.okdeer.archive.store.enums.ResultCodeEnum;
import com.okdeer.archive.store.service.StoreInfoServiceApi;
import com.okdeer.archive.system.entity.SysBuyerUser;
import com.okdeer.archive.system.entity.SysBuyerUserThirdparty;
import com.okdeer.archive.system.entity.SysSmsVerifyCode;
import com.okdeer.archive.system.entity.SysUserLoginLog;
import com.okdeer.archive.system.service.SysSmsVerifyCodeServiceApi;
import com.okdeer.archive.system.service.SysUserLoginLogServiceApi;
import com.okdeer.base.common.enums.Disabled;
import com.okdeer.base.common.exception.ServiceException;
import com.okdeer.base.common.model.RequestParams;
import com.okdeer.base.common.utils.EncryptionUtils;
import com.okdeer.base.common.utils.UuidUtils;
import com.okdeer.base.common.utils.mapper.BeanMapper;
import com.okdeer.base.dal.IBaseCrudMapper;
import com.okdeer.base.service.BaseCrudServiceImpl;
import com.okdeer.ca.api.buyeruser.entity.SysBuyerUserConditionDto;
import com.okdeer.ca.api.buyeruser.entity.SysBuyerUserDto;
import com.okdeer.ca.api.buyeruser.entity.SysBuyerUserItemDto;
import com.okdeer.ca.api.buyeruser.service.ISysBuyerUserApi;
import com.okdeer.ca.api.common.ApiException;
import com.okdeer.ca.api.sysuser.entity.SysUserDto;
import com.okdeer.ca.api.sysuser.service.ISysUserApi;
import com.okdeer.common.consts.RedisKeyConstants;
import com.okdeer.mall.common.enums.ClientTypeEnum;
import com.okdeer.mall.common.utils.security.DESUtils;
import com.okdeer.mall.member.member.dto.SysBuyerLocateInfoDto;
import com.okdeer.mall.member.member.entity.MemberConsigneeAddress;
import com.okdeer.mall.member.member.entity.SysBuyerExt;
import com.okdeer.mall.member.member.service.MemberConsigneeAddressServiceApi;
import com.okdeer.mall.member.member.service.SysBuyerExtServiceApi;
import com.okdeer.mall.member.member.service.SysBuyerLocateInfoServiceApi;
import com.okdeer.mall.member.points.entity.PointsRecord;
import com.okdeer.mall.member.points.entity.PointsRule;
import com.okdeer.mall.member.points.enums.PointsRuleCode;
import com.okdeer.mall.member.points.service.PointsRecordServiceApi;
import com.okdeer.mall.member.points.service.PointsRuleServiceApi;
import com.okdeer.mall.order.enums.OrderResourceEnum;
import com.okdeer.mall.system.entity.BuyerUserVo;
import com.okdeer.mall.system.entity.SysRandCodeRecord;
import com.okdeer.mall.system.entity.SysUserInvitationCode;
import com.okdeer.mall.system.enums.InvitationUserType;
import com.okdeer.mall.system.enums.VerifyCodeBussinessTypeEnum;
import com.okdeer.mall.system.mapper.SysBuyerUserMapper;
import com.okdeer.mall.system.mapper.SysBuyerUserThirdpartyMapper;
import com.okdeer.mall.system.mapper.SysRandCodeRecordMapper;
import com.okdeer.mall.system.mapper.SysSmsVerifyCodeMapper;
import com.okdeer.mall.system.service.InvitationCodeService;
import com.okdeer.mall.system.service.SysBuyerUserService;
import com.okdeer.mall.system.service.SysBuyerUserServiceApi;

/**
 * @DESC: 买家用户信息
 * @author YSCGD
 * @date  2016-03-17 17:05:52
 * @version 1.0.0
 * @copyright ©2005-2020 yschome.com Inc. All rights reserved
 * 
 *=================================================================================================
 *     Task ID            Date               Author           Description
 * ----------------+----------------+-------------------+-------------------------------------------
 *     V1.1.0          2016年10月14日                          zhaoqc          修改注册生成邀请码的问题
 * 
 */
@Service(version = "1.0.0", interfaceName = "com.okdeer.mall.system.service.SysBuyerUserServiceApi")
class SysBuyerUserServiceImpl extends BaseCrudServiceImpl implements SysBuyerUserService, SysBuyerUserServiceApi {

	private static final Logger logger = LoggerFactory.getLogger(SysBuyerUserServiceImpl.class);

	@Resource
	private SysBuyerUserMapper sysBuyerUserMapper;

	/**
	 * 系统用户登陆信息日志
	 */
	@Reference(version = "1.0.0", check = false)
	private SysUserLoginLogServiceApi sysUserLoginLogService;

	/**
	 * 短信验证码
	 */
	@Reference(version = "1.0.0", check = false)
	private SysSmsVerifyCodeServiceApi sysSmsVerifyCodeService;

	// @Resource
	// private SysBuyerExtMapper sysBuyerExtMapper;
	@Reference(version = "1.0.0", check = false)
	SysBuyerExtServiceApi sysBuyerExtServiceApi;

	@Resource
	private SysSmsVerifyCodeMapper sysSmsVerifyCodeMapper;

	@Resource
	private SysBuyerUserThirdpartyMapper sysBuyerUserThirdpartyMapper;

	// @Resource
	// private PointsRuleMapper pointsRuleMapper;
	@Reference(version = "1.0.0", check = false)
	PointsRuleServiceApi pointsRuleServiceApi;

	// @Resource
	// private PointsRecordMapper pointsRecordMapper;
	@Reference(version = "1.0.0", check = false)
	PointsRecordServiceApi pointsRecordServiceApi;

	@Reference(version = "1.0.0", check = false)
	private ISysBuyerUserApi sysBuyerUserApi;

	/**
	 * mapper注入
	 */
	// @Autowired
	// StoreInfoMapper storeInfoMapper;
	@Reference(version = "1.0.0", check = false)
	StoreInfoServiceApi storeInfoServiceApi;

	/**
	 * 用户中心系统用户接口注入
	 */
	@Reference(version = "1.0.0", check = false)
	ISysUserApi sysUserApi;

	/**
	 * mapper注入
	 */
	// @Autowired
	// MemberConsigneeAddressMapper memberConsigneeAddressMapper;
	@Reference(version = "1.0.0", check = false)
	private MemberConsigneeAddressServiceApi MemberConsigneeAddressServiceApi;

	/**
	 * RedisTemplate
	 */
	@Autowired
	private RedisTemplate<String, String> redisTemplate;
	/**
	 * 用户邀请码service
	 */
	@Autowired
	private InvitationCodeService invitationCodeService;
	
	/**
	 * 随机码Mapper
	 */
	@Autowired
	private SysRandCodeRecordMapper sysRandCodeRecordMapper;
	
	
	/**
	 * 用户定位信息api
	 */
	@Reference(version = "1.0.0", check = false)
	private SysBuyerLocateInfoServiceApi sysBuyerLocateInfoServiceApi;
	
	@Override
	public IBaseCrudMapper init() {
		return sysBuyerUserMapper;
	}

	/**
	 * DESC: 添加买家用户信息、修改验证码状态、添加第三方平台账号与本平台账号映射
	 * @author LIU.W
	 * @param sysSmsVerifyCodeUpdate
	 * @param buyerUserThirdparty
	 * @throws ServiceException
	 */
	@Transactional(rollbackFor = Exception.class)
	public String addSysBuyerSync(SysBuyerUserDto sysBuyerUserDto, SysSmsVerifyCode sysSmsVerifyCodeUpdate,
			SysBuyerUserThirdparty buyerUserThirdparty) throws ApiException, ServiceException {

		try {

			String buyerId = UuidUtils.getUuid();
			/**
			 * 1. 更新验证码状态
			 */
			if (null != sysSmsVerifyCodeUpdate) {
				sysSmsVerifyCodeMapper.updateByPrimaryKeySelective(sysSmsVerifyCodeUpdate);
			}

			/**
			 * 2. 添加第三方平台与自平台账号映射关系
			 */
			if (null != buyerUserThirdparty) {
				buyerUserThirdparty.setBuyerUserId(buyerId);
				sysBuyerUserThirdpartyMapper.insertSelective(buyerUserThirdparty);
			}
			/**
			 * 3. 添加用户注册信息
			 */
			if (null != sysBuyerUserDto) {

				/**
				 * 3.1 添加买家用户扩展表信息并注册用户送积分
				 */
				PointsRule pointsRule = new PointsRule();
				pointsRule.setCode(PointsRuleCode.REGISTER.getCode());
				/**
				 * 3.2 根据积分规则编码code查询规则
				 */
				// PointsRule pointRule =
				// pointsRuleMapper.selectByCode(pointsRule);
				PointsRule pointRule = pointsRuleServiceApi.selectByCode(pointsRule);
				if (null == pointRule) {
					return buyerId;
				}
				/**
				 * 3.3 添加用户扩展信息及积分详细记录
				 */
				SysBuyerExt sysBuyerExt = new SysBuyerExt();
				sysBuyerExt.setId(UuidUtils.getUuid());
				sysBuyerExt.setUserId(buyerId);
				sysBuyerExt.setPointVal(pointRule.getPointVal());
				// sysBuyerExtMapper.insertSelective(sysBuyerExt);
				sysBuyerExtServiceApi.insertSelective(sysBuyerExt);

				PointsRecord pointsRecord = new PointsRecord();
				pointsRecord.setId(UuidUtils.getUuid());
				pointsRecord.setUserId(buyerId);
				pointsRecord.setCode(pointRule.getCode());
				pointsRecord.setPointVal(pointRule.getPointVal());
				pointsRecord.setType((byte) 0);
				pointsRecord.setDescription(pointRule.getRemark());
				pointsRecord.setCreateTime(new Date());
				// pointsRecordMapper.insert(pointsRecord);
				pointsRecordServiceApi.insert(pointsRecord);
				/**
				 * 3.4 添加用户信息
				 */
				sysBuyerUserDto.setId(buyerId);
				sysBuyerUserApi.save(sysBuyerUserDto);
			}

			return buyerId;
		} catch (ApiException e) {
			logger.error("添加用户失败!", e);
			throw e;
		} catch (Exception e) {
			throw new ServiceException("添加用户失败!", e);
		}
	}
	
	/**
	 * 
	 * @Description: V2.1新增需求，pos机注册时保存当前店铺所在地址
	 * @return String 
	 * @throws 异常
	 * @author chenzc
	 * @date 2017年2月22日
	 */
	@Transactional(rollbackFor = Exception.class)
	public String addSysBuyerSyncV210(SysBuyerUserDto sysBuyerUserDto, SysSmsVerifyCode sysSmsVerifyCodeUpdate,
			SysBuyerUserThirdparty buyerUserThirdparty, String storeId) throws ApiException, ServiceException {

		try {
			// 原注册用户的方法
			String buyerId = addSysBuyerSync(sysBuyerUserDto, sysSmsVerifyCodeUpdate, buyerUserThirdparty);
			
			// 如果传了店铺id，则保存用户注册时的所在城市
			if (StringUtils.isNotBlank(storeId)) {
				StoreDetailVo storeDetailVo = storeInfoServiceApi.getStoreDetailById(storeId);
				
				if (null != storeDetailVo) {
					String cityId = storeDetailVo.getCityId();
					
					// 保存对象
					SysBuyerLocateInfoDto dto = new SysBuyerLocateInfoDto();
					dto.setStoreCityId(cityId);
					dto.setUserId(buyerId);
					dto.setRegisterSource(sysBuyerUserDto.getDataSource());
					
					sysBuyerLocateInfoServiceApi.save(dto);
				}
			}

			return buyerId;
		} catch (ApiException e) {
			logger.error("添加用户失败!", e);
			throw e;
		} catch (Exception e) {
			throw new ServiceException("添加用户失败!", e);
		}
	}

	// begin add by wangf01 2016.08.11
	@Transactional(rollbackFor = Exception.class)
	public String addSysBuyerSync410(SysBuyerUserDto sysBuyerUserDto, SysSmsVerifyCode sysSmsVerifyCodeUpdate,
			SysBuyerUserThirdparty buyerUserThirdparty) throws Exception {

		try {
			String buyerId = UuidUtils.getUuid();
			/**
			 * 1. 更新验证码状态
			 */
			if (null != sysSmsVerifyCodeUpdate) {
				sysSmsVerifyCodeMapper.updateByPrimaryKeySelective(sysSmsVerifyCodeUpdate);
			}

			/**
			 * 2. 添加第三方平台与自平台账号映射关系
			 */
			if (null != buyerUserThirdparty) {
				buyerUserThirdparty.setBuyerUserId(buyerId);
				sysBuyerUserThirdpartyMapper.insertSelective(buyerUserThirdparty);
			}
			/**
			 * 3. 添加用户注册信息
			 */
			if (null != sysBuyerUserDto) {
				/**
				 * 3.4 添加用户信息
				 */
				sysBuyerUserDto.setId(buyerId);
				sysBuyerUserApi.save(sysBuyerUserDto);
			}

			return buyerId;
		} catch (ApiException e) {
			logger.error("添加用户失败!", e);
			throw e;
		} catch (Exception e) {
			throw new ServiceException("添加用户失败!", e);
		}
	}
	// end add by wangf01 2016.08.11

	/**
	 * DESC: 修改验证码状态、添加第三方平台账号与本平台账号映射
	 * @author LIU.W
	 * @param sysSmsVerifyCodeUpdate
	 * @param buyerUserThirdparty
	 * @throws ServiceException
	 */
	@Transactional(rollbackFor = Exception.class)
	public void addSysBuyerThirdParty(SysSmsVerifyCode sysSmsVerifyCodeUpdate,
			SysBuyerUserThirdparty buyerUserThirdparty) throws ServiceException {

		try {
			/**
			 * 2. 更新验证码状态
			 */
			if (null != sysSmsVerifyCodeUpdate) {
				sysSmsVerifyCodeMapper.updateByPrimaryKeySelective(sysSmsVerifyCodeUpdate);
			}
			/**
			 * 3. 添加第三方平台与自平台账号映射关系
			 */
			if (null != buyerUserThirdparty) {
				sysBuyerUserThirdpartyMapper.insertSelective(buyerUserThirdparty);
			}

		} catch (Exception e) {
			throw new ServiceException("", e);
		}
	}

	@Override
	public String selectMemberMobile(String userId) {
		return sysBuyerUserMapper.selectMemberMobile(userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateMobile(String phone, String smsCodeId, String userId, String storeId)
			throws ServiceException, ApiException {
		SysUserDto sysUserDto = new SysUserDto();
		sysUserDto.setId(userId);
		sysUserDto.setPhone(phone);
		sysUserDto.setUpdateUserId(userId);
		// 更新用户中心用户手机号码
		sysUserApi.edit(sysUserDto);
		StoreInfo storeInfo = new StoreInfo();
		storeInfo.setId(storeId);
		storeInfo.setMobile(phone);
		storeInfo.setUpdateUserId(userId);
		storeInfo.setUpdateTime(new Date());
		// 更新店铺表手机号码
		// storeInfoMapper.updateByPrimaryKeySelective(storeInfo);
		storeInfoServiceApi.updateByPrimaryKeySelective(storeInfo);

		// List<MemberConsigneeAddress> addressList =
		// memberConsigneeAddressMapper.selectByUserId(storeId);
		List<MemberConsigneeAddress> addressList = MemberConsigneeAddressServiceApi.findListByUserId(storeId);
		if (addressList != null && addressList.size() > 0) {
			MemberConsigneeAddress memberConsigneeAddress = addressList.get(0);
			memberConsigneeAddress.setMobile(phone);
			// 绑定手机号码时将手机号码设置到店铺初始地址的手机号码字段里
			// memberConsigneeAddressMapper.updateByPrimaryKeySelective(memberConsigneeAddress);
			MemberConsigneeAddressServiceApi.updateByPrimaryKeySelective(memberConsigneeAddress);
		}

		// 更新验证码已使用状态
		SysSmsVerifyCode sysSmsVerifyCodeUpdate = new SysSmsVerifyCode();
		sysSmsVerifyCodeUpdate.setId(smsCodeId);
		sysSmsVerifyCodeUpdate.setStatus(1);
		sysSmsVerifyCodeMapper.updateByPrimaryKeySelective(sysSmsVerifyCodeUpdate);
	}

	// begin add by wushp
	/**
	 * (non-Javadoc)
	 * @see com.okdeer.mall.system.service.SysBuyerUserServiceApi#findByPrimaryKey(java.lang.String)
	 */
	@Override
	public SysBuyerUser findByPrimaryKey(String userId) throws ServiceException {
		return sysBuyerUserMapper.selectByPrimaryKey(userId);
	}
	// end add by wushp
	
	/**
	 * 登入验证
	 */
	@Override
	public int loginValidation(BuyerUserVo buyerUserVo) throws Exception {
		
		String mobilePhone = buyerUserVo.isNotDesLoginName() ? buyerUserVo.getLoginName()
				: DESUtils.decrypt(buyerUserVo.getLoginName());
		String loginPassword = buyerUserVo.getLoginPassword();
		if (!StringUtils.isEmpty(loginPassword)) {
			return validationByPwd(mobilePhone,loginPassword);
		}else{
			return validationByVerifyCode(mobilePhone,buyerUserVo);
		}
	}
	
	/**
	 * 验证码登入验证
	 * @param mobilePhone
	 * @param buyerUserVo
	 * @return
	 * @throws Exception   
	 * @author guocp
	 * @date 2017年4月13日
	 */
	private int validationByVerifyCode(String mobilePhone,BuyerUserVo buyerUserVo) throws Exception {
		
		Map<String, Object> map = new HashMap<String, Object>();
		buyerUserVo.setVerifyCode(buyerUserVo.getVerifyCode().toLowerCase());
		String verifyCode = buyerUserVo.getVerifyCode();
		Integer verifyCodeType = buyerUserVo.getVerifyCodeType();
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("phoneSearch", mobilePhone);
		params.put("typeSearch", verifyCodeType);
		params.put("bussinessTypeSearch", VerifyCodeBussinessTypeEnum.LOGIN.getCode());
		SysSmsVerifyCode sysSmsVerifyCode = sysSmsVerifyCodeService.findLatestByParams(params);
		// 如果验证码为空或者验证码不相等，则提示验证码错误，如果验证码相等，但状态为已使用，则提示验证码失效
		if (null == sysSmsVerifyCode || !verifyCode.equals(sysSmsVerifyCode.getVerifyCode())) {
			return 12;
		} else if (sysSmsVerifyCode.getStatus() == 1) {
			map.put("flag", 11);
			return 11;
		}
		// 更新验证码状态 修复未成功验证导致验证码失效
		sysSmsVerifyCodeService.modifyUsedStatus(sysSmsVerifyCode.getId());
		return 0;
	}
	
	/**
	 * 密码登入验证
	 * @param mobilePhone
	 * @param loginPassword
	 * @return
	 * @throws Exception   
	 * @author guocp
	 * @date 2017年4月13日
	 */
	private int validationByPwd(String mobilePhone,String loginPassword) throws Exception {
		
		loginPassword = EncryptionUtils.md5(DESUtils.decrypt(loginPassword));
		// 查询手机用户信息
		SysBuyerUserItemDto sysBuyerUserItemDto = sysBuyerUserApi.login(mobilePhone, null);
		// 验证密码登录
		if (null == sysBuyerUserItemDto) {
			return 1;
		}
		// 验证用户是否设置密码
		if (StringUtils.isEmpty(sysBuyerUserItemDto.getLoginPassword())) {
			return 2;
		}
		// 密码错误
		if (!(loginPassword.toLowerCase()).equals(sysBuyerUserItemDto.getLoginPassword().toLowerCase())) {
			return 3;
		}
		return 0;
	}


	/**
	 * 保存用户登入日志
	 * @param sysUserLoginLogs
	 * @param cLIENT_TYPE_APP   
	 * @author guocp
	 * @date 2017年4月11日
	 */
	private List<SysUserLoginLog> saveUserLoginLog(RequestParams parameters, final String userId) {

		Integer clientType = StringUtils.isNotBlank(parameters.getClientType())
				? Integer.valueOf(parameters.getClientType()) : ClientTypeEnum.CVS.getCode();
		List<SysUserLoginLog> sysUserLoginLogs = sysUserLoginLogService.findAllByUserId(userId, null,
				parameters.getMachineCode(), clientType);
		Date data = new Date();
		// 设置设备登陆信息
		if (!CollectionUtils.isEmpty(sysUserLoginLogs)) {
			SysUserLoginLog sysLog = BeanMapper.map(sysUserLoginLogs.get(0), SysUserLoginLog.class);
			sysLog.setIsLogin(SysUserLoginLog.IS_LOGIN_STAUE_1);
			sysLog.setToken(parameters.getToken());
			sysLog.setVersion(parameters.getVersion());
			sysLog.setUpdateTime(data);
			sysUserLoginLogService.updateSysUserLoginLog(sysLog);
		} else {
			SysUserLoginLog sysLog = new SysUserLoginLog();
			sysLog.setId(UuidUtils.getUuid());
			sysLog.setDeviceId(parameters.getMachineCode());
			sysLog.setIsLogin(SysUserLoginLog.IS_LOGIN_STAUE_1);
			sysLog.setVersion(parameters.getVersion());
			sysLog.setToken(parameters.getToken());
			sysLog.setUserId(userId);
			sysLog.setCreateTime(data);
			sysLog.setUpdateTime(data);
			sysLog.setClientType(clientType);
			sysUserLoginLogService.insertSysUserLoginLog(sysLog);
		}
		return sysUserLoginLogs;
	}

	/**
	 * 获取登入日志
	 * @param id
	 * @param clientType
	 * @return   获取登入日志
	 * @author guocp
	 * @date 2017年4月11日
	 */
	private List<SysUserLoginLog> findUserLoginLogs(String userId,String deviceId, String clientTypeStr) {
		Integer clientType = ClientTypeEnum.CVS.getCode();
		if(clientType!=null){
			clientType = Integer.valueOf(clientTypeStr);
		}
		List<SysUserLoginLog> sysUserLoginLogs = sysUserLoginLogService.findAllByUserId(userId, null, deviceId, clientType);
		return sysUserLoginLogs;
	}

	/**
	 * @Description: 单点登录
	 * @param machineCode
	 * @param token
	 * @param sysBuyerUserItemDto
	 * @param map
	 * @param sysUserLoginLogs   
	 * @return void  
	 * @throws
	 * @author wangf01
	 * @date 2016年7月26日
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void singlePoint(String machineCode, String token, SysBuyerUserItemDto sysBuyerUserItemDto, /*Map map,*/
			List<SysUserLoginLog> sysUserLoginLogs) {
		List<String> ids = new ArrayList<String>();
		boolean bool = true;
		// 设备id
		SysUserLoginLog sysLog = new SysUserLoginLog();
		for (SysUserLoginLog sysUserLoginLog : sysUserLoginLogs) {
			// 该设备是否有登陆过
			if (bool && machineCode.equals(sysUserLoginLog.getDeviceId())) {
				sysLog.setId(sysUserLoginLog.getId());
				sysLog.setDeviceId(sysUserLoginLog.getDeviceId());
				sysLog.setUserId(sysUserLoginLog.getId());
				sysLog.setCreateTime(sysUserLoginLog.getCreateTime());
				sysLog.setClientType(sysUserLoginLog.getClientType());
				bool = false;
			}
			// 设备是否在线

			if (SysUserLoginLog.IS_LOGIN_STAUE_1.equals(sysUserLoginLog.getIsLogin())) {
				ids.add(sysUserLoginLog.getId());
			} // 清除已上线设备

		}

//		map.put("sysUserLoginLogs", sysUserLoginLogs);

		// 清除已上线设备
		if (ids != null && ids.size() > 0) {
			sysUserLoginLogService.updateIsLoginByIds(ids);
		}

	}

	/**
	 * 新增用户或查询用户信息
	 */
	@Transactional(rollbackFor = Exception.class)
	@Override
	public Map<String, Object> saveBuyerUserAndLog(RequestParams parameters,String mobilePhone) throws Exception {
		// 添加用户来源，clientType:0是管家版app，3是便利店app
		String clientTypeStr = parameters.getClientType();
		SysBuyerUserConditionDto condition = new SysBuyerUserConditionDto();
		condition.setLoginName(mobilePhone);
		List<SysBuyerUserItemDto> lstUserItemDtos = sysBuyerUserApi.findByCondition(condition);
		
		SysBuyerUserItemDto sysBuyerUserItemDto = new SysBuyerUserItemDto();
		BuyerUserVo resultBuyerUserVo;
		if (CollectionUtils.isEmpty(lstUserItemDtos)) {
			//调用用户中心保存用户
			String userId = saveBuyerUser(mobilePhone,clientTypeStr);
			//保存邀请码
			SysUserInvitationCode invitationCode = saveInvitationCode(userId);
			resultBuyerUserVo = new BuyerUserVo();
			resultBuyerUserVo.setId(userId);
			resultBuyerUserVo.setLoginName(mobilePhone);
			resultBuyerUserVo.setPhone(mobilePhone);
			resultBuyerUserVo.setInvitationCode(invitationCode.getInvitationCode());
			// 判断用户是否第一次注册登录，如果是则返回1
			resultBuyerUserVo.setIsOneLogin("1");
		} else {
			// 查询手机用户信息
			sysBuyerUserItemDto = sysBuyerUserApi.login(mobilePhone, null);
			resultBuyerUserVo = new BuyerUserVo();
			
			//查询用户邀请码
			SysUserInvitationCode invitationCode = this.invitationCodeService
					.findInvitationCodeByUserId(sysBuyerUserItemDto.getId(), InvitationUserType.phoneUser);
			//当邀请码为空时，让用户继续登录  start 涂志定
			if(invitationCode != null){
				resultBuyerUserVo.setInvitationCode(invitationCode.getInvitationCode());
			}
			//end 涂志定
			PropertyUtils.copyProperties(resultBuyerUserVo, sysBuyerUserItemDto);
			resultBuyerUserVo.setUserId(sysBuyerUserItemDto.getId());
		}
		//保存用户登入日志
		List<SysUserLoginLog> sysUserLoginLogs = saveUserLoginLog(parameters, sysBuyerUserItemDto.getId());
		Map<String, Object> requestMap = Maps.newHashMap();
		requestMap .put("sysUserLoginLogs", sysUserLoginLogs);
		requestMap.put("resultBuyerUserVo", resultBuyerUserVo);
		return requestMap;
	}

	/**
	 * 新增用户
	 * @return   
	 * @author guocp
	 * @throws Exception 
	 * @date 2017年4月11日
	 */
	private String saveBuyerUser(String mobilePhone,String clientType) throws Exception {
		SysBuyerUserDto sysBuyerUserDto = new SysBuyerUserDto();
		sysBuyerUserDto.setLoginName(mobilePhone);
		sysBuyerUserDto.setPhone(mobilePhone);
//		if (StringUtils.isNotBlank(clientType) && "0".equals(clientType)) {
//			sysBuyerUserDto.setDataSource(String.valueOf(OrderResourceEnum.YSCAPP.ordinal()));
//		} else if (StringUtils.isNotBlank(clientType) && "3".equals(clientType)) {
//			sysBuyerUserDto.setDataSource(String.valueOf(OrderResourceEnum.CVSAPP.ordinal()));
//		}else{
//		}
		sysBuyerUserDto.setDataSource(clientType);
		//新增用户及关系
		return this.addSysBuyerSync410(sysBuyerUserDto, null, null);
	}

	//Begin add by zhaoqc 2016.10.05
    @Override
    public SysUserInvitationCode saveInvitationCode(String userId) throws Exception {
        SysUserInvitationCode invitationCode = new SysUserInvitationCode();
        invitationCode.setId(UuidUtils.getUuid());
        invitationCode.setSysBuyerUserId(userId);
        invitationCode.setUserType(InvitationUserType.phoneUser);
        String code = redisTemplate.boundListOps(RedisKeyConstants.MALL_RANDCODE).rightPop();
        SysRandCodeRecord sysRandCodeRecord = null;
        if(StringUtils.isEmpty(code)) {
            sysRandCodeRecord = this.sysRandCodeRecordMapper.getOneRandCode();
            invitationCode.setInvitationCode(sysRandCodeRecord.getRandomCode());
        } else {
            invitationCode.setInvitationCode(code); 
        }
        
        invitationCode.setInvitationUserNum(0);
        invitationCode.setFirstOrderUserNum(0);
        invitationCode.setCreateTime(new Date());
        invitationCode.setUpdateTime(new Date());
        
        //生成邀请码
        this.invitationCodeService.saveCode(invitationCode);
        
        if(sysRandCodeRecord == null) {
            sysRandCodeRecord = this.sysRandCodeRecordMapper.findRecordByRandCode(code);
        }
        
        //在商城库将消费码设置为不可用
        sysRandCodeRecord.setDisabled(Disabled.invalid);
        sysRandCodeRecord.setUpdateTime(new Date());
     
        this.sysRandCodeRecordMapper.updateSysRandCodeRecord(sysRandCodeRecord);
        return invitationCode;
    }
    //End add by zhaoqc 2016.10.05
    
    // Begin added by maojj 2016-10-17
    /**
     * @Description: 验证码校验
     * @param requestParam   
     * @author maojj
     * @date 2016年10月17日
     */
    @Override
    public Map<String,Object> checkVerifyCode(Map<String, Object> requestParam) throws Exception{
    	Map<String,Object> checkResult = new HashMap<String,Object>();
    	checkResult.put("resultCode", ResultCodeEnum.SUCCESS);
    	// 用户手机号码
    	String phone = String.valueOf(requestParam.get("phone"));
    	// 验证码业务类型，对应枚举VerifyCodeBussinessTypeEnum
    	String bussinessTypeSearch = String.valueOf(requestParam.get("bussinessTypeSearch"));
    	// 查询类型：0：语音，1：短信
    	String typeSearch = String.valueOf(requestParam.get("typeSearch"));
    	// 用户输入的验证码
    	String verifyCode = String.valueOf(requestParam.get("verifyCode"));
    	Map<String, Object> params = new HashMap<String, Object>();
		params.put("phoneSearch", phone);
		params.put("typeSearch", typeSearch);
		params.put("bussinessTypeSearch", bussinessTypeSearch);
		SysSmsVerifyCode sysSmsVerifyCode = sysSmsVerifyCodeService.findLatestByParams(params);
		if (sysSmsVerifyCode == null || !verifyCode.equals(sysSmsVerifyCode.getVerifyCode())) {
			// 如果验证码不存在或者验证码不匹配，则提示验证码错误
	    	checkResult.put("resultCode", ResultCodeEnum.VERIFY_CODE_ERROR);
			return checkResult;
		} else if (sysSmsVerifyCode.getStatus() == 1) {
			// 如果验证码已使用，则提示无效验证码
			checkResult.put("resultCode", ResultCodeEnum.VERIFY_CODE_INVALID);
			return checkResult;
		}
		// 校验成功，将验证码ID返回到请求参数中。用于业务处理成功之后，将验证码更改为已使用状态
		checkResult.put("verifyCodeId", sysSmsVerifyCode.getId());
		return checkResult;
    }
    // End added by maojj 2016-10-17

	@Override
	public List<SysBuyerUser> findUserListByIds(List<String> ids,int page,int pageSize) {
		int start = (page -1) * pageSize;
		return sysBuyerUserMapper.findByIds(ids,start,pageSize);
	}
}