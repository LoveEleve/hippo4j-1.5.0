package cn.hippo4j.common.executor.support;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Test case to verify that the capacity field in ResizableCapacityLinkedBlockingQueue
 * is properly declared as volatile to ensure thread-safety and visibility across threads.
 * 
 * This test addresses the issue where capacity can be modified by setCapacity() without
 * holding any lock, while being read by put()/offer() methods under putLock. Without
 * volatile, there's no happens-before relationship established, which violates the
 * Java Memory Model (JMM) requirements.
 */
public class CapacityVolatileTest {

    /**
     * Test that the capacity field is declared as volatile.
     * This is critical because:
     * 1. setCapacity() modifies capacity without holding any lock
     * 2. put()/offer() read capacity while holding putLock
     * 3. Without volatile, no happens-before relationship exists between write and read
     * 4. This can lead to visibility issues, thread starvation, or capacity constraint violations
     * 
     * Reference: JDK's ThreadPoolExecutor.maximumPoolSize uses volatile for the same pattern
     */
    @Test
    public void testCapacityFieldIsVolatile() throws NoSuchFieldException {
        // Get the capacity field via reflection
        Field capacityField = ResizableCapacityLinkedBlockingQueue.class.getDeclaredField("capacity");
        
        // Check if the field is declared as volatile
        boolean isVolatile = Modifier.isVolatile(capacityField.getModifiers());
        
        Assert.assertTrue(
            "The 'capacity' field must be declared as volatile to ensure visibility across threads. " +
            "Without volatile, modifications made by setCapacity() may not be visible to threads " +
            "executing put()/offer() methods, leading to potential thread starvation or capacity violations.",
            isVolatile
        );
    }

    /**
     * Test that capacity field has the expected modifiers (private volatile int).
     */
    @Test
    public void testCapacityFieldModifiers() throws NoSuchFieldException {
        Field capacityField = ResizableCapacityLinkedBlockingQueue.class.getDeclaredField("capacity");
        int modifiers = capacityField.getModifiers();
        
        // Verify it's private
        Assert.assertTrue("capacity field should be private", Modifier.isPrivate(modifiers));
        
        // Verify it's volatile
        Assert.assertTrue("capacity field should be volatile", Modifier.isVolatile(modifiers));
        
        // Verify it's not static
        Assert.assertFalse("capacity field should not be static", Modifier.isStatic(modifiers));
        
        // Verify it's not final
        Assert.assertFalse("capacity field should not be final (it's mutable)", Modifier.isFinal(modifiers));
        
        // Verify the type is int
        Assert.assertEquals("capacity field should be of type int", int.class, capacityField.getType());
    }

    /**
     * Functional test: Verify that capacity changes are visible across threads.
     * While this test may pass even without volatile due to lock side-effects or
     * CPU cache coherence, it serves as a functional verification.
     */
    @Test
    public void testCapacityVisibilityAcrossThreads() throws InterruptedException {
        final ResizableCapacityLinkedBlockingQueue<Integer> queue = 
            new ResizableCapacityLinkedBlockingQueue<>(5);
        
        // Fill the queue to capacity
        for (int i = 0; i < 5; i++) {
            queue.offer(i);
        }
        
        final boolean[] putThreadStarted = {false};
        final boolean[] putThreadCompleted = {false};
        final Exception[] putThreadException = {null};
        
        // Thread that will block on put() due to full queue
        Thread putThread = new Thread(() -> {
            try {
                putThreadStarted[0] = true;
                queue.put(999); // This will block until capacity is increased
                putThreadCompleted[0] = true;
            } catch (Exception e) {
                putThreadException[0] = e;
            }
        });
        
        putThread.start();
        
        // Wait for put thread to start and block
        Thread.sleep(100);
        Assert.assertTrue("Put thread should have started", putThreadStarted[0]);
        Assert.assertFalse("Put thread should be blocked", putThreadCompleted[0]);
        
        // Increase capacity - this should be visible to the put thread
        queue.setCapacity(10);
        
        // Wait for put thread to complete
        putThread.join(2000);
        
        // Verify the put operation completed successfully
        Assert.assertNull("Put thread should not throw exception", putThreadException[0]);
        Assert.assertTrue("Put thread should have completed after capacity increase", putThreadCompleted[0]);
        Assert.assertEquals("Queue should contain 6 elements", 6, queue.size());
    }

    /**
     * Test that demonstrates the importance of volatile in shrink scenario.
     * When capacity is reduced, threads reading capacity must see the new value
     * to correctly enforce the capacity constraint.
     */
    @Test
    public void testCapacityVisibilityOnShrink() throws InterruptedException {
        final ResizableCapacityLinkedBlockingQueue<Integer> queue = 
            new ResizableCapacityLinkedBlockingQueue<>(10);
        
        // Add 5 elements
        for (int i = 0; i < 5; i++) {
            queue.offer(i);
        }
        
        // Shrink capacity to 3 (less than current size)
        queue.setCapacity(3);
        
        // Verify that offer() correctly sees the new capacity and rejects new elements
        boolean offerResult = queue.offer(999);
        Assert.assertFalse("offer() should fail when queue size exceeds new capacity", offerResult);
        
        // Verify remainingCapacity reflects the new capacity
        int remaining = queue.remainingCapacity();
        Assert.assertEquals("remainingCapacity should be negative after shrinking", -2, remaining);
    }
}
