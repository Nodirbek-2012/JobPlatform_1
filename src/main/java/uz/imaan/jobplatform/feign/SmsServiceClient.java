package uz.imaan.jobplatform.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "sms-service",
        url = "${external.sms-service.url:https://notify.eskiz.uz/api}",
        configuration = FeignConfig.class
)
public interface SmsServiceClient {

    @PostMapping("/message/sms/send")
    SmsClientDto.SmsSendResponse sendSms(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody SmsClientDto.SmsSendRequest request
    );
}