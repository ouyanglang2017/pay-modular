package com.startai.wechatpay;

import com.startai.util.DomParser;
import com.startai.util.MoneyCurrency;
import com.startai.util.RequestSender;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.security.MD5Encoder;
import org.dom4j.DocumentException;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class WechatpayHelper {
    @Autowired
    private WechatpayConfig wechatpayConfig;

    /**
     * 微信App支付统一下单
     *      expireTime失效时间建议设置为startTime 1小时后
     */
    public Map<String, String> generateAppPayOrder(String body, String outTradNo, String totalAmount, String userIp, String startTime, String expireTime) throws IOException, DocumentException {
        // 1. 组织参数
        SortedMap<String,String> params = new TreeMap<String,String>();
        params.put("appid", wechatpayConfig.getAppId());
        params.put("mch_id", wechatpayConfig.getMchId());
        params.put("device_info", wechatpayConfig.getDeviceInfo());
        params.put("sign_type", wechatpayConfig.getSignType());
        String noncestr = createNoncestr();
        params.put("nonce_str", noncestr);
        params.put("body", body);
        params.put("detail", "");
        params.put("out_trade_no", outTradNo);
        params.put("fee_type", MoneyCurrency.CNY);
        params.put("total_fee", totalAmount);
        params.put("trade_type", wechatpayConfig.getTradeType());
        params.put("notify_url", wechatpayConfig.getNotifyUrl());
        params.put("spbill_create_ip", userIp);
        params.put("time_start", startTime);
        params.put("time_expire", expireTime);
        params.put("goods_tag", "");
        params.put("limit_pay", wechatpayConfig.getLimitPay());
        params.put("scene_info", "");
        // 签名
        params.put("sign", createSign(params));

        // 2. 转化为xml
        String str = DomParser.map2Str(params);

        // 3. 调用微信统一下单API
        String response = RequestSender.sendByHttps(wechatpayConfig.getOrderUrl(), str);

        // 4. 处理返回
        Map<String, String> map = DomParser.str2Map(response);

        SortedMap<String,String> appParams = new TreeMap<String,String>();
        appParams.put("appid", wechatpayConfig.getAppId());
        appParams.put("partnerid", wechatpayConfig.getMchId());
        appParams.put("prepayid", map.get("prepay_id"));
        appParams.put("package", "Sign=WXPay");
        appParams.put("noncestr", noncestr);
        appParams.put("timestamp", System.currentTimeMillis()+"");
        appParams.put("sign", createSign(appParams));

        // 5. 返回给调用者
        return appParams;
    }

    /**
     * 异步通知验签
     */
    public Map<String, Object> checkSign4AsyncNotice(String requestBody) throws DocumentException {
        // 参数组织
        Map<String, String> map = DomParser.str2Map(requestBody);
        SortedMap<String,String> sortedMap = new TreeMap<String,String>();
        for (String key : map.keySet()) {
            sortedMap.put(key, map.get(key));
        }

        // 验签
        boolean flag = checkSign(sortedMap);

        HashMap<String, Object> rtnMap = new HashMap<>();
        rtnMap.put("flag", flag);
        rtnMap.put("map", rtnMap);
        return rtnMap;
    }

    /**
     * 发起退款(统一收单交易退款)
     */
    public Map<String,String> refund(String transactionId, String outTradeNo, String outRefundNo, Integer totalFee, int refundFee) throws IOException, DocumentException {
        // 1. 组织参数
        SortedMap<String,String> params = new TreeMap<String,String>();
        params.put("appid", wechatpayConfig.getAppId());
        params.put("mch_id", wechatpayConfig.getMchId());
        params.put("sign_type", wechatpayConfig.getSignType());
        params.put("transaction_id", transactionId);
        params.put("out_trade_no", outTradeNo);
        params.put("out_refund_no", outRefundNo);
        params.put("total_fee", totalFee+"");
        params.put("refund_fee", refundFee+"");
        params.put("refund_fee_type", MoneyCurrency.CNY);
        params.put("refund_desc", "正常退款");
        params.put("notify_url", wechatpayConfig.getNotifyUrl());
        String noncestr = createNoncestr();
        params.put("nonce_str", noncestr);
        params.put("sign", createSign(params));

        // 2. 转成xml字符串
        String xmlStr = DomParser.map2Str(params);

        // 3. 调用微信退款API
        String response = RequestSender.sendByHttps(wechatpayConfig.getRefundUrl(), xmlStr);

        // 4. 返回调用结果
        return DomParser.str2Map(response);
    }

    /**
     * 验签
     */
    private boolean checkSign(SortedMap<String,String> params){
        String mySign = createSign(params);
        String paramSign = params.get("sign");
        if (mySign.equals(paramSign)) {
            return true;
        }else {
            return false;
        }
    }

    /**
     * 生成签名
     */
    private String createSign(SortedMap<String,String> parameters){
        StringBuffer sb = new StringBuffer();
        for (String key : parameters.keySet()) {
            String value = parameters.get(key);
            // value为空不参与
            if (StringUtils.isEmpty(value)) {
                continue;
            }
            // key=sign不参与
            if ("sign".equalsIgnoreCase(key)) {
                continue;
            }
            sb.append( key.toLowerCase() + "=" + value + "&");
        }
        // 最后拼接api_key
        sb.append("key="+ wechatpayConfig.getApiKey());
        // md5运算并转大写
        return MD5Encoder.encode(sb.toString().getBytes(Charset.forName("UTF-8"))).toUpperCase();
    }

    /**
     * 生成随机Noncestr
     */
    private String createNoncestr(){
        return RandomStringUtils.random(32);
    }


}
