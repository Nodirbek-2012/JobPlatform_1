package uz.imaan.jobplatform.feign;

public class SmsClientDto {
    public record SmsSendRequest(
            String phoneNumber,
            String message
    ) {}

    public record SmsSendResponse(
            String status,
            String messageId
    ) {}
}