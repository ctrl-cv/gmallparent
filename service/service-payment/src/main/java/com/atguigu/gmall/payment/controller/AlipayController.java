package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Resource
    AlipayService alipayService;

    @Resource
    PaymentService paymentService;

    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String aliPaySubmit(@PathVariable Long orderId){
        return alipayService.createaliPay(orderId);
    }

    @RequestMapping("callback/return")
    public String callBack(){
        return "redirect:" + AlipayConfig.return_order_url;
    }


    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String,String> paramsMap){
        System.out.println("回来了！");

        String outTradeNo = paramsMap.get("out_trade_no");
        String tradeStatus = paramsMap.get("trade_status");
        boolean  signVerified = false;  //调用SDK验证签名

        try {
            signVerified = AlipaySignature.rsaCheckV1(paramsMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (paymentInfo == null){
            return "failure";
        }
        if (signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            if ("TRADE_FINISHED".equals(tradeStatus) || "TRADE_SUCCESS".equals(tradeStatus)){

                if ("PAID".equals(paymentInfo.getPaymentStatus()) || "ClOSED".equals(paymentInfo.getPaymentStatus())){
                    return "failure";
                }
                paymentService.paySuccess(outTradeNo,PaymentType.ALIPAY.name(),paramsMap);
                return "success";
            }
        } else {
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

    // 发起退款！http://localhost:8205/api/payment/alipay/refund/20
    @GetMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable Long orderId){
        Boolean flag = alipayService.refund(orderId);
        return Result.ok(flag);
    }

    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        return alipayService.closePay(orderId);
    }

    @GetMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        return alipayService.checkPayment(orderId);
    }

    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (paymentInfo != null){
            return paymentInfo;
        }
        return null;
    }
}
