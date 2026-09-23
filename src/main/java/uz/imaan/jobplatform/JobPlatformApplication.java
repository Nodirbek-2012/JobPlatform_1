package uz.imaan.jobplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class JobPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobPlatformApplication.class, args);
    }

}