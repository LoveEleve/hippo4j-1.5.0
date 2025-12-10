package com.wjcoder.config;

import cn.hippo4j.core.executor.DynamicThreadPoolWrapper;
import cn.hippo4j.core.executor.SpringDynamicThreadPool;
import cn.hippo4j.core.executor.support.ThreadPoolBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Hippo4j 动态线程池配置
 */
@Configuration
public class ThreadPoolConfig {

    @Value("${threadPool.produce}")
    private String producePoolName;

    @Value("${threadPool.consume}")
    private String consumePoolName;

//    /**
//     * 创建动态线程池：方式一
//     */
//    @Bean
//    public DynamicThreadPoolWrapper messageConsumePool() {
//        return new DynamicThreadPoolWrapper(consumePoolName);
//    }
//
//    /**
//     * 创建动态线程池：方式二
//     */
//    @SpringDynamicThreadPool
//    public ThreadPoolExecutor messageProducePool() {
//        return ThreadPoolBuilder.builder()
//                .threadFactory(producePoolName)
//                .dynamicPool()
//                .threadPoolId("test-bug")
//                .build();
//    }
//
//    @SpringDynamicThreadPool
//    public ThreadPoolExecutor messageBugPool() {
//        return ThreadPoolBuilder.builder()
//                .threadFactory("test-bug-2")
//                .dynamicPool()
//                .threadPoolId("test-bug-2")
//                .build();
//    }

//    @SpringDynamicThreadPool
//    public ThreadPoolExecutor BugPool() {
//        return ThreadPoolBuilder.builder()
//                .threadFactory("test-bug-3")
//                .dynamicPool()
//                .threadPoolId("test-bug-3")
//                .build();
//    }

    @SpringDynamicThreadPool
    public ThreadPoolExecutor BugFixPool() {
        return ThreadPoolBuilder.builder()
                .threadFactory("bug-fix")
                .dynamicPool()
                .threadPoolId("bug-fix")
                .build();
    }


}
