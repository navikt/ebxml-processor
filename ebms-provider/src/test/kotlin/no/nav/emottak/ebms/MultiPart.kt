package no.nav.emottak.ebms

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.content.PartData
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.validation.MimeHeaders

const val MULTIPART_CONTENT_TYPE: String = """multipart/related;type="text/xml";boundary="----=_Part_495_-1172936255.1665395092859";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";"""
const val EBXML_PAYLOAD: String = """PFNPQVA6RW52ZWxvcGUgeG1sbnM6U09BUD0iaHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvc29hcC9lbnZlbG9wZS8iIHhtbG5zOmViPSJodHRwOi8vd3d3Lm9hc2lzLW9wZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtbXNnL3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54c2QiIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB4bWxuczp4c2k9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4c2k6c2NoZW1hTG9jYXRpb249Imh0dHA6Ly9zY2hlbWFzLnhtbHNvYXAub3JnL3NvYXAvZW52ZWxvcGUvIGh0dHA6Ly93d3cub2FzaXMtb3Blbi5vcmcvY29tbWl0dGVlcy9lYnhtbC1tc2cvc2NoZW1hL2VudmVsb3BlLnhzZCBodHRwOi8vd3d3Lm9hc2lzLW9wZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtbXNnL3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54c2QgaHR0cDovL3d3dy5vYXNpcy1vcGVuLm9yZy9jb21taXR0ZWVzL2VieG1sLW1zZy9zY2hlbWEvbXNnLWhlYWRlci0yXzAueHNkIj48U09BUDpIZWFkZXI+PGViOk1lc3NhZ2VIZWFkZXIgU09BUDptdXN0VW5kZXJzdGFuZD0iMSIgZWI6dmVyc2lvbj0iMi4wIj48ZWI6RnJvbT48ZWI6UGFydHlJZCBlYjp0eXBlPSJIRVIiPjgxNDEyNTM8L2ViOlBhcnR5SWQ+PGViOlJvbGU+QmVoYW5kbGVyPC9lYjpSb2xlPjwvZWI6RnJvbT48ZWI6VG8+PGViOlBhcnR5SWQgZWI6dHlwZT0iSEVSIj43OTc2ODwvZWI6UGFydHlJZD48ZWI6Um9sZT5Lb250cm9sbFV0YmV0YWxlcjwvZWI6Um9sZT48L2ViOlRvPjxlYjpDUEFJZD5uYXY6cWFzczozNTA2NTwvZWI6Q1BBSWQ+PGViOkNvbnZlcnNhdGlvbklkPmJlMTkyZDNhLTM0YjUtNDQ4YS1hMzc0LTVlYWIwNTI0Yzc0ZDwvZWI6Q29udmVyc2F0aW9uSWQ+PGViOlNlcnZpY2UgZWI6dHlwZT0ic3RyaW5nIj5CZWhhbmRsZXJLcmF2PC9lYjpTZXJ2aWNlPjxlYjpBY3Rpb24+T3BwZ2pvcnNNZWxkaW5nPC9lYjpBY3Rpb24+PGViOk1lc3NhZ2VEYXRhPjxlYjpNZXNzYWdlSWQ+NzEwNGFjZjgtMjFlOS00ZWU3LWI4OTQtZDQxM2EwMGE4ODgxPC9lYjpNZXNzYWdlSWQ+PGViOlRpbWVzdGFtcD4yMDIzLTA4LTI5VDEwOjU2OjUwLjMwNjk0NzlaPC9lYjpUaW1lc3RhbXA+PC9lYjpNZXNzYWdlRGF0YT48L2ViOk1lc3NhZ2VIZWFkZXI+PGViOkFja1JlcXVlc3RlZCBTT0FQOmFjdG9yPSJ1cm46b2FzaXM6bmFtZXM6dGM6ZWJ4bWwtbXNnOmFjdG9yOnRvUGFydHlNU0giIFNPQVA6bXVzdFVuZGVyc3RhbmQ9IjEiIGViOnNpZ25lZD0idHJ1ZSIgZWI6dmVyc2lvbj0iMi4wIj48L2ViOkFja1JlcXVlc3RlZD48ZHM6U2lnbmF0dXJlIHhtbG5zOmRzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwLzA5L3htbGRzaWcjIj4KICA8ZHM6U2lnbmVkSW5mbz4KICAgIDxkczpDYW5vbmljYWxpemF0aW9uTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvVFIvMjAwMS9SRUMteG1sLWMxNG4tMjAwMTAzMTUiPjwvZHM6Q2Fub25pY2FsaXphdGlvbk1ldGhvZD4KICAgIDxkczpTaWduYXR1cmVNZXRob2QgQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxLzA0L3htbGRzaWctbW9yZSNyc2Etc2hhMjU2Ij48L2RzOlNpZ25hdHVyZU1ldGhvZD4KICAgIDxkczpSZWZlcmVuY2UgVVJJPSIiPgogICAgICA8ZHM6VHJhbnNmb3Jtcz4KICAgICAgICA8ZHM6VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI2VudmVsb3BlZC1zaWduYXR1cmUiPjwvZHM6VHJhbnNmb3JtPgogICAgICAgIDxkczpUcmFuc2Zvcm0gQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy9UUi8xOTk5L1JFQy14cGF0aC0xOTk5MTExNiI+CiAgICAgICAgICA8ZHM6WFBhdGggeG1sbnM6U09BUC1FTlY9Imh0dHA6Ly9zY2hlbWFzLnhtbHNvYXAub3JnL3NvYXAvZW52ZWxvcGUvIj5ub3QoYW5jZXN0b3Itb3Itc2VsZjo6bm9kZSgpW0BTT0FQLUVOVjphY3Rvcj0idXJuOm9hc2lzOm5hbWVzOnRjOmVieG1sLW1zZzphY3RvcjpuZXh0TVNIIl0gfCBhbmNlc3Rvci1vci1zZWxmOjpub2RlKClbQFNPQVAtRU5WOmFjdG9yPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2FjdG9yL25leHQiXSkKICAgICAgICA8L2RzOlhQYXRoPjwvZHM6VHJhbnNmb3JtPgogICAgICAgIDxkczpUcmFuc2Zvcm0gQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy9UUi8yMDAxL1JFQy14bWwtYzE0bi0yMDAxMDMxNSI+PC9kczpUcmFuc2Zvcm0+CiAgICAgIDwvZHM6VHJhbnNmb3Jtcz4KICAgICAgPGRzOkRpZ2VzdE1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMDQveG1sZW5jI3NoYTI1NiI+PC9kczpEaWdlc3RNZXRob2Q+CiAgICAgIDxkczpEaWdlc3RWYWx1ZT5NdzhZeFRlYnUycis3UTJ4Y216WDFDeGV0QTJiQWRRQ1VxSGV0ZWhITWFJPTwvZHM6RGlnZXN0VmFsdWU+CiAgICA8L2RzOlJlZmVyZW5jZT4KICAgIDxkczpSZWZlcmVuY2UgVVJJPSJjaWQ6M0NUR0k4VUtVS1U0LkFESEVVRE1EQ1kzUTNAc3BlYXJlLm5vIj4KICAgICAgPGRzOkRpZ2VzdE1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMDQveG1sZW5jI3NoYTI1NiI+PC9kczpEaWdlc3RNZXRob2Q+CiAgICAgIDxkczpEaWdlc3RWYWx1ZT5paE5IUWw4YTQ0R3dUeTcyQnE3bTBqeW44L0FwN25iU090Tml4NHN5Y1RzPTwvZHM6RGlnZXN0VmFsdWU+CiAgICA8L2RzOlJlZmVyZW5jZT4KICA8L2RzOlNpZ25lZEluZm8+CiAgPGRzOlNpZ25hdHVyZVZhbHVlPmdoK0Rhb0R6T0lPSGVrRzFRQ2w2OGhhNHdtaUU0ejVSSXE1dWhGYTZzVzQrRm0xWi9LemhjZW5RbGFsWEErV0hsL29IR0RXVVk4TjNoeXk1MUVndFZOSDI5QVpnSGVYUFIvYzdUQ3hDTytIVXZjNm5QMTl6VktEMGROMzRrRVlMNVBxTGsrLzltQXhKalAyYzNGbDg5czFNeDVqeDI2djkxcTBrQjBxRkdBa0tTeTR5VTExSzdhZEh3WWpqN3YwWVB6bi9pQWFuVUJLOUFSMEh2cUNwYlNJbWJSZ0ZRcTVmQ1BUbXRCVXBkbUxzUWlMMXE4Sy9ZQmVGdmh1V0xxYTNYeHZqT0xCVlJ6T1hpUUNvYldDWDM0M1NwVkRZMEtKMVc3K0RzL2ZuS2FhNS84Z2ZZY29UQWxwOHFYR2JLNlhqSFF3NWZUZW5Xd1ptQmg0U1ZnRnoxdFBQWkpsWnk3MHc0RytabGV5L2FNRHZxVHArd1FXa3JQQm1DYkNFVWxJMnFsNmlKM1ZMODZQR016cG5sZksrbXZuRXVxVnlKaDRTY3JTY2Z5ZnhLQllER3Z1OHFNSDFGcjN1QnJjZUhZN081QmE4QVpGK1p3NTBvQXQ5K2hpN2NTcXJ6NURjQkE4MStHdkFHcVI2OFNVdzF1d3hXZkxYNVBQQm8va25uYXlBPC9kczpTaWduYXR1cmVWYWx1ZT4KICA8ZHM6S2V5SW5mbz4KICAgIDxkczpLZXlWYWx1ZT4KICAgICAgPGRzOlJTQUtleVZhbHVlPjxkczpNb2R1bHVzPnNCNkdGTFBOYUZYWHRHdkNZSGJqVWtzWGpXZzdONUZKWGhvMFFLWFBwTSs3WE81Ti9PczEzcHhjWjd5TURXbFJuUjFiTjNUZVR0T29lVUpSdTY1UmJNVWZnYmMrcUJSY0l2K1ZuQUxROFNZK0YwVlM5SHJ5MTJXUE9nL0U1Z0xUSUhVMEwwV2QvWTN6NlpBVnVaTVNENHV3NkprdFB3WXJOcjdNc3RRRzY4cFoyTnE0aldXTEUyU3grZ0k0Q1pWbzZCUTh0c2p1YXNmTEdtcDhvL2JFK2NnUUdSZ2FlWFlQa0F5Z3NYYVkzdFMwOWJsNGVCYWQvNEJ4R2x2akR2MnR2eTl2SkRIdTVXRC9PMGQ5THlBWU9Odk5wK3hUYjhHQUx3TUhEem9MME5JY0tscXZaWXNsa0tCZzVNR0pRR2hVVm9XbmNzaTQwa3dPTkpldjVvTlJqSkZjQWxDcC90TEZBa1hQRTAvSzBseXVycXV0Mm9oY1hLMGZiVVp2MWZmMCtEL3MzbVBiRnp3RmFxN2RvTFg2U0h6QXdVTi9YVXM3ZWRMLzYvMVpHVlQvYmJ4TGZkZnNJaWEzRGt3NmtWVndqdTdMZnYxamdMOFZQSkYwN0VKNDA4ZlhUS0s4cEJGbGJsSkN0OGN5Z2RGbm8ydVR4TytIN2xtY29HNDd6Rm90PC9kczpNb2R1bHVzPjxkczpFeHBvbmVudD5BUUFCPC9kczpFeHBvbmVudD48L2RzOlJTQUtleVZhbHVlPgogICAgPC9kczpLZXlWYWx1ZT4KICAgIDxkczpYNTA5RGF0YT4KICAgICAgPGRzOlg1MDlDZXJ0aWZpY2F0ZT5NSUlHS3pDQ0JCT2dBd0lCQWdJTEFaVi9FVElUelJwUFcyQXdEUVlKS29aSWh2Y05BUUVMQlFBd2JqRUxNQWtHQTFVRUJoTUNUazh4R0RBV0JnTlZCR0VNRDA1VVVrNVBMVGs0TXpFMk16TXlOekVUTUJFR0ExVUVDZ3dLUW5WNWNHRnpjeUJCVXpFd01DNEdBMVVFQXd3blFuVjVjR0Z6Y3lCRGJHRnpjeUF6SUZSbGMzUTBJRU5CSUVjeUlGTlVJRUoxYzJsdVpYTnpNQjRYRFRJeU1Ea3lNakV4TXpReE4xb1hEVEkxTURreU1qSXhOVGt3TUZvd1R6RUxNQWtHQTFVRUJoTUNUazh4RWpBUUJnTlZCQW9NQ1ZOUVJVRlNSU0JCVXpFU01CQUdBMVVFQXd3SlUxQkZRVkpGSUVGVE1SZ3dGZ1lEVlFSaERBOU9WRkpPVHkwNU9UTTVOVFE0T1RZd2dnR2lNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUJqd0F3Z2dHS0FvSUJnUUN3SG9ZVXM4MW9WZGUwYThKZ2R1TlNTeGVOYURzM2tVbGVHalJBcGMra3o3dGM3azM4NnpYZW5GeG52SXdOYVZHZEhWczNkTjVPMDZoNVFsRzdybEZzeFIrQnR6Nm9GRndpLzVXY0F0RHhKajRYUlZMMGV2TFhaWTg2RDhUbUF0TWdkVFF2UlozOWpmUHBrQlc1a3hJUGk3RG9tUzAvQmlzMnZzeXkxQWJyeWxuWTJyaU5aWXNUWkxINkFqZ0psV2pvRkR5MnlPNXF4OHNhYW55ajlzVDV5QkFaR0JwNWRnK1FES0N4ZHBqZTFMVDF1WGg0RnAzL2dIRWFXK01PL2EyL0wyOGtNZTdsWVA4N1IzMHZJQmc0MjgybjdGTnZ3WUF2QXdjUE9ndlEwaHdxV3E5bGl5V1FvR0Rrd1lsQWFGUldoYWR5eUxqU1RBNDBsNi9tZzFHTWtWd0NVS24rMHNVQ1JjOFRUOHJTWEs2dXE2M2FpRnhjclI5dFJtL1Y5L1Q0UCt6ZVk5c1hQQVZxcnQyZ3RmcElmTURCUTM5ZFN6dDUwdi9yL1ZrWlZQOXR2RXQ5MSt3aUpyY09URHFSVlhDTzdzdCsvV09BdnhVOGtYVHNRbmpUeDlkTW9yeWtFV1Z1VWtLM3h6S0IwV2VqYTVQRTc0ZnVXWnlnYmp2TVdpMENBd0VBQWFPQ0FXY3dnZ0ZqTUFrR0ExVWRFd1FDTUFBd0h3WURWUjBqQkJnd0ZvQVVwLzY3YkZtSXJYUXVSbDU2YVBuUnU3L1B0b3N3SFFZRFZSME9CQllFRkI3YThoQ1hJWXIrK1hod2tHQjZkQ3lOY2xIaE1BNEdBMVVkRHdFQi93UUVBd0lHUURBZkJnTlZIU0FFR0RBV01Bb0dDR0NFUWdFYUFRTUNNQWdHQmdRQWozb0JBVEJCQmdOVkhSOEVPakE0TURhZ05LQXloakJvZEhSd09pOHZZM0pzTG5SbGMzUTBMbUoxZVhCaGMzTmpZUzVqYjIwdlFsQkRiRE5EWVVjeVUxUkNVeTVqY213d2V3WUlLd1lCQlFVSEFRRUViekJ0TUMwR0NDc0dBUVVGQnpBQmhpRm9kSFJ3T2k4dmIyTnpjR0p6TG5SbGMzUTBMbUoxZVhCaGMzTmpZUzVqYjIwd1BBWUlLd1lCQlFVSE1BS0dNR2gwZEhBNkx5OWpjblF1ZEdWemREUXVZblY1Y0dGemMyTmhMbU52YlM5Q1VFTnNNME5oUnpKVFZFSlRMbU5sY2pBbEJnZ3JCZ0VGQlFjQkF3UVpNQmN3RlFZSUt3WUJCUVVIQ3dJd0NRWUhCQUNMN0VrQkFqQU5CZ2txaGtpRzl3MEJBUXNGQUFPQ0FnRUFRdDd6Qkp4RkVGTThwaDVrZjcveVN4eFB6NHhQK0NNbERjRTQ3R2hzNGFuZ1JSNG1kQUNjRzhHWjVrYzRZWEVySEgvcUtDbzd2clVMTmcvQWo1ay9iTkpFY25NM09kZll2VjBTMmwvS0sybmlyUkFCN1FpKzVPYjdFNytjSU11WHVLTnNkeEUzOGNqVGsvZ2VReW42SnUrSUFnRm04L1o0Q0xNM2lZcTI1SXFxMmJpNGlxSlpMRUZGeVFCYThsYkR6WDY3NG5wdmlhdkIrT2k0U1NjSlpPdFYrSHd0VjhHWEtEZlBCOFNLSUtqcEFXRjFzcWlqbjNUNDVjTFdEbjg3dGVhVnRVUkN1K1ZyeFd1dmI0OFJKQlBvdGYzSnBIQnpLZUFRZk9keFZMRDJWdURJOUV0Qzc3WnZHV2JZMnZlOVZhOTlwWjd6MWlYTHZYaXFqY20rNEFLTnRqZ25MY1ZCRVl3MURaQk0vMFphUnYybzRQSzVtWC9mYUdlQTB6Q1FhMWRkOEJra1VXNkF2TEZIVVIyUUV3Y2JoZDc4UFI1d3RicW9BK0M5NDVISzZ1NzRWRFlscE1RU081SnRLZFpsZ29zY3VmNFJSaFBrREFQVWtLdHdjTDNqTzZlcDR5cjk1OHhMK0VWWWQ5dEtwYm1HQXJYd0Q5SmxFa2ZVUk1pMDZpSFhrUUtpd0VRMjZock5jZDRzbkJqc3Z0cVdtNkEwQmhHVG9MaFhUWUpOZlRZWk5oNUNHMTBDN0l6Qkd6RnF3RytaUW1ldTFSVjRsdElpSlFXbjZOTzMyZkZpNXBTa2ZKMDRPK1c2aHNhRmlJTUg3a2hnYUdZZFYzMnpmSFAzNFBqMXNmalVvV21LSXlVMUozZ2lmV25pZGhaZ0ZOeCtzZW5DVE1CSFlIVT08L2RzOlg1MDlDZXJ0aWZpY2F0ZT4KICAgIDwvZHM6WDUwOURhdGE+CiAgPC9kczpLZXlJbmZvPgo8L2RzOlNpZ25hdHVyZT48L1NPQVA6SGVhZGVyPjxTT0FQOkJvZHk+PGViOk1hbmlmZXN0IGViOnZlcnNpb249IjIuMCI+PGViOlJlZmVyZW5jZSB4bGluazpocmVmPSJjaWQ6M0NUR0k4VUtVS1U0LkFESEVVRE1EQ1kzUTNAc3BlYXJlLm5vIiB4bGluazp0eXBlPSJzaW1wbGUiPjwvZWI6UmVmZXJlbmNlPjwvZWI6TWFuaWZlc3Q+PC9TT0FQOkJvZHk+PC9TT0FQOkVudmVsb3BlPg=="""
const val FAGMELDING_PAYLOAD: String = """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"""

class MultipartRequest(val headers: Headers, val parts: List<Part>)

class SinglePartRequest(headers: Headers, payload: String) : Part(headers, payload)

open class Part(val headers: Headers, val payload: String)

fun validMultipartRequest(): MultipartRequest {
    return MultipartRequest(
        valid.modify {
            it.append(SMTPHeaders.MESSAGE_ID, "12345")
        },
        listOf(
            Part(validSoapMimeHeaders, EBXML_PAYLOAD),
            Part(validSoapAttachmentHeaders, FAGMELDING_PAYLOAD)
        )
    )
}
val validSMTPHeaders = Headers.build {
    append(SMTPHeaders.MESSAGE_ID, "12345")
}

val valid = Headers.build {
    append(MimeHeaders.MIME_VERSION, "1.0")
    append(MimeHeaders.SOAP_ACTION, "ebXML")
    append(MimeHeaders.CONTENT_TYPE, MULTIPART_CONTENT_TYPE)
}

val validSoapMimeHeaders = Headers.build {
    append(MimeHeaders.CONTENT_ID, "<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>")
    append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
    append(MimeHeaders.CONTENT_TYPE, """text/xml; charset="UTF-8"""")
}

val validSoapAttachmentHeaders = Headers.build {
    append(MimeHeaders.CONTENT_ID, "<3CTGI8UKUKU4.ADHEUDMDCY3Q3@speare.no>")
    append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
    append(MimeHeaders.CONTENT_DISPOSITION, "attachment")
    append(MimeHeaders.CONTENT_TYPE, """application/pkcs7-mime; smimetype=enveloped-data"""")
}

public fun Headers.modify(block: (HeadersBuilder) -> Unit) = Headers.build {
    this@modify.entries().forEach {
        this.append(it.key, it.value.first())
        this.apply(block)
    }
}

fun MultipartRequest.modify(block: (HeadersBuilder) -> Unit): MultipartRequest {
    val headers = Headers.build {
        this@modify.headers.entries().forEach {
            this.append(it.key, it.value.first())
            this.apply(block)
        }
    }
    return MultipartRequest(headers, this.parts)
}

fun MultipartRequest.modify(pair: Pair<Part, Part>): MultipartRequest {
    return MultipartRequest(headers, parts.swap(pair))
}

fun Part.modify(block: (HeadersBuilder) -> Unit): Part {
    val headers = Headers.build {
        this@modify.headers.entries().forEach {
            this.append(it.key, it.value.first())
            this.apply(block)
        }
    }
    return Part(headers, this.payload)
}

fun Part.payload(payload: String): Part {
    val headers = Headers.build {
        this@payload.headers.entries().forEach {
            this.append(it.key, it.value.first())
        }
    }
    return Part(headers, payload)
}

fun MultipartRequest.asHttpRequest(): HttpRequestBuilder.() -> Unit {
    return {
        headers {
            this@asHttpRequest.headers.entries().forEach {
                append(it.key, it.value.first())
            }
        }
        val partData = this@asHttpRequest.parts.map { PartData.FormItem(it.payload, {}, it.headers) }
        setBody(
            MultiPartFormDataContent(
                partData,
                boundary = "----=_Part_495_-1172936255.1665395092859",
                ContentType.parse(MULTIPART_CONTENT_TYPE)
            )
        )
    }
}

fun List<Part>.swap(pair: Pair<Part, Part>): List<Part> {
    val itemIndex = indexOf(pair.first)
    return if (itemIndex == -1) {
        this.toList()
    } else {
        slice(0 until itemIndex) + pair.second + slice(itemIndex + 1 until size)
    }
}
