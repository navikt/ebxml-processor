package no.nav.emottak.payload.helseid

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import no.nav.emottak.payload.helseid.testutils.HelseIDCreator
import no.nav.emottak.payload.helseid.testutils.ResourceUtil
import no.nav.emottak.payload.helseid.testutils.SecurityUtils
import no.nav.emottak.payload.helseid.testutils.XMLUtil
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.security.KeyStore
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * TODO:
 * - Flytt over test filene fra resources
 * - Oppdater hard-coded filnavn
 */
@Suppress("MaxLineLength")
internal class HelseIDValidatorTest {
    private lateinit var validator: HelseIDValidator
    private lateinit var helseIDCreator: HelseIDCreator
    private lateinit var keyStore: KeyStore

    @BeforeEach
    fun setUp() {
        validator = HelseIDValidator(ISSUER)
        helseIDCreator = HelseIDCreator("helseid/keystore.jks", "jks", "123456789".toCharArray())
        keyStore = SecurityUtils.createKeyStore("helseid/keystore.jks", "jks", "123456789".toCharArray())
    }

    @Test
    fun `validate helseID happy day`() {
        val validator = HelseIDValidator(ISSUER)
        HelseIDValidator.SUPPORTED_SCOPES.forEach { scope ->
            HelseIDValidator.SUPPORTED_AUDIENCE.forEach { aud ->
                validateHomeMadeHelseId(validator, scope = scope, audience = aud)
            }
        }
    }

    @Test
    fun `validate helseID wrong algorithm`() {
        val jwk = OctetSequenceKeyGenerator(256)
            .keyID("somerandomid") // give the key some ID (optional)
            .algorithm(JWSAlgorithm.HS256) // indicate the intended key alg (optional)
            .issueTime(Date())
            .generate()
        validateHomeMadeHelseId(
            validator,
            jwk = jwk,
            scope = HelseIDValidator.SUPPORTED_SCOPES.first(),
            audience = HelseIDValidator.SUPPORTED_AUDIENCE.first(),
            errMsg = "Token is not signed with an approved algorithm",
            algo = JWSAlgorithm.HS256
        )
    }

    @Test
    fun `validate helseID with wrong scope`() {
        validateHomeMadeHelseId(
            validator,
            scope = "e-hacker:hackhack",
            audience = HelseIDValidator.SUPPORTED_AUDIENCE.first(),
            "Token does not contain required scope"
        )
    }

    @Test
    fun `validate helseID with missing audience`() {
        validateHomeMadeHelseId(
            validator,
            scope = HelseIDValidator.SUPPORTED_SCOPES.first(),
            audience = "e-hacker:dr.evil",
            "Token does not contain required audience"
        )
    }

    @Test
    fun `validate helseID with wrong issuer`() {
        validateHomeMadeHelseId(
            HelseIDValidator("https://foo.bar"),
            scope = HelseIDValidator.SUPPORTED_SCOPES.first(),
            audience = HelseIDValidator.SUPPORTED_AUDIENCE.first(),
            "Invalid issuer https://helseid-sts.test.nhn.no"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["jwt", "at+jwt", "application/at+jwt"])
    fun `validate helseID with right type in header`(type: JOSEObjectType) {
        validateHomeMadeHelseId(
            HelseIDValidator("https://foo.bar"),
            scope = HelseIDValidator.SUPPORTED_SCOPES.first(),
            audience = HelseIDValidator.SUPPORTED_AUDIENCE.first(),
            type = type,
            errMsg = "Invalid issuer https://helseid-sts.test.nhn.no"
        )
    }

    @Test
    fun `validate helseID with wrong type in header`() {
        validateHomeMadeHelseId(
            HelseIDValidator("https://foo.bar"),
            scope = HelseIDValidator.SUPPORTED_SCOPES.first(),
            audience = HelseIDValidator.SUPPORTED_AUDIENCE.first(),
            type = JOSEObjectType("foo"),
            errMsg = "Unsupported token type foo"
        )
    }

    @Suppress("LongParameterList")
    private fun validateHomeMadeHelseId(
        validator: HelseIDValidator,
        scope: String,
        audience: String,
        errMsg: String? = null,
        algo: JWSAlgorithm = JWSAlgorithm.RS256,
        type: JOSEObjectType = JOSEObjectType.JWT,
        jwk: JWK? = null
    ) {
        val b64 = Base64.getEncoder().encodeToString(
            if (jwk == null) {
                helseIDCreator.getToken(
                    alias = "docsigner",
                    pid = "01010000110",
                    scope = scope,
                    audience = audience,
                    algo = algo,
                    type = type
                ).toByteArray()
            } else {
                helseIDCreator.getToken(
                    jwk = jwk,
                    pid = "01010000110",
                    scope = scope,
                    audience = audience,
                    algo = algo,
                    type = type
                ).toByteArray()
            }
        )
        TimeUnit.MILLISECONDS.sleep(20)
        val timeStamp = ZonedDateTime.now()
        val func: () -> Unit = {
            validator.getValidatedNin(
                b64,
                 timeStamp
            )
        }
        if (errMsg != null) {
            val t = if (errMsg == "no certificate to verify token with given") {
                assertThrows<NoSuchElementException> { func() }
            } else {
                assertThrows<RuntimeException> { func() }
            }
            assertThat(t.message, `is`(errMsg))
        } else {
            func()
        }
    }

    @Test
    fun validateHelseIDFromRealLifeM1() {
        val b64 =
            "ZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNklrSXlNRUZGTXpaRE1UUTVNME01TUVJMFFrSkRNRU01TmtGRU56UkJRMFkxUVRaRk9EZzFNVFFpTENKMGVYQWlPaUpLVjFRaUxDSjROWFFpT2lKelozSnFZa0pUVkhsUmRFeDJRWGxYY2xoVGN6bGhZbTlvVWxFaWZRLmV5SnVZbVlpT2pFMU5UZzBNalk0TWpZc0ltVjRjQ0k2TVRVMU9EUXpNRFF5Tml3aWFYTnpJam9pYUhSMGNITTZMeTlvWld4elpXbGtMWE4wY3k1MFpYTjBMbTVvYmk1dWJ5SXNJbUYxWkNJNld5Sm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2TDNKbGMyOTFjbU5sY3lJc0ltVXRhR1ZzYzJVNmNtVnpaWEIwWm05eWJXbGtiR1Z5Wlc0aVhTd2lZMnhwWlc1MFgybGtJam9pTTJSbFl6TTVPV1F0T0dJeE1pMDBOVGMzTFdGak5tSXRNVEF6WmpCa01HVmtOell3SWl3aWMzVmlJam9pU1ZCc2VtWXpRVVJvU0ZJMVZIRlVhelk1Ulc1VGFreERNek5IV2psSFRtTkxSMmRuVjIxNWJFeGlPRDBpTENKaGRYUm9YM1JwYldVaU9qRTFOVGcwTWpZNE1EUXNJbWxrY0NJNkluUmxjM1JwWkhBdGIybGtZeUlzSW1obGJITmxhV1E2THk5amJHRnBiWE12WTJ4cFpXNTBMMjl5YVdkcGJtRnNYMk5zYVdWdWRGOXBaQ0k2SWpabFpUbGpPVEExTFdObE1HRXROREJrWVMwNE1URTVMV00zTVRFd1lqWmpNRFZtTmlJc0ltaGxiSE5sYVdRNkx5OWpiR0ZwYlhNdmFXUmxiblJwZEhrdmMyVmpkWEpwZEhsZmJHVjJaV3dpT2lJMElpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OXBaR1Z1ZEdsMGVTOXdhV1FpT2lJeU5UQXlOell3TURNMk15SXNJbWhsYkhObGFXUTZMeTlqYkdGcGJYTXZhSEJ5TDJod2NsOXVkVzFpWlhJaU9pSTBNekV3TURFNU1EWWlMQ0pvWld4elpXbGtPaTh2WTJ4aGFXMXpMMk5zYVdWdWRDOWhiWElpT2lKeWMyRmZjSEpwZG1GMFpWOXJaWGtpTENKelkyOXdaU0k2V3lKbExXaGxiSE5sT25KbGMyVndkR1p2Y20xcFpHeGxjbVZ1TDNKbGEzWnBjbVZ1ZENKZExDSmhiWElpT2xzaWNIZGtJbDBzSW1GamRDSTZleUpwYzNNaU9pSm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2SWl3aVkyeHBaVzUwWDJsa0lqb2lNMlJsWXpNNU9XUXRPR0l4TWkwME5UYzNMV0ZqTm1JdE1UQXpaakJrTUdWa056WXdJaXdpYUdWc2MyVnBaRG92TDJOc1lXbHRjeTlqYkdsbGJuUXZaV012YjNKbmJuSmZjR0Z5Wlc1MElqb2lNVEF3TVRZek1UWXdJaXdpYUdWc2MyVnBaRG92TDJOc1lXbHRjeTlqYkdsbGJuUXZaV012Wlhod0lqb2lNVFUzT1RNNU1UazBNQ0lzSW1obGJITmxhV1E2THk5amJHRnBiWE12WTJ4cFpXNTBMMlZqTDJOdmJXMXZibDl1WVcxbElqb2lWRWhWVEVGTVJVZEZTMDlPVkU5U0lpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OWpiR2xsYm5RdlkyeGhhVzF6TDI5eVoyNXlYM0JoY21WdWRDSTZJakV3TURFMk16RTJNQ0o5ZlEuV0NiYVRWMVNDLXRwZ3c2czdibmtsSDE4ZHJ2U2h1Z0ZDVFhMMGZ4N1F6MFl1SGRzV3RnTW9mSnFhbjFzNUk3VUNJT3MxdWxuQWVITlZINlhTNlE3clpXT3ZLVWphS2pHUE1UVVY5cGlMLUVxdXRFWkJCYjlhSW5HcU15bVRHbmJhQ0JjTzQtUFNnbXVKTkUyVG5fdW1PYkVSclExYlU0dHdIR05na3d4VHNBZzhyV25VTWNGbi1hVEZPSWt2RzdMSGxPY2RqNEZnQzVQRTQyckQtZ2pPSEtHZS1OZkR5c09WTTdPU000dnZnenBLbWEzWnFQd1N1VFpuZUZQMjJlZTRZdm4wN2c2WW9rU0hvc2pKbEQyOG83UnhPR3UwVGRpaVBwRm1WMUJVRjFlTDBzSmxNamh5ZFAyRkstZWZOSFBrYVhPbmZUOFoyLW9JTGZ6U2hHdkFn"
        val timeStamp = ZonedDateTime.of(2019, 5, 21, 10, 22, 0, 0, ZoneId.systemDefault())
        validator.getValidatedNin(b64, timeStamp)
    }

    @Test
    fun validateHelseID2() {
        val validator = HelseIDValidator(ISSUER)
        val token = helseIDCreator.getToken(alias = "docsigner", pid = "01010000110")
        TimeUnit.MILLISECONDS.sleep(20)
        val timeStamp = ZonedDateTime.now()
        validator.getValidatedNin(token, timeStamp)
    }

    @ParameterizedTest
    @MethodSource("no.nav.emottak.payload.helseid.HelseIDValidatorTestKt#timestampAndErrorPairs")
    fun validateHelseIDwrongTimestamp(ts: String, msg: String) {
        val b64 =
            "ZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNklrTXdOakl3UTBaRk1ESXpRVEEyTVVWQk1VVXlRa00xTkRBeE5UUXdNakpGUlVOQ01EVTJRallpTENKMGVYQWlPaUpLVjFRaUxDSjROWFFpT2lKM1IwbE5YMmRKTmtKb05tZzBjbmhWUVZaUlEweDFlWGRXY2xraWZRLmV5SnVZbVlpT2pFMU5EUTFNamt4TkRrc0ltVjRjQ0k2TVRVME5EVXpNamMwT1N3aWFYTnpJam9pYUhSMGNITTZMeTlvWld4elpXbGtMWE4wY3k1MFpYTjBMbTVvYmk1dWJ5SXNJbUYxWkNJNld5Sm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2TDNKbGMyOTFjbU5sY3lJc0ltdHFaWEp1WldwdmRYSnVZV3d1WVhCcElpd2laUzFvWld4elpTOVRSazB1WVhCcElsMHNJbU5zYVdWdWRGOXBaQ0k2SWpRMk9EZGlaalZoTFRKbE5EVXRORFprTnkxaVpqYzNMV1l5Wm1VM01XVmhNalEyTnlJc0luTjFZaUk2SWtZclpta3hOM2x4VDJsNE5tbGlaWE5vYkhabmJVWlpURTlzWmt4SldqQlJLMWw2T0dkUGRVSmpNR005SWl3aVlYVjBhRjkwYVcxbElqb3hOVFEwTlRJNU1UUTVMQ0pwWkhBaU9pSjBaWE4wYVdSd0xXOXBaR01pTENKb1pXeHpaV2xrT2k4dlkyeGhhVzF6TDJsa1pXNTBhWFI1TDNObFkzVnlhWFI1WDJ4bGRtVnNJam9pTkNJc0ltaGxiSE5sYVdRNkx5OWpiR0ZwYlhNdmFIQnlMMmh3Y2w5dWRXMWlaWElpT2xzaU5ETXhNREF4TVRFd0lpd2lORE14TURBeE1URXdJbDBzSW1obGJITmxhV1E2THk5amJHRnBiWE12YVdSbGJuUnBkSGt2Y0dsa0lqb2lNVGN3TlRZMk1EQTFOelFpTENKelkyOXdaU0k2V3lKdmNHVnVhV1FpTENKd2NtOW1hV3hsSWl3aWFHVnNjMlZwWkRvdkwzTmpiM0JsY3k5cFpHVnVkR2wwZVM5d2FXUWlMQ0pvWld4elpXbGtPaTh2YzJOdmNHVnpMMmxrWlc1MGFYUjVMM05sWTNWeWFYUjVYMnhsZG1Wc0lpd2lhSFIwY0hNNkx5OWxhR1ZzYzJVdWJtOHZhMnBsY201bGFtOTFjbTVoYkM5cmFsOWhjR2tpTENKbExXaGxiSE5sTDNObWJTNWhjR2t2YzJadExtRndhU0pkTENKaGJYSWlPbHNpY0hka0lsMTkubERPT0xLZDlsOERraEcxN2JPQ05ILV90ZW9CZ2QydktsRHBxd2pzWlVFc0ZlRGtYNUhnc29YT2xsRGNGaGNDYzJhV3pDcmN0LVVZVklQczFhWXBzcV8weUtrOUp5dnNTZGV5MlhaUlg4MTlVWEtWMVFLWnZUTFZodW1PSmpFSkF0NGQxanRpZDZoWUVpLThxQW1OZDhwS1pqWHNmWDhaSkIyQmdUbVhGZFFLUVZlbGZpcHkzTHllNnMxSXZHRFViLXVnVnpWNGltckM5dVU3QVlVQlBzWS1yVFNQUk9BZU5PaUVEeng2ZlUydmp5VFBDdGV4UEY1QUlNQnc2Y096dzVoRHQ4dlpSVmpZSzhhLXB3cmE5STJXUjYtRDJMbTdNOUhIanN3RlJseUxtclByM2x1MGNISU4wYWFCd0tEMVU1dDJHVlhWNEpRLWJKa0pBeFN4NC1n"
        val t: Throwable = assertThrows<RuntimeException> {
            validator.getValidatedNin(
                b64,
                ZonedDateTime.parse(ts)
            )
        }
        assertThat(t.message, `is`(msg))
    }

    @ParameterizedTest
    @MethodSource("no.nav.emottak.payload.helseid.HelseIDValidatorTestKt#timestampAndErrorPairs")
    fun validateHelseIDwrongTimestampWithLargeAllowedClockSkew(ts: String) {
        val b64 =
            "ZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNklrTXdOakl3UTBaRk1ESXpRVEEyTVVWQk1VVXlRa00xTkRBeE5UUXdNakpGUlVOQ01EVTJRallpTENKMGVYQWlPaUpLVjFRaUxDSjROWFFpT2lKM1IwbE5YMmRKTmtKb05tZzBjbmhWUVZaUlEweDFlWGRXY2xraWZRLmV5SnVZbVlpT2pFMU5EUTFNamt4TkRrc0ltVjRjQ0k2TVRVME5EVXpNamMwT1N3aWFYTnpJam9pYUhSMGNITTZMeTlvWld4elpXbGtMWE4wY3k1MFpYTjBMbTVvYmk1dWJ5SXNJbUYxWkNJNld5Sm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2TDNKbGMyOTFjbU5sY3lJc0ltdHFaWEp1WldwdmRYSnVZV3d1WVhCcElpd2laUzFvWld4elpTOVRSazB1WVhCcElsMHNJbU5zYVdWdWRGOXBaQ0k2SWpRMk9EZGlaalZoTFRKbE5EVXRORFprTnkxaVpqYzNMV1l5Wm1VM01XVmhNalEyTnlJc0luTjFZaUk2SWtZclpta3hOM2x4VDJsNE5tbGlaWE5vYkhabmJVWlpURTlzWmt4SldqQlJLMWw2T0dkUGRVSmpNR005SWl3aVlYVjBhRjkwYVcxbElqb3hOVFEwTlRJNU1UUTVMQ0pwWkhBaU9pSjBaWE4wYVdSd0xXOXBaR01pTENKb1pXeHpaV2xrT2k4dlkyeGhhVzF6TDJsa1pXNTBhWFI1TDNObFkzVnlhWFI1WDJ4bGRtVnNJam9pTkNJc0ltaGxiSE5sYVdRNkx5OWpiR0ZwYlhNdmFIQnlMMmh3Y2w5dWRXMWlaWElpT2xzaU5ETXhNREF4TVRFd0lpd2lORE14TURBeE1URXdJbDBzSW1obGJITmxhV1E2THk5amJHRnBiWE12YVdSbGJuUnBkSGt2Y0dsa0lqb2lNVGN3TlRZMk1EQTFOelFpTENKelkyOXdaU0k2V3lKdmNHVnVhV1FpTENKd2NtOW1hV3hsSWl3aWFHVnNjMlZwWkRvdkwzTmpiM0JsY3k5cFpHVnVkR2wwZVM5d2FXUWlMQ0pvWld4elpXbGtPaTh2YzJOdmNHVnpMMmxrWlc1MGFYUjVMM05sWTNWeWFYUjVYMnhsZG1Wc0lpd2lhSFIwY0hNNkx5OWxhR1ZzYzJVdWJtOHZhMnBsY201bGFtOTFjbTVoYkM5cmFsOWhjR2tpTENKbExXaGxiSE5sTDNObWJTNWhjR2t2YzJadExtRndhU0pkTENKaGJYSWlPbHNpY0hka0lsMTkubERPT0xLZDlsOERraEcxN2JPQ05ILV90ZW9CZ2QydktsRHBxd2pzWlVFc0ZlRGtYNUhnc29YT2xsRGNGaGNDYzJhV3pDcmN0LVVZVklQczFhWXBzcV8weUtrOUp5dnNTZGV5MlhaUlg4MTlVWEtWMVFLWnZUTFZodW1PSmpFSkF0NGQxanRpZDZoWUVpLThxQW1OZDhwS1pqWHNmWDhaSkIyQmdUbVhGZFFLUVZlbGZpcHkzTHllNnMxSXZHRFViLXVnVnpWNGltckM5dVU3QVlVQlBzWS1yVFNQUk9BZU5PaUVEeng2ZlUydmp5VFBDdGV4UEY1QUlNQnc2Y096dzVoRHQ4dlpSVmpZSzhhLXB3cmE5STJXUjYtRDJMbTdNOUhIanN3RlJseUxtclByM2x1MGNISU4wYWFCd0tEMVU1dDJHVlhWNEpRLWJKa0pBeFN4NC1n"
        val validator2 = HelseIDValidator(ISSUER, LARGE_ALLOWED_CLOCK_SKEW)
        val t: Throwable = assertThrows<RuntimeException> {
            validator2.getValidatedNin(b64, ZonedDateTime.parse(ts))
        }
        assertThat(t.message, `is`("Token does not contain required audience"))
    }

    @Test
    fun getHelseIDTokenNodesFromDocumentWithHelseId() {
        val doc = XMLUtil.createDocument(
            Base64.getDecoder().decode(ResourceUtil.getStringClasspathResource("helseid/testdata/m1.helseid.ok.b64"))
        )
        assertThat(
            validator.getHelseIDTokenNodesFromDocument(doc),
            notNullValue()
        )
    }

    @Test
    fun getHelseIDTokenNodesFromDocumentNoHelseId() {
        val doc = XMLUtil.createDocument(
            Base64.getDecoder()
                .decode(ResourceUtil.getStringClasspathResource("helseid/testdata/m1.with.attachment.not.helseid.b64"))
        )
        assertThat(validator.getHelseIDTokenNodesFromDocument(doc), nullValue())
    }

    @Test
    fun getHelseIDTokenNodesFromDocumentMultipleHelseId() {
        val doc =
            XMLUtil.createDocument(ResourceUtil.getStringClasspathResource("helseid/testdata/m1.helseid.multiple.xml"))
        assertThrows<RuntimeException> {
            validator.getHelseIDTokenNodesFromDocument(
                doc
            )
        }
    }

    @Test
    fun nin() {
        assertThat(
            validator.getNin(
                "ZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNklrSXlNRUZGTXpaRE1UUTVNME01TUVJMFFrSkRNRU01TmtGRU56UkJRMFkxUVRaRk9EZzFNVFFpTENKMGVYQWlPaUpLVjFRaUxDSjROWFFpT2lKelozSnFZa0pUVkhsUmRFeDJRWGxYY2xoVGN6bGhZbTlvVWxFaWZRLmV5SnVZbVlpT2pFMU5UWTJNVEkwTmpZc0ltVjRjQ0k2TVRVMU5qWXhOakEyTml3aWFYTnpJam9pYUhSMGNITTZMeTlvWld4elpXbGtMWE4wY3k1MFpYTjBMbTVvYmk1dWJ5SXNJbUYxWkNJNld5Sm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2TDNKbGMyOTFjbU5sY3lJc0ltVXRhR1ZzYzJVNmNtVnpaWEIwWm05eWJXbGtiR1Z5Wlc0aVhTd2lZMnhwWlc1MFgybGtJam9pTTJSbFl6TTVPV1F0T0dJeE1pMDBOVGMzTFdGak5tSXRNVEF6WmpCa01HVmtOell3SWl3aWMzVmlJam9pU1dRNGVWTlRRVXd5UVZvcmFXeFJWVzgxUkRkNGJYbDViWHAyYTBwRk4wVm5XSE5XUldkWWJHSlRSVDBpTENKaGRYUm9YM1JwYldVaU9qRTFOVFkyTVRJek56TXNJbWxrY0NJNkluUmxjM1JwWkhBdGIybGtZeUlzSW1obGJITmxhV1E2THk5amJHRnBiWE12WTJ4cFpXNTBMMjl5YVdkcGJtRnNYMk5zYVdWdWRGOXBaQ0k2SWpabFpUbGpPVEExTFdObE1HRXROREJrWVMwNE1URTVMV00zTVRFd1lqWmpNRFZtTmlJc0ltaGxiSE5sYVdRNkx5OWpiR0ZwYlhNdlkyeHBaVzUwTDJ0cUwyOXlaMjV5SWpvaU1UQXdNVFl6TVRZd0lpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OWpiR2xsYm5RdlpXTXZiM0puYm5KZmNHRnlaVzUwSWpvaU1UQXdNVFl6TVRZd0lpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OWpiR2xsYm5RdlpXTXZaWGh3SWpveE5UYzVNemt4T1RRd0xDSm9aV3h6Wldsa09pOHZZMnhoYVcxekwyTnNhV1Z1ZEM5bFl5OWpiMjF0YjI1ZmJtRnRaU0k2SWxSSVZVeEJURVZIUlV0UFRsUlBVaUlzSW1obGJITmxhV1E2THk5amJHRnBiWE12YVdSbGJuUnBkSGt2YzJWamRYSnBkSGxmYkdWMlpXd2lPaUkwSWl3aWFHVnNjMlZwWkRvdkwyTnNZV2x0Y3k5cFpHVnVkR2wwZVM5d2FXUWlPaUl3TnpBeE5Ua3dNREV6TkNJc0ltaGxiSE5sYVdRNkx5OWpiR0ZwYlhNdmFIQnlMMmh3Y2w5dWRXMWlaWElpT2lJeU1qSXlNREF3T0RFaUxDSm9aV3h6Wldsa09pOHZZMnhoYVcxekwyTnNhV1Z1ZEM5aGJYSWlPaUp5YzJGZmNISnBkbUYwWlY5clpYa2lMQ0p6WTI5d1pTSTZXeUpsTFdobGJITmxPbkpsYzJWd2RHWnZjbTFwWkd4bGNtVnVMM0psYTNacGNtVnVkQ0pkTENKaGJYSWlPbHNpY0hka0lsMHNJbUZqZENJNmV5SnBjM01pT2lKb2RIUndjem92TDJobGJITmxhV1F0YzNSekxuUmxjM1F1Ym1odUxtNXZJaXdpWTJ4cFpXNTBYMmxrSWpvaU0yUmxZek01T1dRdE9HSXhNaTAwTlRjM0xXRmpObUl0TVRBelpqQmtNR1ZrTnpZd0lpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OWpiR2xsYm5RdlpXTXZiM0puYm5KZmNHRnlaVzUwSWpvaU1UQXdNVFl6TVRZd0lpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OWpiR2xsYm5RdlpXTXZaWGh3SWpvaU1UVTNPVE01TVRrME1DSXNJbWhsYkhObGFXUTZMeTlqYkdGcGJYTXZZMnhwWlc1MEwyVmpMMk52YlcxdmJsOXVZVzFsSWpvaVZFaFZURUZNUlVkRlMwOU9WRTlTSWl3aWFHVnNjMlZwWkRvdkwyTnNZV2x0Y3k5amJHbGxiblF2WTJ4aGFXMXpMMjl5WjI1eVgzQmhjbVZ1ZENJNklqRXdNREUyTXpFMk1DSjlmUS5hWWh4LVR1Z09KMUpkMmV1QUNaQXVUbDlQaFhTa2piOTBrV09UWlBDTHotMzZpREphR3pJei1tNmJmYWtDdlhTQ0plYVZ6Mnp3bXBGNXVMdFpOWFFVM3hHMndVa0stek4xeENmYl9PRUZ3aUZRWlhIZngzX1lGUTVJT3VhODNqcXMtQmtsZExqSUlkNEZsVVRwMXc4YTlUQkJwaUM0YmdSQmgwV3BKOFcybWhzU3NWYjNiSE9YRERoLXR3TUVtTUNkTG9nTUhTQ0lieHl1SFZ0c0RlaHFKSzk3ck0wOGM3NEV1bURDdXJEUTFVaDduX3g2V1VjR2tPazU5UXRPYlk1VExPbDlUYjdWM2FNT1lHckdEc2x1REVwVkQycVZveGw4cklrWjNPMk8wVWpMZTlEeDFTR1VDSmJLb2o3U2xpN3ZuZmZvTW5WZ3JRODY3OWRSUE9ObHc="
            ),
            `is`("07015900134")
        )
    }

    @Test
    fun nin2() {
        assertThat(validator.getNin(JWT), `is`("07015900134"))
    }

    @Test
    fun nin3() {
        val jwt = JWTParser.parse(JWT) as SignedJWT
        assertThat(validator.getNin(jwt), `is`("07015900134"))
    }

    @Suppress("LongMethod")
    @Test
    fun `validateSignature using jwk keyssets from issuer`() {
        val b64 =
            "ZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNklrSXlNRUZGTXpaRE1UUTVNME01TUVJMFFrSkRNRU01TmtGRU56UkJRMFkxUVRaRk9EZzFNVFFpTENKMGVYQWlPaUpLVjFRaUxDSjROWFFpT2lKelozSnFZa0pUVkhsUmRFeDJRWGxYY2xoVGN6bGhZbTlvVWxFaWZRLmV5SnVZbVlpT2pFMU5UZzBNalk0TWpZc0ltVjRjQ0k2TVRVMU9EUXpNRFF5Tml3aWFYTnpJam9pYUhSMGNITTZMeTlvWld4elpXbGtMWE4wY3k1MFpYTjBMbTVvYmk1dWJ5SXNJbUYxWkNJNld5Sm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2TDNKbGMyOTFjbU5sY3lJc0ltVXRhR1ZzYzJVNmNtVnpaWEIwWm05eWJXbGtiR1Z5Wlc0aVhTd2lZMnhwWlc1MFgybGtJam9pTTJSbFl6TTVPV1F0T0dJeE1pMDBOVGMzTFdGak5tSXRNVEF6WmpCa01HVmtOell3SWl3aWMzVmlJam9pU1ZCc2VtWXpRVVJvU0ZJMVZIRlVhelk1Ulc1VGFreERNek5IV2psSFRtTkxSMmRuVjIxNWJFeGlPRDBpTENKaGRYUm9YM1JwYldVaU9qRTFOVGcwTWpZNE1EUXNJbWxrY0NJNkluUmxjM1JwWkhBdGIybGtZeUlzSW1obGJITmxhV1E2THk5amJHRnBiWE12WTJ4cFpXNTBMMjl5YVdkcGJtRnNYMk5zYVdWdWRGOXBaQ0k2SWpabFpUbGpPVEExTFdObE1HRXROREJrWVMwNE1URTVMV00zTVRFd1lqWmpNRFZtTmlJc0ltaGxiSE5sYVdRNkx5OWpiR0ZwYlhNdmFXUmxiblJwZEhrdmMyVmpkWEpwZEhsZmJHVjJaV3dpT2lJMElpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OXBaR1Z1ZEdsMGVTOXdhV1FpT2lJeU5UQXlOell3TURNMk15SXNJbWhsYkhObGFXUTZMeTlqYkdGcGJYTXZhSEJ5TDJod2NsOXVkVzFpWlhJaU9pSTBNekV3TURFNU1EWWlMQ0pvWld4elpXbGtPaTh2WTJ4aGFXMXpMMk5zYVdWdWRDOWhiWElpT2lKeWMyRmZjSEpwZG1GMFpWOXJaWGtpTENKelkyOXdaU0k2V3lKbExXaGxiSE5sT25KbGMyVndkR1p2Y20xcFpHeGxjbVZ1TDNKbGEzWnBjbVZ1ZENKZExDSmhiWElpT2xzaWNIZGtJbDBzSW1GamRDSTZleUpwYzNNaU9pSm9kSFJ3Y3pvdkwyaGxiSE5sYVdRdGMzUnpMblJsYzNRdWJtaHVMbTV2SWl3aVkyeHBaVzUwWDJsa0lqb2lNMlJsWXpNNU9XUXRPR0l4TWkwME5UYzNMV0ZqTm1JdE1UQXpaakJrTUdWa056WXdJaXdpYUdWc2MyVnBaRG92TDJOc1lXbHRjeTlqYkdsbGJuUXZaV012YjNKbmJuSmZjR0Z5Wlc1MElqb2lNVEF3TVRZek1UWXdJaXdpYUdWc2MyVnBaRG92TDJOc1lXbHRjeTlqYkdsbGJuUXZaV012Wlhod0lqb2lNVFUzT1RNNU1UazBNQ0lzSW1obGJITmxhV1E2THk5amJHRnBiWE12WTJ4cFpXNTBMMlZqTDJOdmJXMXZibDl1WVcxbElqb2lWRWhWVEVGTVJVZEZTMDlPVkU5U0lpd2lhR1ZzYzJWcFpEb3ZMMk5zWVdsdGN5OWpiR2xsYm5RdlkyeGhhVzF6TDI5eVoyNXlYM0JoY21WdWRDSTZJakV3TURFMk16RTJNQ0o5ZlEuV0NiYVRWMVNDLXRwZ3c2czdibmtsSDE4ZHJ2U2h1Z0ZDVFhMMGZ4N1F6MFl1SGRzV3RnTW9mSnFhbjFzNUk3VUNJT3MxdWxuQWVITlZINlhTNlE3clpXT3ZLVWphS2pHUE1UVVY5cGlMLUVxdXRFWkJCYjlhSW5HcU15bVRHbmJhQ0JjTzQtUFNnbXVKTkUyVG5fdW1PYkVSclExYlU0dHdIR05na3d4VHNBZzhyV25VTWNGbi1hVEZPSWt2RzdMSGxPY2RqNEZnQzVQRTQyckQtZ2pPSEtHZS1OZkR5c09WTTdPU000dnZnenBLbWEzWnFQd1N1VFpuZUZQMjJlZTRZdm4wN2c2WW9rU0hvc2pKbEQyOG83UnhPR3UwVGRpaVBwRm1WMUJVRjFlTDBzSmxNamh5ZFAyRkstZWZOSFBrYVhPbmZUOFoyLW9JTGZ6U2hHdkFn"
        val token = String(Base64.getDecoder().decode(b64))
        val jws: JWSObject = JWTParser.parse(token) as JWSObject

        val currentJWKSet = """
            {
              "keys": [
                {
                  "kty": "RSA",
                  "use": "sig",
                  "kid": "78667F90DC11BF04BD9467D1F9120C4A4340B4CF",
                  "x5t": "eGZ_kNwRvwS9lGfR-RIMSkNAtM8",
                  "e": "AQAB",
                  "n": "tN7XtBOECw46hgl6q76EW122XGy8OjsobNCxpnds6awfMwt3yoBASQ8z7lcwW5rXQUXlfg3b6K2jBZc33-KmwrT7FBJrcOKIRZWl_EhHm4JGdKXbMva7UmyBsr3LBFAj9cQvKukbF6b5ATIknz9NFTlDn7Cv0TFzxpbtolIvNH6gy0Yk5U1hsNxQ1eS96w40K0gvKSw_Fow_rgkhfNcOzYqC9TIbKihvL9Ya0ZhiVaEVvq58ZtXGXgduQZGcMr8vk1XwTKmQ4AXtrIajnO_id67bV60chDdxSGyc5yKBEjPFIoPXpebRtgl2IL7z1O80A48vtXeH2B492wgQyFIDgN70UZwNYvlfniD8Zjn66kWPPn0W3erdiHoYQoMluaqIsoY2K2r7dN0Wxi9OYcTvUfH128lOVVLkw0zANCh57yi67fNjB_aaExQZ4tWY21HAdIgQRSOgPwbtyv4TQ-_RU0_FtZWyc6Yn2mij_tV3D7pGnEeSB2evLWeO2M8oE3e1",
                  "x5c": [
                    "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"
                  ],
                  "alg": "RS256"
                }
              ]
            }
        """.trimIndent()
        val expiredJWKSet = """
            {
              "keys": [
                {
                  "kty": "RSA",
                  "use": "sig",
                  "kid": "B20AE36C1493C90B4BBC0C96AD74ACF5A6E88514",
                  "x5t": "sgrjbBSTyQtLvAyWrXSs9abohRQ",
                  "e": "AQAB",
                  "n": "1Imh5XgI8But2O64eRIdb5cTZmE6BWoL_p4rMwLgEAh0VlXgDqTx-xuy6HGsNtPZbxl6gHBNimr0Wyx6xCDXUPafPhPqE91PeCmVgLBAqDU4c56PQyFkBHIHJMAP2D3sDUEzc9gODK0mFbznUjxuU6X8FEmP_ioFM_5fKrSpVG-KAfzv5_TBy37zRyldAVHkFHiR3EdtOCivIEqcC4PhKHesUug1wmawSEUI6Z1ViuLeuNnn_b0T7Ng23tVcAM8mrdHn_ES06YuaXZYmnUN0GEiK_J1ewpvBb_EMHGO4ocbodjuPdjO3SwP_hXTE2MsjN8xS7vmpqeoV9KUDHQFKCw",
                  "x5c": [
                    "MIIFBzCCA++gAwIBAgILAc2fG6Tv6ws1fwUwDQYJKoZIhvcNAQELBQAwUTELMAkGA1UEBhMCTk8xHTAbBgNVBAoMFEJ1eXBhc3MgQVMtOTgzMTYzMzI3MSMwIQYDVQQDDBpCdXlwYXNzIENsYXNzIDMgVGVzdDQgQ0EgMzAeFw0xODA5MTEwNTIwNTRaFw0yMTA5MTAyMTU5MDBaMG0xCzAJBgNVBAYTAk5PMRswGQYDVQQKDBJOT1JTSyBIRUxTRU5FVFQgU0YxEDAOBgNVBAsMB0hlbHNlSUQxGzAZBgNVBAMMEk5PUlNLIEhFTFNFTkVUVCBTRjESMBAGA1UEBRMJOTk0NTk4NzU5MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1Imh5XgI8But2O64eRIdb5cTZmE6BWoL/p4rMwLgEAh0VlXgDqTx+xuy6HGsNtPZbxl6gHBNimr0Wyx6xCDXUPafPhPqE91PeCmVgLBAqDU4c56PQyFkBHIHJMAP2D3sDUEzc9gODK0mFbznUjxuU6X8FEmP/ioFM/5fKrSpVG+KAfzv5/TBy37zRyldAVHkFHiR3EdtOCivIEqcC4PhKHesUug1wmawSEUI6Z1ViuLeuNnn/b0T7Ng23tVcAM8mrdHn/ES06YuaXZYmnUN0GEiK/J1ewpvBb/EMHGO4ocbodjuPdjO3SwP/hXTE2MsjN8xS7vmpqeoV9KUDHQFKCwIDAQABo4IBwjCCAb4wCQYDVR0TBAIwADAfBgNVHSMEGDAWgBQ/rvV4C5KjcCA1X1r69ySgUgHwQTAdBgNVHQ4EFgQUd2lMsttTomQN4RWhBaPHMFTd0SkwDgYDVR0PAQH/BAQDAgZAMBYGA1UdIAQPMA0wCwYJYIRCARoBAAMCMIG7BgNVHR8EgbMwgbAwN6A1oDOGMWh0dHA6Ly9jcmwudGVzdDQuYnV5cGFzcy5uby9jcmwvQlBDbGFzczNUNENBMy5jcmwwdaBzoHGGb2xkYXA6Ly9sZGFwLnRlc3Q0LmJ1eXBhc3Mubm8vZGM9QnV5cGFzcyxkYz1OTyxDTj1CdXlwYXNzJTIwQ2xhc3MlMjAzJTIwVGVzdDQlMjBDQSUyMDM/Y2VydGlmaWNhdGVSZXZvY2F0aW9uTGlzdDCBigYIKwYBBQUHAQEEfjB8MDsGCCsGAQUFBzABhi9odHRwOi8vb2NzcC50ZXN0NC5idXlwYXNzLm5vL29jc3AvQlBDbGFzczNUNENBMzA9BggrBgEFBQcwAoYxaHR0cDovL2NydC50ZXN0NC5idXlwYXNzLm5vL2NydC9CUENsYXNzM1Q0Q0EzLmNlcjANBgkqhkiG9w0BAQsFAAOCAQEALKawhumnN8vCF+cmcLDZEpkfubOHqPfp7jBGcTSjFMGSbtrHxHWnzkJcsn9kTAEPIpqtThNQmAEM2WvmdEQrFVSdrf+eQpA+cOBhQBpSrJzTOI9KSERS/DxwuImSHj/6P6joSeQQVNAKo4U9o9xu+qQGvIxAl5bOEMaDY89beyvfJpaJO+NXKjy6xl8RpbdRO3bO62flyrg3h87ebHxZpQZrlJpwkEnK0AcKSQ4fhIiZqKSHMmGvxRp3WAoU/8ePa0u/+GacifncBRka7PdeG2CuW6KyPTf54ZiTVXhJ9TnxUU5F2Fg4mpA0466u3uhEh9KeEX6pvJgNOGSHh2x/cw=="
                  ],
                  "alg": "RS256"
                }
              ]
            }
        """.trimIndent()

        // validate with current key set
        val jwks1: JWKSet = JWKSet.parse(currentJWKSet)
        val verifiermap1 = jwks1.keys.map { it.keyID.to(verifier(it)) }.toMap()
        assertThat(verifiermap1.containsKey(jws.header.keyID), `is`(false))

        // validate with expired key set
        val jwks2: JWKSet = JWKSet.parse(expiredJWKSet)
        val verifiermap2 = jwks2.keys.map { it.keyID.to(verifier(it)) }.toMap()
        assertThat(verifiermap2.containsKey(jws.header.keyID), `is`(true))
        val verifier2 = verifiermap2[jws.header.keyID]
        val res2 = jws.verify(verifier2)
        assertThat(res2, `is`(true))
    }

    private fun verifier(jwk: JWK): JWSVerifier =
        when (jwk.algorithm) {
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512 -> RSASSAVerifier(
                jwk.toRSAKey().toRSAPublicKey()
            )

            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512 -> ECDSAVerifier(jwk.toECKey().toECPublicKey())
            else -> throw RuntimeException("unsupported algorithm")
        }

    companion object {
        private const val ISSUER = "https://helseid-sts.test.nhn.no"
        private const val JWT =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6IkIyMEFFMzZDMTQ5M0M5MEI0QkJDMEM5NkFENzRBQ0Y1QTZFODg1MTQiLCJ0eXAiOiJKV1QiLCJ4NXQiOiJzZ3JqYkJTVHlRdEx2QXlXclhTczlhYm9oUlEifQ.eyJuYmYiOjE1NTY2MTI0NjYsImV4cCI6MTU1NjYxNjA2NiwiaXNzIjoiaHR0cHM6Ly9oZWxzZWlkLXN0cy50ZXN0Lm5obi5ubyIsImF1ZCI6WyJodHRwczovL2hlbHNlaWQtc3RzLnRlc3QubmhuLm5vL3Jlc291cmNlcyIsImUtaGVsc2U6cmVzZXB0Zm9ybWlkbGVyZW4iXSwiY2xpZW50X2lkIjoiM2RlYzM5OWQtOGIxMi00NTc3LWFjNmItMTAzZjBkMGVkNzYwIiwic3ViIjoiSWQ4eVNTQUwyQVoraWxRVW81RDd4bXl5bXp2a0pFN0VnWHNWRWdYbGJTRT0iLCJhdXRoX3RpbWUiOjE1NTY2MTIzNzMsImlkcCI6InRlc3RpZHAtb2lkYyIsImhlbHNlaWQ6Ly9jbGFpbXMvY2xpZW50L29yaWdpbmFsX2NsaWVudF9pZCI6IjZlZTljOTA1LWNlMGEtNDBkYS04MTE5LWM3MTEwYjZjMDVmNiIsImhlbHNlaWQ6Ly9jbGFpbXMvY2xpZW50L2tqL29yZ25yIjoiMTAwMTYzMTYwIiwiaGVsc2VpZDovL2NsYWltcy9jbGllbnQvZWMvb3JnbnJfcGFyZW50IjoiMTAwMTYzMTYwIiwiaGVsc2VpZDovL2NsYWltcy9jbGllbnQvZWMvZXhwIjoxNTc5MzkxOTQwLCJoZWxzZWlkOi8vY2xhaW1zL2NsaWVudC9lYy9jb21tb25fbmFtZSI6IlRIVUxBTEVHRUtPTlRPUiIsImhlbHNlaWQ6Ly9jbGFpbXMvaWRlbnRpdHkvc2VjdXJpdHlfbGV2ZWwiOiI0IiwiaGVsc2VpZDovL2NsYWltcy9pZGVudGl0eS9waWQiOiIwNzAxNTkwMDEzNCIsImhlbHNlaWQ6Ly9jbGFpbXMvaHByL2hwcl9udW1iZXIiOiIyMjIyMDAwODEiLCJoZWxzZWlkOi8vY2xhaW1zL2NsaWVudC9hbXIiOiJyc2FfcHJpdmF0ZV9rZXkiLCJzY29wZSI6WyJlLWhlbHNlOnJlc2VwdGZvcm1pZGxlcmVuL3Jla3ZpcmVudCJdLCJhbXIiOlsicHdkIl0sImFjdCI6eyJpc3MiOiJodHRwczovL2hlbHNlaWQtc3RzLnRlc3QubmhuLm5vIiwiY2xpZW50X2lkIjoiM2RlYzM5OWQtOGIxMi00NTc3LWFjNmItMTAzZjBkMGVkNzYwIiwiaGVsc2VpZDovL2NsYWltcy9jbGllbnQvZWMvb3JnbnJfcGFyZW50IjoiMTAwMTYzMTYwIiwiaGVsc2VpZDovL2NsYWltcy9jbGllbnQvZWMvZXhwIjoiMTU3OTM5MTk0MCIsImhlbHNlaWQ6Ly9jbGFpbXMvY2xpZW50L2VjL2NvbW1vbl9uYW1lIjoiVEhVTEFMRUdFS09OVE9SIiwiaGVsc2VpZDovL2NsYWltcy9jbGllbnQvY2xhaW1zL29yZ25yX3BhcmVudCI6IjEwMDE2MzE2MCJ9fQ.aYhx-TugOJ1Jd2euACZAuTl9PhXSkjb90kWOTZPCLz-36iDJaGzIz-m6bfakCvXSCJeaVz2zwmpF5uLtZNXQU3xG2wUkK-zN1xCfb_OEFwiFQZXHfx3_YFQ5IOua83jqs-BkldLjIId4FlUTp1w8a9TBBpiC4bgRBh0WpJ8W2mhsSsVb3bHOXDDh-twMEmMCdLogMHSCIbxyuHVtsDehqJK97rM08c74EumDCurDQ1Uh7n_x6WUcGkOk59QtObY5TLOl9Tb7V3aMOYGrGDsluDEpVD2qVoxl8rIkZ3O2O0UjLe9Dx1SGUCJbKoj7Sli7vnffoMnVgrQ8679dRPONlw"
        private const val LARGE_ALLOWED_CLOCK_SKEW = 31 * 24 * 3600 * 1000L
    }
}

@Suppress("MaxLineLength")
internal fun timestampAndErrorPairs(): List<Arguments> {
    return listOf(
        Arguments.of(
            "2018-12-10T13:51:00.000+01:00",
            "Timestamp (Mon Dec 10 13:51:00 CET 2018) is before not-before time (Tue Dec 11 12:52:29 CET 2018)"
        ),
        Arguments.of(
            "2018-12-12T13:51:00.000+01:00",
            "Timestamp (Wed Dec 12 13:51:00 CET 2018) is after expiry time (Tue Dec 11 13:52:29 CET 2018)"
        )
    )
}
