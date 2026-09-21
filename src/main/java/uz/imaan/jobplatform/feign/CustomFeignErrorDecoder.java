package uz.imaan.jobplatform.feign;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.error("Feign Client xatolik berdi. Metod: {}, Status: {}", methodKey, status);

        return switch (status) {
            case 502 -> new ExternalServiceException(
                    "Tashqi servis vaqtincha ishlamayapti (502 Bad Gateway). Iltimos, keyinroq urinib ko'ring.", status);
            case 504 -> new ExternalServiceException(
                    "Tashqi servisdan javob kutish vaqti tugadi (504 Gateway Timeout).", status);
            case 400 -> new ExternalServiceException(
                    "Tashqi servisga yuborilgan so'rov xato (400 Bad Request).", status);
            case 401, 403 -> new ExternalServiceException(
                    "Tashqi servis avtorizatsiyadan o'tkazmadi (Ruxsat yo'q).", status);
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }
}