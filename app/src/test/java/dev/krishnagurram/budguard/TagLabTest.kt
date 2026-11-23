package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.tracking.SamsungFind
import dev.krishnagurram.budguard.tracking.TagLab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TagLabTest {

    @Test
    fun recognisesSamsungTagServices() {
        assertEquals(TagLab.FD5A, TagLab.serviceName("0000fd5a-0000-1000-8000-00805f9b34fb"))
        assertEquals(TagLab.FD59, TagLab.serviceName("0000FD59-0000-1000-8000-00805F9B34FB"))
        assertNull(TagLab.serviceName("0000fe9f-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun likelyYoursIsStrongestSteadyRecentTag() {
        var near = TagLab.update(null, "AA", TagLab.FD5A, -45, "01", 0)
        var far = TagLab.update(null, "BB", TagLab.FD5A, -80, "02", 0)
        repeat(6) { i ->
            near = TagLab.update(near, "AA", TagLab.FD5A, -44, if (i == 3) "09" else "01", 1000L * i)
            far = TagLab.update(far, "BB", TagLab.FD5A, -79, "02", 1000L * i)
        }
        assertEquals("AA", TagLab.likelyYours(listOf(near, far), 6_000))
        assertEquals(2, near.dataChanges)
        assertNull(TagLab.likelyYours(listOf(near, far), 60_000))
    }

    @Test
    fun parsesDeviceListWithDoubleEscapedNames() {
        val json = """{"deviceList":[{"dvceID":"d1","usrId":"u1","modelName":"Krishna&amp;#39;s Wallet","deviceTypeCode":"TAG","modelID":"EI-T5600"},
            {"dvceID":"d2","usrId":"u1","modelName":"Fold","deviceTypeCode":"PHONE","modelID":"SM-F971U1"}]}"""
        val devices = SamsungFind.parseDevices(json)
        assertEquals(2, devices.size)
        assertEquals("Krishna's Wallet", devices[0].name)
        assertEquals(true, devices[0].isTag)
        assertEquals(false, devices[1].isTag)
    }

    @Test
    fun picksNewestUnencryptedFixAndBattery() {
        val device = SamsungFind.Device("d1", "u1", "Wallet", "TAG", "EI-T5600")
        val json = """{"operation":[
            {"oprnType":"CHECK_CONNECTION","battery":"MEDIUM"},
            {"oprnType":"LASTLOC","latitude":"33.38","longitude":"-112.17","horizontalUncertainty":"30","verticalUncertainty":"40","extra":{"gpsUtcDt":"20261123150000"}},
            {"oprnType":"LOCATION","latitude":"33.40","longitude":"-112.10","horizontalUncertainty":"6","extra":{"gpsUtcDt":"20261123160500"}},
            {"oprnType":"OFFLINE_LOC","encLocation":{"encrypted":true}}
        ]}"""
        val st = SamsungFind.parseStatus(device, json)
        assertNotNull(st.fix)
        val fix = st.fix!!
        assertEquals(33.40, fix.lat, 1e-9)
        assertEquals("LOCATION", fix.kind)
        assertEquals(6.0, fix.accuracyM!!, 1e-9)
        assertEquals(50, st.battery)
        assertNull(st.note)
    }

    @Test
    fun onlyEncryptedLocationsGiveANote() {
        val device = SamsungFind.Device("d1", "u1", "Wallet", "TAG", "EI-T5600")
        val st = SamsungFind.parseStatus(device, """{"operation":[{"oprnType":"OFFLINE_LOC","encLocation":{"encrypted":true}}]}""")
        assertNull(st.fix)
        assertEquals("Samsung only returned encrypted locations (1)", st.note)
    }

    @Test
    fun accuracyAndDates() {
        assertEquals(50.0, SamsungFind.accuracy("30", "40")!!, 1e-9)
        assertEquals(1795435200000L, SamsungFind.parseDate("20261123120000"))
        assertNull(SamsungFind.parseDate("bad"))
    }
}
