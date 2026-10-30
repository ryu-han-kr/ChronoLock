package com.chronolock.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBatchTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test fun heldUntilTheNextDelivery_andAfterNineUntilNoon() {
        assertEquals(at(30, 12), NotificationBatch.nextDelivery(at(30, 8, 15)))
        assertEquals(at(30, 18), NotificationBatch.nextDelivery(at(30, 12)))
        assertEquals(at(30, 21), NotificationBatch.nextDelivery(at(30, 20, 59)))
        assertEquals(at(31, 12), NotificationBatch.nextDelivery(at(30, 21)))
        assertEquals(at(31, 12), NotificationBatch.nextDelivery(at(30, 23, 30)))
    }
}
