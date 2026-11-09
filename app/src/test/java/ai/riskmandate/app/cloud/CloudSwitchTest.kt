package ai.riskmandate.app.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSwitchTest {
    @Test fun offByDefaultAndOnOnlyWhenStaffTurnItOn() {
        val s = CloudSwitch()
        assertFalse(s.isOn)
        assertTrue(s.turnOn()); assertTrue(s.isOn)
        s.turnOff(); assertFalse(s.isOn)
    }

    @Test fun standModeRefusesTurningItOn() {
        val s = CloudSwitch(standMode = { true })
        assertFalse(s.turnOn()); assertFalse(s.isOn)
    }

    @Test fun standModeSwitchesItOffForGood() {
        var stand = false
        val s = CloudSwitch(standMode = { stand })
        s.turnOn(); assertTrue(s.isOn)
        stand = true
        assertFalse(s.isOn)
        stand = false
        assertFalse("it does not come back when stand mode ends: staff turn it on again", s.isOn)
        assertTrue(s.turnOn())
        assertFalse(CloudSwitch().isOn) // a restart
    }
}
