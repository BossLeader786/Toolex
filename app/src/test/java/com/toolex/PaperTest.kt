package com.toolex

import org.junit.Assert.*
import org.junit.Test

class PaperTest {
    @Test fun unitsUseDecimalBytes() {
        assertEquals(1250L,Paper(target="1.25").limit())
        assertEquals(1_000_000L,Paper(target="1",unit="MB").limit())
        assertNull(Paper().limit())
        for (bad in listOf("NaN","0","Infinity","no","-3"))
            assertTrue(runCatching { Paper(target=bad).limit() }.isFailure)
    }
    @Test fun geometryPreservesPhotoAspectAcrossFormats() {
        for (size in listOf("A4","A5","Letter","Original")) for (fit in listOf("Fit","Fill"))
            for ((w,h) in listOf(1 to 5,320 to 200,300 to 300)) {
                val p=layout(w,h,Paper(size=size,fit=fit,margin=32))
                assertEquals(w.toDouble()/h,p.dw/p.dh,.00001)
                val maxW=p.width-2*p.margin
                val maxH=p.height-2*p.margin
                if(fit=="Fit") assertTrue(p.dw<=maxW+.001 && p.dh<=maxH+.001)
                else assertTrue(p.dw>=maxW-.001 && p.dh>=maxH-.001)
            }
    }
    @Test fun orientationAndOriginalSize() {
        val p=layout(400,200,Paper(size="Original",margin=0))
        assertEquals(300.0,p.width,0.0);assertEquals(150.0,p.height,0.0)
        val l=layout(100,300,Paper(orientation="Landscape"))
        assertTrue(l.width>l.height)
    }
}
