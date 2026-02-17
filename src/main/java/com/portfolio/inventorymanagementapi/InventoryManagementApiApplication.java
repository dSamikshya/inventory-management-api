package com.portfolio.inventorymanagementapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
//@EnableCaching
public class InventoryManagementApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryManagementApiApplication.class, args);
    }

}
