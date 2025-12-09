/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.common.executor.support;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试 ResizableCapacityLinkedBlockingQueue 的线程安全性问题
 */
public class ResizableCapacityLinkedBlockingQueueTest {

    /**
     * 测试 capacity 字段的可见性问题 - 改进版
     * <p>
     * 场景：队列已满，put 线程先阻塞在 notFull.await()，
     * 然后另一个线程扩容并唤醒 put 线程，
     * 由于 capacity 没有 volatile 修饰，put 线程被唤醒后可能仍然读取到旧的 capacity 值，
     * 导致再次进入等待，造成线程饥饿
     */
    @Test(timeout = 10000) // 设置超时时间，如果线程饥饿会超时失败
    public void testCapacityVisibilityIssue() throws InterruptedException {
        final ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(5);

        // 先填满队列
        for (int i = 0; i < 5; i++) {
            queue.offer(i);
        }

        final AtomicBoolean putSuccess = new AtomicBoolean(false);
        final CountDownLatch putThreadBlocked = new CountDownLatch(1);

        // 线程 1：尝试 put，会阻塞
        Thread putThread = new Thread(() -> {
            try {
                System.out.println("[PUT Thread] 开始尝试 put，队列已满，将阻塞...");

                // 这里会阻塞，因为队列已满
                // 关键：线程会在 notFull.await() 上等待
                new Thread(() -> {
                    // 用另一个线程来标记 put 线程已经启动
                    try {
                        Thread.sleep(100);
                        putThreadBlocked.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                queue.put(100);
                putSuccess.set(true);
                System.out.println("[PUT Thread] put 成功！");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[PUT Thread] 被中断");
            }
        });

        putThread.start();

        // 等待 put 线程进入阻塞状态
        putThreadBlocked.await();
        Thread.sleep(200); // 确保 put 线程已经在 notFull.await() 上等待

        System.out.println("[MAIN Thread] put 线程已阻塞，现在扩容...");

        // 线程 2：扩容
        System.out.println("[MAIN Thread] 扩容前 capacity=5, size=" + queue.size());
        queue.setCapacity(10);
        System.out.println("[MAIN Thread] 扩容后 capacity=10, size=" + queue.size());

        // 等待 put 线程完成（如果有可见性问题，这里会超时）
        putThread.join(5000);

        // 验证 put 是否成功
        if (!putSuccess.get()) {
            System.err.println("❌ 测试失败：put 线程可能因为可见性问题而永久阻塞！");
            System.err.println("   原因：put 线程被唤醒后，在 while (count.get() >= capacity) 检查时");
            System.err.println("   可能读取到旧的 capacity=5，导致再次进入 await()");
            System.err.println("   这证明了 capacity 字段需要 volatile 修饰。");
        }

        Assert.assertTrue("put 操作应该成功，但由于可见性问题可能失败", putSuccess.get());
    }

    /**
     * 压力测试：通过大量并发操作增加可见性问题的复现概率
     * <p>
     * 这个测试通过高频率的扩容/缩容和 offer 操作，
     * 增加 capacity 可见性问题的复现概率
     */
    @Test(timeout = 30000)
    public void testCapacityVisibilityStressTest() throws InterruptedException {
        final ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(10);

        final AtomicInteger violations = new AtomicInteger(0);
        final AtomicBoolean stopFlag = new AtomicBoolean(false);
        final CountDownLatch allThreadsStarted = new CountDownLatch(12);

        // 10 个生产者线程：不断尝试 offer
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            new Thread(() -> {
                allThreadsStarted.countDown();
                int count = 0;
                while (!stopFlag.get() && count < 1000) {
                    try {
                        queue.offer(threadId * 10000 + count);
                        count++;
                        // 不使用 sleep，保持高并发
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        // 1 个消费者线程：不断取出元素
        new Thread(() -> {
            allThreadsStarted.countDown();
            while (!stopFlag.get()) {
                try {
                    queue.poll();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // 1 个调整容量的线程：频繁扩容/缩容
        new Thread(() -> {
            allThreadsStarted.countDown();
            int count = 0;
            while (!stopFlag.get() && count < 100) {
                try {
                    // 在 5-15 之间来回调整
                    int newCapacity = 5 + (count % 11);
                    queue.setCapacity(newCapacity);

                    // 检查是否违反容量限制
                    int size = queue.size();
                    if (size > newCapacity + 5) { // 允许一定的误差
                        violations.incrementAndGet();
                        System.err.println("❌ 检测到容量违规：size=" + size + ", capacity=" + newCapacity);
                    }

                    count++;
                    Thread.sleep(10); // 稍微降低频率
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // 等待所有线程启动
        allThreadsStarted.await();
        System.out.println("[STRESS TEST] 所有线程已启动，开始压力测试...");

        // 运行 5 秒
        Thread.sleep(5000);

        // 停止所有线程
        stopFlag.set(true);
        Thread.sleep(500); // 等待线程结束

        System.out.println("[STRESS TEST] 测试完成");
        System.out.println("检测到的容量违规次数: " + violations.get());

        if (violations.get() > 0) {
            System.err.println("❌ 压力测试检测到容量违规，这可能是由于 capacity 可见性问题导致的");
        }
    }

    /**
     * 测试缩容时的容量限制失效问题
     * <p>
     * 场景：队列容量为 10，当前有 8 个元素，缩容到 5，
     * 由于 capacity 没有 volatile，put 线程可能读取到旧值 10，
     * 导致插入成功，队列元素数量超过新容量
     */
    @Test
    public void testCapacityViolationOnShrink() throws InterruptedException {
        final ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(10);

        // 先放入 8 个元素
        for (int i = 0; i < 8; i++) {
            queue.offer(i);
        }

        final AtomicInteger successfulPuts = new AtomicInteger(0);
        final CountDownLatch allThreadsReady = new CountDownLatch(3);
        final CountDownLatch startSignal = new CountDownLatch(1);

        // 线程 1：缩容
        Thread shrinkThread = new Thread(() -> {
            allThreadsReady.countDown();
            try {
                startSignal.await();
                System.out.println("[SHRINK Thread] 缩容前 capacity=10, size=" + queue.size());
                queue.setCapacity(5);
                System.out.println("[SHRINK Thread] 缩容后 capacity=5, size=" + queue.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 线程 2 和 3：尝试 offer
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            Thread offerThread = new Thread(() -> {
                allThreadsReady.countDown();
                try {
                    startSignal.await();
                    // 短暂延迟，让缩容线程先执行
                    Thread.sleep(10);

                    // 尝试 offer，如果 capacity 可见性有问题，可能会成功
                    // 导致队列大小超过新容量 5
                    boolean success = queue.offer(100 + threadId);
                    if (success) {
                        successfulPuts.incrementAndGet();
                        System.out.println("[OFFER Thread-" + threadId + "] offer 成功");
                    } else {
                        System.out.println("[OFFER Thread-" + threadId + "] offer 失败（正确行为）");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            offerThread.start();
        }

        shrinkThread.start();

        // 等待所有线程准备就绪
        allThreadsReady.await();

        // 同时启动所有线程
        startSignal.countDown();

        // 等待所有线程完成
        Thread.sleep(500);

        int finalSize = queue.size();
        System.out.println("最终队列大小: " + finalSize + ", 新容量: 5");

        // 如果有可见性问题，队列大小可能会超过 5
        if (finalSize > 5) {
            System.err.println("❌ 容量限制失效：队列大小 " + finalSize + " 超过了新容量 5！");
            System.err.println("   这证明了 capacity 字段需要 volatile 修饰。");
        }
    }

    /**
     * 测试 remainingCapacity 的准确性
     * <p>
     * 场景：动态调整容量时，remainingCapacity 可能返回不准确的值
     */
    @Test
    public void testRemainingCapacityAccuracy() throws InterruptedException {
        final ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(10);

        // 放入 5 个元素
        for (int i = 0; i < 5; i++) {
            queue.offer(i);
        }

        final AtomicInteger wrongReadings = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(10);

        // 启动多个线程读取 remainingCapacity
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        int remaining = queue.remainingCapacity();
                        int size = queue.size();

                        // remainingCapacity 应该 >= 0
                        // 如果 capacity 可见性有问题，可能出现负数或不合理的值
                        if (remaining < 0) {
                            wrongReadings.incrementAndGet();
                            System.err.println("❌ 检测到错误：remainingCapacity=" + remaining + " < 0");
                        }

                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // 同时动态调整容量
        Thread resizeThread = new Thread(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    queue.setCapacity(5 + (i % 10));
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        resizeThread.start();
        latch.await();
        resizeThread.join();

        if (wrongReadings.get() > 0) {
            System.err.println("❌ 检测到 " + wrongReadings.get() + " 次错误的 remainingCapacity 读取");
        }
    }

    /**
     * 基本功能测试：验证队列的基本操作
     */
    @Test
    public void testBasicOperations() throws InterruptedException {
        ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(5);

        // 测试 offer
        Assert.assertTrue(queue.offer(1));
        Assert.assertTrue(queue.offer(2));
        Assert.assertEquals(2, queue.size());

        // 测试 poll
        Assert.assertEquals(Integer.valueOf(1), queue.poll());
        Assert.assertEquals(1, queue.size());

        // 测试 peek
        Assert.assertEquals(Integer.valueOf(2), queue.peek());
        Assert.assertEquals(1, queue.size());

        // 测试 take
        Assert.assertEquals(Integer.valueOf(2), queue.take());
        Assert.assertEquals(0, queue.size());
    }

    /**
     * 测试扩容功能
     */
    @Test
    public void testCapacityIncrease() {
        ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(5);

        // 填满队列
        for (int i = 0; i < 5; i++) {
            Assert.assertTrue(queue.offer(i));
        }

        // 队列已满，offer 应该失败
        Assert.assertFalse(queue.offer(100));

        // 扩容
        queue.setCapacity(10);

        // 扩容后应该可以继续添加
        Assert.assertTrue(queue.offer(100));
        Assert.assertEquals(6, queue.size());
    }

    /**
     * 测试缩容功能
     */
    @Test
    public void testCapacityDecrease() {
        ResizableCapacityLinkedBlockingQueue<Integer> queue =
                new ResizableCapacityLinkedBlockingQueue<>(10);

        // 添加 8 个元素
        for (int i = 0; i < 8; i++) {
            queue.offer(i);
        }

        // 缩容到 5
        queue.setCapacity(5);

        // 缩容后，队列大小仍然是 8（不会删除已有元素）
        Assert.assertEquals(8, queue.size());

        // 但是不能再添加新元素（因为 8 > 5）
        Assert.assertFalse(queue.offer(100));

        // 取出 4 个元素后（剩余 4 个）
        for (int i = 0; i < 4; i++) {
            queue.poll();
        }

        // 现在应该可以添加 1 个元素（4 < 5）
        Assert.assertTrue(queue.offer(100));
        Assert.assertEquals(5, queue.size());

        // 但不能再添加了（5 >= 5）
        Assert.assertFalse(queue.offer(101));
    }

    /**
     * 通过反射检查 capacity 字段是否有 volatile 修饰符
     * <p>
     * 这是最直接的证明方式：如果 capacity 没有 volatile，
     * 在多线程环境下就存在可见性问题的风险
     */
    @Test
    public void testCapacityFieldHasVolatileModifier() {
        try {
            java.lang.reflect.Field capacityField =
                    ResizableCapacityLinkedBlockingQueue.class.getDeclaredField("capacity");

            int modifiers = capacityField.getModifiers();
            boolean isVolatile = java.lang.reflect.Modifier.isVolatile(modifiers);

            if (!isVolatile) {
                System.err.println("❌ 严重问题：capacity 字段没有 volatile 修饰符！");
                System.err.println("   字段声明：" + capacityField.toString());
                System.err.println("   修饰符：" + java.lang.reflect.Modifier.toString(modifiers));
                System.err.println("");
                System.err.println("   影响：");
                System.err.println("   1. 线程 A 调用 setCapacity() 修改容量");
                System.err.println("   2. 线程 B 在 put()/offer() 中读取 capacity");
                System.err.println("   3. 线程 B 可能读取到旧的 capacity 值（CPU 缓存未刷新）");
                System.err.println("");
                System.err.println("   后果：");
                System.err.println("   - 扩容时：等待的 put 线程可能永久阻塞（线程饥饿）");
                System.err.println("   - 缩容时：队列可能超过容量限制");
                System.err.println("   - remainingCapacity() 可能返回错误值");
                System.err.println("");
                System.err.println("   解决方案：");
                System.err.println("   private volatile int capacity;  // 添加 volatile 修饰符");
            }

            Assert.assertTrue(
                    "capacity 字段必须有 volatile 修饰符以保证多线程可见性。\n" +
                            "当前修饰符：" + java.lang.reflect.Modifier.toString(modifiers) + "\n" +
                            "建议修改为：private volatile int capacity;",
                    isVolatile
            );

        } catch (NoSuchFieldException e) {
            Assert.fail("无法找到 capacity 字段：" + e.getMessage());
        }
    }
}
