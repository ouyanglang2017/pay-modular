package com.startai.alipay;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class AlipayHelper {
    @Autowired
    private AlipayConfig alipayConfig;

    private AlipayClient alipayClient;

    @PostConstruct
    private void init() {
        // 实例化客户端
        alipayClient = new DefaultAlipayClient(
                alipayConfig.getServerUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getAppPrivateKey(),
                alipayConfig.getFormat(),
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );
    }

    /**
     * 支付宝下单
     */
    public String placeOrder(String subject, String body, String outTradNo, String totalAmount) throws AlipayApiException {
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setBody(body); // 非必填
        model.setSubject(subject);
        model.setOutTradeNo(outTradNo);
        model.setTimeoutExpress("30m");
        model.setTotalAmount(totalAmount);
        model.setProductCode("QUICK_MSECURITY_PAY");
        request.setBizModel(model);
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        AlipayTradeAppPayResponse response = alipayClient.sdkExecute(request);
        return response.getBody();
    }

    /**
     * 订单查询
     */
    public AlipayTradeQueryResponse query(String outTradeNo, String tradeNo) throws AlipayApiException {

        JSONObject params = new JSONObject();
        if (outTradeNo == null) {
            params.put("trade_no", tradeNo);
        }
        if (tradeNo == null) {
            params.put("out_trade_no", outTradeNo);
        }

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(params.toJSONString());
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        if (response.isSuccess()) {
            return response;
        } else {
            //调用失败处理
            return null;
        }
    }

    /**
     * 发起退款(统一收单交易退款)
     * :返回空代表发生异常
     * ：受理成功或失败需要从response里面判断
     */
    public AlipayTradeRefundResponse refund(
            String out_trade_no, String trade_no, BigDecimal refund_amount,
            String refund_reason, String out_request_no) throws AlipayApiException {

        JSONObject params = new JSONObject();
        params.put("out_trade_no", out_trade_no);
        params.put("trade_no", trade_no);
        params.put("refund_amount", refund_amount);
        params.put("refund_reason", refund_reason);
        params.put("out_request_no", out_request_no);

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent(params.toJSONString());
        AlipayTradeRefundResponse response = null;
        response = alipayClient.execute(request);
        if (response.isSuccess()) {
            return response;
        } else {
            throw new AlipayApiException("接口调用失败");
        }

    }

    /**
     * 异步通知验签
     */
    public Map<String, Object> checkSign4AsyncNotice(HttpServletRequest request) throws AlipayApiException {
        // 0. 构造返回
        Map<String, Object> result = new HashMap<String, Object>();

        // 1. 异步通知参数解析
        SortedMap<String, String> params = new TreeMap<String, String>();
        Map requestParams = request.getParameterMap();
        for (Object key : requestParams.keySet()) {
            String[] values = (String[]) requestParams.get(key);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            params.put((String) key, valueStr);
        }

        result.put("paramsMap", params);

        // 2. 验签 :v1计算时，会自动去除sign和signType， 传递归来的sign也会自动做base64解码
        boolean flag = AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(), "UTF-8", alipayConfig.getSignType());
        result.put("flag", flag);

        return result;
    }

}
