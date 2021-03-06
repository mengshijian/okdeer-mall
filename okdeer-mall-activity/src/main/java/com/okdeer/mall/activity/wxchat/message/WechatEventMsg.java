
package com.okdeer.mall.activity.wxchat.message;

import com.okdeer.mall.activity.wxchat.annotation.XStreamCDATA;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("xml")
public class WechatEventMsg extends WechatMsg {

	/**
	 * 事件类型，CLICK、VIEW Event
	 */
	@XStreamAlias("Event")
	@XStreamCDATA
	private String event;

	/**
	 * 事件KEY值，与自定义菜单接口中KEY值对应 EventKey
	 */
	@XStreamAlias("EventKey")
	@XStreamCDATA
	private String eventKey;

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getEventKey() {
		return eventKey;
	}

	public void setEventKey(String eventKey) {
		this.eventKey = eventKey;
	}

}
