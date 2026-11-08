package ai.riskmandate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusTest {
    @Test fun deviceOwnerIsFullyManaged() =
        assertEquals(Management.FULLY_MANAGED, DeviceStatus.from(true, false, null).management)

    @Test fun deviceOwnerWinsOverWorkProfile() =
        assertEquals(Management.FULLY_MANAGED, DeviceStatus.from(true, true, null).management)

    @Test fun workProfile() = assertEquals(Management.WORK_PROFILE, DeviceStatus.from(false, true, null).management)

    @Test fun nothingDetected() = assertEquals(Management.NOT_DETECTED, DeviceStatus.from(false, false, null).management)

    @Test fun labelIsCarried() = assertEquals("Desk 1", DeviceStatus.from(false, false, "Desk 1").label)
}
