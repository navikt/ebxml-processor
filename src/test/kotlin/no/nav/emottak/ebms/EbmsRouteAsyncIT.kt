package no.nav.emottak.ebms

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.ebxml.acknowledgment
import no.nav.emottak.ebms.ebxml.messageHeader
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.ebms.xml.xmlMarshaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.xmlsoap.schemas.soap.envelope.Envelope

class EbmsRouteAsyncIT : EbmsRoutFellesIT("/ebms/async") {

    @Test
    fun `Valid Payload should produce Acknowledgment`() = validationTestApp {
        val multipart = validMultipartRequest.modify(
            validMultipartRequest.parts.first() to validMultipartRequest.parts.first().modify {
                it.remove(MimeHeaders.CONTENT_ID)
                it.append(MimeHeaders.CONTENT_ID, "<contentID-validRequest>")
            }
        )
        val response = client.post("/ebms/async", multipart.asHttpRequest())
        coVerify(exactly = 1) {
            processingService.processAsync(any(), any())
        }
        val envelope = xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java)
        envelope.assertAcknowledgmen()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Valid feilsignal should be processed`() = validationTestApp {
        val feilmelding = feilmeldingWithoutSignature.modify {
            it.append(MimeHeaders.CONTENT_ID, "<contentID-validRequest>")
        }

        mockkObject(SignaturValidator.Companion)

        every {
            SignaturValidator.validate(any(), any(), any())
        } returns Unit
        val response = client.post("/ebms/async") {
            headers {
                feilmelding.headers.entries().forEach {
                    append(it.key, it.value.first())
                }
            }
            setBody(feilmelding.payload)
        }

        assertEquals("Processed", response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Valid Acknowledgment should be processed`() = validationTestApp {
        val ack = validAcknowledgment.modify {
            it[MimeHeaders.CONTENT_ID] = "<contentID-validRequest>"
        }

        mockkObject(SignaturValidator.Companion)

        every {
            SignaturValidator.validate(any(), any(), any())
        } returns Unit
        val response = client.post("/ebms/async") {
            headers {
                ack.headers.entries().forEach {
                    append(it.key, it.value.first())
                }
            }
            setBody(ack.payload)
        }

        assertEquals("Processed", response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
    }

    fun Envelope.assertAcknowledgmen() {
        assertNotNull(this.header.messageHeader())
        assertNotNull(this.header.acknowledgment())
    }

    val validAcknowledgment = Part(
        valid.modify {
            it.append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
            it[MimeHeaders.CONTENT_TYPE] = "text/xml"
            it.append(SMTPHeaders.MESSAGE_ID, "<20231128102918.35C2D1829F69@a01drvl071.adeo.no>")
        },
        """PFNPQVA6RW52ZWxvcGUgeG1sbnM6ZWI9Imh0dHA6Ly93d3cub2FzaXMtb3Blbi5vcmcvY29tbWl0
dGVlcy9lYnhtbC1tc2cvc2NoZW1hL21zZy1oZWFkZXItMl8wLnhzZCIgeG1sbnM6eHNpPSJodHRw
Oi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSIgeG1sbnM6U09BUD0iaHR0cDov
L3NjaGVtYXMueG1sc29hcC5vcmcvc29hcC9lbnZlbG9wZS8iIHhzaTpzY2hlbWFMb2NhdGlvbj0i
aHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvc29hcC9lbnZlbG9wZS8gaHR0cDovL3d3dy5vYXNp
cy1vcGVuLm9yZy9jb21taXR0ZWVzL2VieG1sLW1zZy9zY2hlbWEvZW52ZWxvcGUueHNkIGh0dHA6
Ly93d3cub2FzaXMtb3Blbi5vcmcvY29tbWl0dGVlcy9lYnhtbC1tc2cvc2NoZW1hL21zZy1oZWFk
ZXItMl8wLnhzZCBodHRwOi8vd3d3Lm9hc2lzLW9wZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtbXNn
L3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54c2QiPjxTT0FQOkhlYWRlcj48ZWI6TWVzc2FnZUhlYWRl
ciB4bWxuczpTT0FQPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2VudmVsb3BlLyIg
ZWI6dmVyc2lvbj0iMi4wIiBTT0FQOm11c3RVbmRlcnN0YW5kPSIxIiB4bWxuczplYj0iaHR0cDov
L3d3dy5vYXNpcy1vcGVuLm9yZy9jb21taXR0ZWVzL2VieG1sLW1zZy9zY2hlbWEvbXNnLWhlYWRl
ci0yXzAueHNkIj48ZWI6RnJvbT48ZWI6UGFydHlJZCBlYjp0eXBlPSJIRVIiPjExMjI4NjwvZWI6
UGFydHlJZD48ZWI6Um9sZT5VdGxldmVyZXI8L2ViOlJvbGU+PC9lYjpGcm9tPjxlYjpUbz48ZWI6
UGFydHlJZCBlYjp0eXBlPSJIRVIiPjc5NzY4PC9lYjpQYXJ0eUlkPjxlYjpSb2xlPktvbnRyb2xs
VXRiZXRhbGVyPC9lYjpSb2xlPjwvZWI6VG8+PGViOkNQQUlkPm5hdjpxYXNzOjMxODU1PC9lYjpD
UEFJZD48ZWI6Q29udmVyc2F0aW9uSWQ+Mjc4YmZlYTQtNjA5ZC00YWMxLWExYmQtY2U2MDAzMDQ5
OGI1PC9lYjpDb252ZXJzYXRpb25JZD48ZWI6U2VydmljZT51cm46b2FzaXM6bmFtZXM6dGM6ZWJ4
bWwtbXNnOnNlcnZpY2U8L2ViOlNlcnZpY2U+PGViOkFjdGlvbj5BY2tub3dsZWRnbWVudDwvZWI6
QWN0aW9uPjxlYjpNZXNzYWdlRGF0YT48ZWI6TWVzc2FnZUlkPjRlYmQ5NWMzLWJkYmYtNDA0ZS1h
MzVhLTI5MDhiZmQ4ZjkzODwvZWI6TWVzc2FnZUlkPjxlYjpUaW1lc3RhbXA+MjAyMy0xMS0yOFQx
MDoyOToxOC4wNjkxMjg4WjwvZWI6VGltZXN0YW1wPjxlYjpSZWZUb01lc3NhZ2VJZD4yMDIzMTEy
OC0xMTI4NTQtNDQyMEBxYS5lYnhtbC5uYXYubm88L2ViOlJlZlRvTWVzc2FnZUlkPjwvZWI6TWVz
c2FnZURhdGE+PC9lYjpNZXNzYWdlSGVhZGVyPjxlYjpBY2tub3dsZWRnbWVudCB4bWxuczpTT0FQ
PSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2VudmVsb3BlLyIgZWI6dmVyc2lvbj0i
Mi4wIiBTT0FQOm11c3RVbmRlcnN0YW5kPSJ0cnVlIiBTT0FQOmFjdG9yPSJ1cm46b2FzaXM6bmFt
ZXM6dGM6ZWJ4bWwtbXNnOmFjdG9yOnRvUGFydHlNU0giIHhtbG5zOmViPSJodHRwOi8vd3d3Lm9h
c2lzLW9wZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtbXNnL3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54
c2QiPjxlYjpUaW1lc3RhbXA+MjAyMy0xMS0yOFQxMDoyODo1NFo8L2ViOlRpbWVzdGFtcD48ZWI6
UmVmVG9NZXNzYWdlSWQ+MjAyMzExMjgtMTEyODU0LTQ0MjBAcWEuZWJ4bWwubmF2Lm5vPC9lYjpS
ZWZUb01lc3NhZ2VJZD48UmVmZXJlbmNlIFVSST0iIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcv
MjAwMC8wOS94bWxkc2lnIyI+PFRyYW5zZm9ybXM+PFRyYW5zZm9ybSBBbGdvcml0aG09Imh0dHA6
Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNlbnZlbG9wZWQtc2lnbmF0dXJlIj48L1RyYW5z
Zm9ybT48VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvVFIvMTk5OS9SRUMt
eHBhdGgtMTk5OTExMTYiPjxYUGF0aCB4bWxuczpTT0FQLUVOVj0iaHR0cDovL3NjaGVtYXMueG1s
c29hcC5vcmcvc29hcC9lbnZlbG9wZS8iPm5vdChhbmNlc3Rvci1vci1zZWxmOjpub2RlKClbQFNP
QVAtRU5WOmFjdG9yPSJ1cm46b2FzaXM6bmFtZXM6dGM6ZWJ4bWwtbXNnOmFjdG9yOm5leHRNU0gi
XSB8IGFuY2VzdG9yLW9yLXNlbGY6Om5vZGUoKVtAU09BUC1FTlY6YWN0b3I9Imh0dHA6Ly9zY2hl
bWFzLnhtbHNvYXAub3JnL3NvYXAvYWN0b3IvbmV4dCJdKTwvWFBhdGg+PC9UcmFuc2Zvcm0+PFRy
YW5zZm9ybSBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnL1RSLzIwMDEvUkVDLXhtbC1jMTRu
LTIwMDEwMzE1Ij48L1RyYW5zZm9ybT48L1RyYW5zZm9ybXM+PERpZ2VzdE1ldGhvZCBBbGdvcml0
aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMDQveG1sZW5jI3NoYTI1NiI+PC9EaWdlc3RNZXRo
b2Q+PERpZ2VzdFZhbHVlPlA5SHd3UTJzOTlTcmd2VElJQm9DVGhmYmxSMGJGc21XT0NFcXN4aFlZ
OEE9PC9EaWdlc3RWYWx1ZT48L1JlZmVyZW5jZT48L2ViOkFja25vd2xlZGdtZW50PjxTaWduYXR1
cmUgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyMiPjxTaWduZWRJbmZv
PjxDYW5vbmljYWxpemF0aW9uTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvVFIv
MjAwMS9SRUMteG1sLWMxNG4tMjAwMTAzMTUiIC8+PFNpZ25hdHVyZU1ldGhvZCBBbGdvcml0aG09
Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMDQveG1sZHNpZy1tb3JlI3JzYS1zaGEyNTYiIC8+PFJl
ZmVyZW5jZSBVUkk9IiI+PFRyYW5zZm9ybXM+PFRyYW5zZm9ybSBBbGdvcml0aG09Imh0dHA6Ly93
d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNlbnZlbG9wZWQtc2lnbmF0dXJlIiAvPjxUcmFuc2Zv
cm0gQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy9UUi8yMDAxL1JFQy14bWwtYzE0bi0yMDAx
MDMxNSIgLz48L1RyYW5zZm9ybXM+PERpZ2VzdE1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93d3cu
dzMub3JnLzIwMDEvMDQveG1sZW5jI3NoYTI1NiIgLz48RGlnZXN0VmFsdWU+OG5sOVRvY1Y2VXVs
ZzQyMDZsNFVtMUhaelMrS0pVdDhvaDkzNzVsVmREWT08L0RpZ2VzdFZhbHVlPjwvUmVmZXJlbmNl
PjwvU2lnbmVkSW5mbz48U2lnbmF0dXJlVmFsdWU+RmVMekRMRXNaRnNsL3R5ZGZIZVZzTG40WUVj
UkE3ak0zZ0NJcjRMSGFlRTVTOTVrL1RuRGdDY3piS2FHR0NubXJEckRQMjJHSWtXaHNEUmk1YkRn
N1VYeWVHYWUreEhvamU0cnlSN0FMdzdEa2xRV045K0lLWG55NUpzTmxXTXVWRVFqQVVCcWhCYUpy
UkZZc3dRVmhhSk1YOEN1N21BT2tyV2V0b1ljcXFrdXdOVVFSdkpNSUxDSmN0L2JkYXdhbytqa3J5
RmkxS21Kb3FOam9ueUF6VGNxYUFpdkN5M2xPTnhQK1VITmdRZUF3WmpyKzhDcHVZWFc3MHZSS0l3
b0ZmaTJsa0p0cjcveG1ERE1XS2RSUEFUbTdLTCtEb2QxVXRNaFVjbXQ5bitwcjhRMURURXloeUg0
MGRTVFFyNXBxWDdTMEVoVUNGMkpLR1VwaUpnMTFRPT08L1NpZ25hdHVyZVZhbHVlPjxLZXlJbmZv
PjxYNTA5RGF0YT48WDUwOUNlcnRpZmljYXRlPk1JSUZNVENDQkJtZ0F3SUJBZ0lMQW9rUVdnV2VP
RVV0M1JVd0RRWUpLb1pJaHZjTkFRRUxCUUF3VVRFTE1Ba0dBMVVFQmhNQ1RrOHhIVEFiQmdOVkJB
b01GRUoxZVhCaGMzTWdRVk10T1Rnek1UWXpNekkzTVNNd0lRWURWUVFEREJwQ2RYbHdZWE56SUVO
c1lYTnpJRE1nVkdWemREUWdRMEVnTXpBZUZ3MHlNVEF5TVRreE16TTBOVGxhRncweU5EQXlNVGt5
TWpVNU1EQmFNSGd4Q3pBSkJnTlZCQVlUQWs1UE1Sa3dGd1lEVlFRS0RCQldSVk5VVEVrZ1FWQlBW
RVZMSUVGVE1TRXdId1lEVlFRTERCaEVRU0JXWlhOMGJHa2dWRVZUVkMwNU1UVXdORGsxTURneEZ6
QVZCZ05WQkFNTURrUkJJRlpsYzNSc2FTQlVSVk5VTVJJd0VBWURWUVFGRXdrNU1UVXdORGsxTURn
d2dnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUJEd0F3Z2dFS0FvSUJBUURSK2p4cDhPc2EvMUp6
ZysvbXcrZ0YxSmhXRk9acDhoRldSNjhxcm9MbFJKN1pxRldZVWFNb3FxRXp4eDhUVW5NMmFuNVVk
c2ZzeitCb0FBaWpQS1o5RmZLcGRDY0pNNTc1K1Z4TnNFL1FLNzNiYUFuNkNORjRlekovOFBBdlpP
aW90SkxtMWpKaTNISnYwd2VVY3dUakhnWlRGOVlDMUI3ZFBRcThMS3kzZE1zejZua1BIQjk5TkpY
Q2FaVEE2NW9QVkFyVCt4QTdLMHY0dUd2clFOOFNFaHppUHk5TjMwbGJqWldaT3Y1Mk9sNlkvQnVO
NTR2ZU1TRkwzSlpTVHF3QXFsT1JqRWhNbDNsZGRnbklHcHk1dGl2WlFQV3FuUGk5akQ2WVd4NGdX
TXU3OXVwVG81NGZNUXk3RXMvMTVhN2N4YU5sTnVUVk9uWEwyTTRvSVNobkFnTUJBQUdqZ2dIaE1J
SUIzVEFKQmdOVkhSTUVBakFBTUI4R0ExVWRJd1FZTUJhQUZEK3U5WGdMa3FOd0lEVmZXdnIzSktC
U0FmQkJNQjBHQTFVZERnUVdCQlRlSjFHUkpBY3ZqdXRLa0g2SWg3N0JXS3E0ZnpBT0JnTlZIUThC
QWY4RUJBTUNCa0F3SFFZRFZSMGxCQll3RkFZSUt3WUJCUVVIQXdJR0NDc0dBUVVGQndNRU1CWUdB
MVVkSUFRUE1BMHdDd1lKWUlSQ0FSb0JBQU1DTUlHN0JnTlZIUjhFZ2JNd2diQXdONkExb0RPR01X
aDBkSEE2THk5amNtd3VkR1Z6ZERRdVluVjVjR0Z6Y3k1dWJ5OWpjbXd2UWxCRGJHRnpjek5VTkVO
Qk15NWpjbXd3ZGFCem9IR0diMnhrWVhBNkx5OXNaR0Z3TG5SbGMzUTBMbUoxZVhCaGMzTXVibTh2
WkdNOVFuVjVjR0Z6Y3l4a1l6MU9UeXhEVGoxQ2RYbHdZWE56SlRJd1EyeGhjM01sTWpBekpUSXdW
R1Z6ZERRbE1qQkRRU1V5TURNL1kyVnlkR2xtYVdOaGRHVlNaWFp2WTJGMGFXOXVUR2x6ZERDQmln
WUlLd1lCQlFVSEFRRUVmakI4TURzR0NDc0dBUVVGQnpBQmhpOW9kSFJ3T2k4dmIyTnpjQzUwWlhO
ME5DNWlkWGx3WVhOekxtNXZMMjlqYzNBdlFsQkRiR0Z6Y3pOVU5FTkJNekE5QmdnckJnRUZCUWN3
QW9ZeGFIUjBjRG92TDJOeWRDNTBaWE4wTkM1aWRYbHdZWE56TG01dkwyTnlkQzlDVUVOc1lYTnpN
MVEwUTBFekxtTmxjakFOQmdrcWhraUc5dzBCQVFzRkFBT0NBUUVBSWlGTTFkZ0R6UHFwcFlUckp6
aTZHSmFnSDQwQ09HREhnZDh2ZUZHRDM3NHRPQVYxb0c5ajh4OThXY1NHZ3ZKUmFGZHhwVmdOc2V2
TWdrUC9BUEhLdGpMOEQ0VmJUaC9BdHRyUG8xaXM5cWhZTUJjSTBTZk15WXBZVktrK1BuTFNES2Qy
cGF2cnhNZUtPT09saWtUdElTSjMwRzJFSGw0aEw0VTNIWWM3OFpsZXpIRCthcmcvRzhjaTVVK1NX
cXMzdk9xNjdrc1FOaTA5N0hHK2hxb1hxQ3I2SjZwSzB2clFSZmRLUWRDTXJScThpRG1DYlh4emcv
UGhiQnovUEJRVG5Jdkk4Q2lqaVdYR1V5TDNmbEx5RzV4K1dIenpzR2RwRytZOG13c0hIMWc3RUFn
ZGhFdmYxZE92c09QSHdQYWVEaTQ0TTl4ZGlMNm05aTNlMDlmOUhBPT08L1g1MDlDZXJ0aWZpY2F0
ZT48L1g1MDlEYXRhPjxLZXlWYWx1ZT48UlNBS2V5VmFsdWU+PE1vZHVsdXM+MGZvOGFmRHJHdjlT
YzRQdjVzUG9CZFNZVmhUbWFmSVJWa2V2S3E2QzVVU2UyYWhWbUZHaktLcWhNOGNmRTFKek5tcCtW
SGJIN00vZ2FBQUlvenltZlJYeXFYUW5DVE9lK2ZsY1RiQlAwQ3U5MjJnSitnalJlSHN5Zi9Ed0wy
VG9xTFNTNXRZeVl0eHliOU1IbEhNRTR4NEdVeGZXQXRRZTNUMEt2Q3lzdDNUTE0rcDVEeHdmZlRT
VndtbVV3T3VhRDFRSzAvc1FPeXRMK0xocjYwRGZFaEljNGo4dlRkOUpXNDJWbVRyK2RqcGVtUHdi
amVlTDNqRWhTOXlXVWs2c0FLcFRrWXhJVEpkNVhYWUp5QnFjdWJZcjJVRDFxcHo0dll3K21Gc2VJ
RmpMdS9icVU2T2VIekVNdXhMUDllV3UzTVdqWlRiazFUcDF5OWpPS0NFb1p3PT08L01vZHVsdXM+
PEV4cG9uZW50PkFRQUI8L0V4cG9uZW50PjwvUlNBS2V5VmFsdWU+PC9LZXlWYWx1ZT48L0tleUlu
Zm8+PC9TaWduYXR1cmU+PC9TT0FQOkhlYWRlcj48U09BUDpCb2R5IC8+PC9TT0FQOkVudmVsb3Bl
Pg=="""
    )

    val feilmeldingWithoutSignature = Part(
        valid.modify {
            it.append(MimeHeaders.CONTENT_TYPE, "text/xml")
            it.append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
            it.append(MimeHeaders.CONTENT_ID, "<feil>")
            it.append(SMTPHeaders.MESSAGE_ID, "12345")
        },
        """PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4NCjxuczU6RW52ZWxvcGUgeG1sbnM6bnM1PSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2VudmVsb3BlLyIgeG1sbnM6bnMxPSJodHRwOi8vd3d3Lm9hc2lzLW9wZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtY3BwYS9zY2hlbWEvY3BwLWNwYS0yXzAueHNkIiB4bWxuczpuczI9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkveGxpbmsiIHhtbG5zOm5zMz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyIgeG1sbnM6bnM0PSJodHRwOi8vd3d3Lm9hc2lzLW9wZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtbXNnL3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54c2QiIHhtbG5zOm5zNz0iaHR0cDovL3d3dy53My5vcmcvMjAwOS94bWxkc2lnMTEjIj4NCiAgICA8bnM1OkhlYWRlcj4NCiAgICAgICAgPG5zNDpNZXNzYWdlSGVhZGVyPg0KICAgICAgICAgICAgPG5zNDpGcm9tPg0KICAgICAgICAgICAgICAgIDxuczQ6UGFydHlJZCBuczQ6dHlwZT0iSEVSIj44MTQxMjUzPC9uczQ6UGFydHlJZD4NCiAgICAgICAgICAgICAgICA8bnM0OlJvbGU+RVJST1JfUkVTUE9OREVSPC9uczQ6Um9sZT4NCiAgICAgICAgICAgIDwvbnM0OkZyb20+DQogICAgICAgICAgICA8bnM0OlRvPg0KICAgICAgICAgICAgICAgIDxuczQ6UGFydHlJZCBuczQ6dHlwZT0iSEVSIj43OTc2ODwvbnM0OlBhcnR5SWQ+DQogICAgICAgICAgICAgICAgPG5zNDpSb2xlPkVSUk9SX1JFQ0VJVkVSPC9uczQ6Um9sZT4NCiAgICAgICAgICAgIDwvbnM0OlRvPg0KICAgICAgICAgICAgPG5zNDpDUEFJZD5uYXY6cWFzczozNTA2NTwvbnM0OkNQQUlkPg0KICAgICAgICAgICAgPG5zNDpDb252ZXJzYXRpb25JZD5iZTE5MmQzYS0zNGI1LTQ0OGEtYTM3NC01ZWFiMDUyNGM3NGQ8L25zNDpDb252ZXJzYXRpb25JZD4NCiAgICAgICAgICAgIDxuczQ6U2VydmljZT51cm46b2FzaXM6bmFtZXM6dGM6ZWJ4bWwtbXNnOnNlcnZpY2U8L25zNDpTZXJ2aWNlPg0KICAgICAgICAgICAgPG5zNDpBY3Rpb24+TWVzc2FnZUVycm9yPC9uczQ6QWN0aW9uPg0KICAgICAgICAgICAgPG5zNDpNZXNzYWdlRGF0YT4NCiAgICAgICAgICAgICAgICA8bnM0Ok1lc3NhZ2VJZD43MTA0YWNmOC0yMWU5LTRlZTctYjg5NC1kNDEzYTAwYTg4ODFfUkVTUE9OU0VfUkVTUE9OU0U8L25zNDpNZXNzYWdlSWQ+DQogICAgICAgICAgICAgICAgPG5zNDpUaW1lc3RhbXA+MjAyMy0xMS0xNFQwOTo1OTowMi40NjErMDE6MDA8L25zNDpUaW1lc3RhbXA+DQogICAgICAgICAgICAgICAgPG5zNDpSZWZUb01lc3NhZ2VJZD43MTA0YWNmOC0yMWU5LTRlZTctYjg5NC1kNDEzYTAwYTg4ODFfUkVTUE9OU0U8L25zNDpSZWZUb01lc3NhZ2VJZD4NCiAgICAgICAgICAgIDwvbnM0Ok1lc3NhZ2VEYXRhPg0KICAgICAgICA8L25zNDpNZXNzYWdlSGVhZGVyPg0KICAgICAgICA8bnM0OkVycm9yTGlzdCBuczQ6aGlnaGVzdFNldmVyaXR5PSJFcnJvciIgbnM0OnZlcnNpb249IjIuMCIgbnM1Om11c3RVbmRlcnN0YW5kPSIxIj4NCiAgICAgICAgICAgIDxuczQ6RXJyb3IgbnM0OmVycm9yQ29kZT0iU2VjdXJpdHlGYWlsdXJlIiBuczQ6aWQ9IkVSUk9SX0lEIiBuczQ6c2V2ZXJpdHk9IkVycm9yIj4NCiAgICAgICAgICAgICAgICA8bnM0OkRlc2NyaXB0aW9uIHhtbDpsYW5nPSJubyI+RmVpbCBzaWduYXR1cmU8L25zNDpEZXNjcmlwdGlvbj4NCiAgICAgICAgICAgIDwvbnM0OkVycm9yPg0KICAgICAgICA8L25zNDpFcnJvckxpc3Q+DQogICAgPC9uczU6SGVhZGVyPg0KPC9uczU6RW52ZWxvcGU+"""
    )
}
