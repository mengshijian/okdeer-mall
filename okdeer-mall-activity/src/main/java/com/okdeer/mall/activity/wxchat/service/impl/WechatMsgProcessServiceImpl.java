
package com.okdeer.mall.activity.wxchat.service.impl;

import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.okdeer.common.exception.MallApiException;
import com.okdeer.mall.activity.wxchat.annotation.XStreamCDATA;
import com.okdeer.mall.activity.wxchat.service.WechatMsgHandlerService;
import com.okdeer.mall.activity.wxchat.service.WechatMsgProcessService;
import com.okdeer.mall.activity.wxchat.util.WxchatUtils;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.core.util.QuickWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

@Service
public class WechatMsgProcessServiceImpl implements WechatMsgProcessService, WechatMsgHandlerService {

	private static final Logger logger = LoggerFactory.getLogger(Logger.class);

	private static final Map<String, WechatMsgHandler> msgHandlerMap = Maps.newHashMap();

	@Override
	public String process(String requestXml) throws MallApiException {
		String msgType = getMsgType(requestXml);
		if (msgType == null) {
			throw new MallApiException("解析msgType出错");
		}
		WechatMsgHandler wechatMsgHandler = msgHandlerMap.get(msgType.toUpperCase());
		if (wechatMsgHandler == null) {
			logger.warn("没有找到相应的消息处理类：msgType={}", msgType);
			return null;
		}
		XStream xStream = createXstream();
		xStream.autodetectAnnotations(true);
		xStream.alias("xml", wechatMsgHandler.getRequestClass());
		Object requestObj = xStream.fromXML(requestXml);
		logger.debug("{}开始处理请求....", wechatMsgHandler.getClass().getName());
		Object response = wechatMsgHandler.process(requestObj);
		return xStream.toXML(response);
	}

	@Override
	public void addHandler(WechatMsgHandler wechatMsgHandler) throws MallApiException {
		if (msgHandlerMap.get(wechatMsgHandler.getMsgType()) != null) {
			throw new MallApiException("微信消息处理类重复,消息类型：" + wechatMsgHandler.getMsgType() + "，已经存在的类为："
					+ msgHandlerMap.get(wechatMsgHandler.getMsgType()).getClass());
		}
		msgHandlerMap.put(wechatMsgHandler.getMsgType(), wechatMsgHandler);
	}

	/**
	 *  获取签名
	 *  
	 * @param body
	 * @return
	 */
	private String getMsgType(String body) {

		String msgTypeNodeName = "<" + WxchatUtils.MSGTYPE + ">";
		String msgTypeEndNodeName = "</" + WxchatUtils.MSGTYPE + ">";

		int indexOfSignNode = body.indexOf(msgTypeNodeName);
		int indexOfSignEndNode = body.indexOf(msgTypeEndNodeName);

		if (indexOfSignNode < 0 || indexOfSignEndNode < 0) {
			return null;
		}
		String msgType = body.substring(indexOfSignNode + msgTypeNodeName.length(), indexOfSignEndNode);
		if (msgType.indexOf("CDATA") != -1) {
			msgType = msgType.substring("<![CDATA[".length(), msgType.length() - "]]>".length());
			return msgType;
		}
		return msgType;
	}

	private XStream createXstream() {
		return new XStream(new XppDriver() {

			@Override
			public HierarchicalStreamWriter createWriter(Writer out) {
				return new PrettyPrintWriter(out) {

					boolean cdata = false;

					Class<?> targetClass = null;

					@Override
					public void startNode(String name, @SuppressWarnings("rawtypes") Class clazz) {
						super.startNode(name, clazz);
						// 业务处理，对于用XStreamCDATA标记的Field，需要加上CDATA标签
						if (!name.equals("xml")) {// 代表当前处理节点是class，用XstreamAlias把class的别名改成xml，下面的代码片段有提到
							cdata = needCDATA(targetClass, name);
						} else {
							targetClass = clazz;
						}
					}

					@Override
					protected void writeText(QuickWriter writer, String text) {
						if (cdata) {
							writer.write(cDATA(text));
						} else {
							writer.write(text);
						}
					}

				};
			}
		});
	}

	private String cDATA(String text) {
		return "<![CDATA[" + text + "]]>";
	}

	private boolean needCDATA(Class<?> targetClass, String fieldAlias) {
		boolean cdata = false;
		// first, scan self
		cdata = existsCDATA(targetClass, fieldAlias);
		if (cdata)
			return cdata;
		// if cdata is false, scan supperClass until java.lang.Object
		Class<?> superClass = targetClass.getSuperclass();
		while (!superClass.equals(Object.class)) {
			cdata = existsCDATA(superClass, fieldAlias);
			if (cdata) {
				return cdata;
			}
			superClass = superClass.getSuperclass();
		}
		return false;
	}

	private boolean existsCDATA(Class<?> clazz, String fieldAlias) {
		// scan fields
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			// 1. exists XStreamCDATA
			if (field.getAnnotation(XStreamCDATA.class) != null) {
				XStreamAlias xStreamAlias = field.getAnnotation(XStreamAlias.class);
				// 2. exists XStreamAlias
				if (null != xStreamAlias) {
					if (fieldAlias.equals(xStreamAlias.value()))// matched
						return true;
				} else {// not exists XStreamAlias
					if (fieldAlias.equals(field.getName()))
						return true;
				}
			}
		}
		return false;
	}

}
