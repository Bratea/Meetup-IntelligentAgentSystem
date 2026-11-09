package com.xunwu.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.xunwu.agent.config.AuthProperties;
import com.xunwu.agent.config.XunwuProperties;

/**
 * 寻晤智能agent系统 - 启动类
 *
 * @author xunwu
 */
@SpringBootApplication
@EnableConfigurationProperties({XunwuProperties.class, AuthProperties.class})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
