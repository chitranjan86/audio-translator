package com.audixt.audiotranslator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AudiotranslatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(AudiotranslatorApplication.class, args);
	}

}
