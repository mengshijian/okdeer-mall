package com.okdeer.mall.ele.sign;

import com.okdeer.mall.ele.util.MD5Utils;
import com.okdeer.mall.ele.util.URLUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;
import java.util.Set;

/**
 * 签名 获取token
 */
public class OpenSignHelper {
    private static final Log logger = LogFactory.getLog(OpenSignHelper.class);

    /**
     * 请求token时生成签名
     *
     * @return
     */
    public static String generateSign(String appId, String salt, String secretKey) throws Exception {
        StringBuilder seed = new StringBuilder();
        seed.append("app_id=").append(appId).append("&salt=").append(salt).append("&secret_key=").append(secretKey);
        String sign = "";
        String encodeString = URLUtils.getInstance().urlEncode(seed.toString());
        sign = MD5Utils.getMD5Code(encodeString);
        //logger.info(String.format("querySting: %s, encodeString: %s, sign: %s", seed.toString(), encodeString, sign));
        return sign;
    }

    /**
     * 业务请求生成签名
     */
    public static String generateBusinessSign(Map<String, Object> sigStr) {
        StringBuilder seed = new StringBuilder();
        Set<String> set = sigStr.keySet();
        for (String key : set) {
            seed.append(key).append("=").append(sigStr.get(key)).append("&");
        }
        String queryString = StringUtils.stripEnd(seed.toString(), "&");
        //logger.info(String.format("query string is %s", queryString));
        return MD5Utils.getMD5Code(queryString);
    }
}
