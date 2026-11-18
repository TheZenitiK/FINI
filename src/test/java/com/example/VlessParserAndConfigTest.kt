package com.example

import com.example.core.logging.AppLogger
import com.example.core.network.InstalledAppsProvider
import com.example.core.network.SharedHttpClient
import com.example.core.parser.ClientSpoofDiagnostics
import com.example.core.parser.ParsedImportResult
import com.example.core.parser.SubscriptionFetcher
import com.example.core.parser.VlessUriParser
import com.example.core.parser.XrayConfigBuilder
import com.example.data.local.MincifryWhitelist
import com.example.data.local.SettingsDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.InstalledAppInfo
import com.example.data.model.SecurityType
import com.example.data.model.Subscription
import com.example.data.model.ThemeMode
import com.example.data.model.TransportType
import com.example.data.model.VpnProtocol
import com.example.data.model.VpnStatus
import com.example.data.model.formatSubscriptionRemainingTime
import com.example.ui.components.isDarkThemeActive
import com.example.ui.components.isHolidaySnowSeason
import com.example.ui.viewmodel.HomeUiState
import com.example.vpn.GRayVpnService
import com.example.vpn.VpnRuntimeState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VlessParserAndConfigTest {

    @Test
    fun parsesVlessRealityUriWithSpacesAndCyrillic() {
        val uri = "vless://550e8400-e29b-41d4-a716-446655440000@185.10.20.30:443" +
            "?type=tcp&security=reality&pbk=TestPubKey123&sid=a1b2&fp=chrome&sni=www.google.com&flow=xtls-rprx-vision#🇩🇪 Германия | Быстрый"

        val result = VlessUriParser.parseClipboardOrQrInput(uri)
        assertTrue(result is ParsedImportResult.SingleOrList)
        val server = (result as ParsedImportResult.SingleOrList).servers.first()

        assertEquals(VpnProtocol.VLESS, server.protocol)
        assertEquals(TransportType.TCP, server.transport)
        assertEquals(SecurityType.REALITY, server.security)
        assertEquals("185.10.20.30", server.host)
        assertEquals(443, server.port)
        assertEquals("TestPubKey123", server.publicKey)
        assertEquals("a1b2", server.shortId)
        assertEquals("www.google.com", server.sni)
        assertEquals("xtls-rprx-vision", server.flow)
        assertEquals("🇩🇪", server.countryFlag)
        assertFalse(server.isAutoSelect)
        assertEquals("REALITY", server.subProtocolLabel)
    }

    @Test
    fun detectsWifiAutoSelectNode() {
        val uri = "vless://00000000-0000-0000-0000-000000000000@127.0.0.1:1" +
            "?type=tcp&security=none#Wi-Fi автовыбор"
        val server = VlessUriParser.parseSingleUri(uri)!!
        assertTrue(server.isAutoSelect)
        assertEquals("AUTO", server.subProtocolLabel)
        assertEquals("⚡", server.countryFlag)
    }

    @Test
    fun parsesVlessXhttpUri() {
        val uri = "vless://550e8400-e29b-41d4-a716-446655440000@nl.node.org:443" +
            "?type=xhttp&security=tls&path=%2Fxhttp&host=cdn.node.org&mode=packet-up#Amsterdam%20XHTTP"

        val server = VlessUriParser.parseSingleUri(uri)!!
        assertEquals(TransportType.XHTTP, server.transport)
        assertEquals(SecurityType.TLS, server.security)
        assertEquals("/xhttp", server.path)
        assertEquals("cdn.node.org", server.hostHeader)
        assertEquals("packet-up", server.xhttpMode)
        assertEquals("🇳🇱", server.countryFlag)
        assertFalse(server.isAutoSelect)
        assertEquals("Xhttp", server.subProtocolLabel)
    }

    @Test
    fun preservesExactSubscriptionOrder() {
        val payload = listOf(
            "vless://550e8400-e29b-41d4-a716-446655440000@1.1.1.1:443?security=reality&pbk=k#Germany Standard",
            "vless://550e8400-e29b-41d4-a716-446655440000@2.2.2.2:443?security=reality&pbk=k#Finland LTE"
        ).joinToString("\n")

        val list = VlessUriParser.parseSubscriptionBody(payload, subscriptionId = "sub1")
        assertEquals(2, list.size)
        assertEquals(0, list[0].sortOrder)
        assertEquals(1, list[1].sortOrder)
        assertTrue(list[0].name.contains("Germany"))
        assertTrue(list[1].name.contains("Finland LTE"))
    }

    @Test
    fun deduplicatesRepeatedServersAndAutoSelectNodesInSubscription() {
        val u1 = "vless://550e8400-e29b-41d4-a716-446655440000@1.1.1.1:443?security=reality&pbk=k&sni=google.com#Switzerland"
        val u1Dup = "vless://550e8400-e29b-41d4-a716-446655440000@1.1.1.1:443?security=reality&pbk=k&sni=google.com#Switzerland"
        val auto1 = "vless://00000000-0000-0000-0000-000000000000@127.0.0.1:1?security=none#Wi-Fi автовыбор"
        val auto1Dup = "vless://00000000-0000-0000-0000-000000000000@127.0.0.2:1?security=none#Wi-Fi автовыбор"

        val list = VlessUriParser.parseSubscriptionBody(
            listOf(auto1, u1, auto1Dup, u1Dup).joinToString("\n"),
            subscriptionId = "sub_dedup"
        )
        assertEquals(2, list.size)
        assertEquals(0, list[0].sortOrder)
        assertEquals(1, list[1].sortOrder)
    }

    @Test
    fun extractsPrimaryOutboundFromMultiConfigJsonArray() {
        val jsonArray = """
            [
              {
                "remarks": "🇨🇭 Швейцария",
                "outbounds": [
                  {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": { "vnext": [{ "address": "ch.example.com", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                    "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk1", "serverName": "example.com" } }
                  },
                  {
                    "tag": "backup",
                    "protocol": "vless",
                    "settings": { "vnext": [{ "address": "ch2.example.com", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                    "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk1", "serverName": "example.com" } }
                  },
                  { "tag": "direct", "protocol": "freedom" }
                ]
              }
            ]
        """.trimIndent()

        val list = VlessUriParser.parseSubscriptionBody(jsonArray, subscriptionId = "sub_json")
        assertEquals(1, list.size)
        assertEquals("ch.example.com", list[0].host)
    }

    @Test
    fun validatesClientSpoofHeaders() {
        val happHeaders = ClientSpoofDiagnostics.buildHeaders(
            happSpoofEnabled = true,
            sendDeviceHwid = true,
            deviceHwid = "test-hwid-123"
        )
        assertTrue(happHeaders["User-Agent"]!!.startsWith("Happ/4.10.1"))
        assertEquals("test-hwid-123", happHeaders["x-hwid"])
        assertEquals("Android", happHeaders["x-device-os"])

        val defaultNoHwid = ClientSpoofDiagnostics.buildHeaders(
            happSpoofEnabled = false,
            sendDeviceHwid = false,
            deviceHwid = "test-hwid-123"
        )
        assertTrue(defaultNoHwid["User-Agent"]!!.startsWith("FINI/"))
        assertFalse(defaultNoHwid.containsKey("x-hwid"))
        assertFalse(defaultNoHwid.containsKey("x-device-os"))
    }

    @Test
    fun checksHolidaySnowSeasonBoundariesAndDarkThemeGate() {
        fun cal(month: Int, day: Int) = Calendar.getInstance().apply { set(2026, month, day) }
        assertFalse(isHolidaySnowSeason(cal(Calendar.DECEMBER, 14)))
        assertTrue(isHolidaySnowSeason(cal(Calendar.DECEMBER, 15)))
        assertTrue(isHolidaySnowSeason(cal(Calendar.DECEMBER, 31)))
        assertTrue(isHolidaySnowSeason(cal(Calendar.JANUARY, 1)))
        assertTrue(isHolidaySnowSeason(cal(Calendar.JANUARY, 15)))
        assertFalse(isHolidaySnowSeason(cal(Calendar.JANUARY, 16)))

        assertTrue(isDarkThemeActive(ThemeMode.DARK, systemInDarkTheme = false))
        assertTrue(isDarkThemeActive(ThemeMode.AMOLED, systemInDarkTheme = false))
        assertFalse(isDarkThemeActive(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertTrue(isDarkThemeActive(ThemeMode.SYSTEM, systemInDarkTheme = true))
        assertFalse(isDarkThemeActive(ThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun loadsMincifryWhitelistAndCleansAvitoShards() {
        MincifryWhitelist.init(RuntimeEnvironment.getApplication())
        val domains = MincifryWhitelist.defaultDomains
        assertTrue(domains.size > 700)
        assertTrue("avito.ru" in domains)
        assertTrue("avito.st" in domains)
        assertTrue("img.avito.st" in domains)
        assertFalse("00.img.avito.st" in domains)
        assertFalse("99.img.avito.st" in domains)
    }

    @Test
    fun validatesCustomRouteDomainsStrictly() {
        assertEquals("example.com", MincifryWhitelist.normalizeAndValidateDomain("https://www.Example.com/path?x=1"))
        assertEquals("госуслуги.рф", MincifryWhitelist.normalizeAndValidateDomain("Госуслуги.рф"))
        assertNull(MincifryWhitelist.normalizeAndValidateDomain("asdfgh"))
        assertNull(MincifryWhitelist.normalizeAndValidateDomain("hello world.com"))
        assertNull(MincifryWhitelist.normalizeAndValidateDomain("fake.notarealtld999"))
    }

    @Test
    fun detectsSubscriptionUrl() {
        val result = VlessUriParser.parseClipboardOrQrInput("https://sub.example.com/api/v1/client/subscribe?token=abc")
        assertTrue(result is ParsedImportResult.SubscriptionUrl)
    }

    @Test
    fun buildsValidXrayJsonWithSeparatedCustomRoutes() {
        val uri = "vless://550e8400-e29b-41d4-a716-446655440000@1.2.3.4:443" +
            "?type=xhttp&security=reality&pbk=PubKey&sid=12ab&sni=example.com#Finland"
        val server = VlessUriParser.parseSingleUri(uri)!!
        val settings = AppSettings(
            bypassLan = true,
            bypassRussianDomains = true,
            bypassMincifry = true,
            customRoutesEnabled = true,
            customDirectDomains = setOf("mycustomsite.org")
        )

        val jsonText = XrayConfigBuilder.buildConfigJson(server, settings, socksPort = 10808)
        val json = JSONObject(jsonText)

        assertEquals(10808, json.getJSONArray("inbounds").getJSONObject(0).getInt("port"))
        val proxyOutbound = json.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", proxyOutbound.getString("protocol"))
        val stream = proxyOutbound.getJSONObject("streamSettings")
        assertEquals("xhttp", stream.getString("network"))
        assertEquals("reality", stream.getString("security"))

        val rules = json.getJSONObject("routing").getJSONArray("rules")
        assertEquals(4, rules.length())
    }

    @Test
    fun selectsNewServerCleanlyAfterDisconnect() {
        val s1 = VlessUriParser.parseSingleUri("vless://550e8400-e29b-41d4-a716-446655440000@1.1.1.1:443?security=reality&pbk=k#NodeOne")!!
        val s2 = VlessUriParser.parseSingleUri("vless://550e8400-e29b-41d4-a716-446655440000@2.2.2.2:443?security=reality&pbk=k#NodeTwo")!!

        val connectedState = HomeUiState(
            servers = listOf(s1, s2),
            settings = AppSettings(selectedServerId = s1.id),
            vpn = VpnRuntimeState(status = VpnStatus.CONNECTED, activeServer = s1)
        )
        assertEquals(s1.id, connectedState.activeSelectedServer?.id)

        val disconnectedAndSwitched = HomeUiState(
            servers = listOf(s1, s2),
            settings = AppSettings(selectedServerId = s2.id),
            vpn = VpnRuntimeState(status = VpnStatus.DISCONNECTED, activeServer = s1)
        )
        assertEquals(s2.id, disconnectedAndSwitched.activeSelectedServer?.id)
    }

    @Test
    fun formatsSubscriptionRemainingTimeAccurately() {
        val now = 1_700_000_000_000L
        assertEquals("Без срока", formatSubscriptionRemainingTime(0L, isRussian = true, nowMs = now))
        assertEquals("No expiration", formatSubscriptionRemainingTime(0L, isRussian = false, nowMs = now))
        assertEquals("Истекла", formatSubscriptionRemainingTime(now - 60_000L, isRussian = true, nowMs = now))

        val twoDaysThreeHoursSec = (now + (2 * 24 + 3) * 3600_000L) / 1000L
        assertEquals("2 дн. 3 ч.", formatSubscriptionRemainingTime(twoDaysThreeHoursSec, isRussian = true, nowMs = now))
        assertEquals("2d 3h", formatSubscriptionRemainingTime(twoDaysThreeHoursSec, isRussian = false, nowMs = now))
    }

    @Test
    fun filtersSystemAppsOnlyWhenCheckboxChecked() {
        val apps = listOf(
            InstalledAppInfo("org.telegram.messenger", "Telegram", isSystemApp = false),
            InstalledAppInfo("com.android.chrome", "Chrome", isSystemApp = true)
        )
        val userOnly = apps.filter { false || !it.isSystemApp }
        val withSystem = apps.filter { true || !it.isSystemApp }
        assertEquals(1, userOnly.size)
        assertEquals("org.telegram.messenger", userOnly.first().packageName)
        assertEquals(2, withSystem.size)
    }

    @Test
    fun enforcesAppLogger300LineRingBufferAndDisabledState() {
        try {
            AppLogger.clear()
            AppLogger.setEnabled(false)
            AppLogger.log("TEST", "should be ignored")
            assertTrue(AppLogger.logs.value.isEmpty())

            AppLogger.setEnabled(true)
            for (i in 1..350) {
                AppLogger.log("TEST", "line-$i")
            }
            val lines = AppLogger.logs.value
            assertEquals(300, lines.size)
            assertTrue(lines.any { it.contains("line-350") })
            assertTrue(lines.last().contains("line-350"))
            assertFalse(lines.any { it.contains("line-1]") || it.endsWith("line-1") })
        } finally {
            AppLogger.clear()
            AppLogger.setEnabled(false)
        }
    }

    @Test
    fun extractsOptionalServerCarrierNotesCleanly() {
        val withNoteUri = "vless://550e8400-e29b-41d4-a716-446655440000@1.2.3.4:443?security=reality&pbk=k#🇫🇮 Финляндия (Безлимит) [Кроме МТС]"
        val withNote = VlessUriParser.parseSingleUri(withNoteUri)!!
        assertEquals("Финляндия", withNote.name)
        assertEquals("Безлимит • Кроме МТС", withNote.note)

        val plainUri = "vless://550e8400-e29b-41d4-a716-446655440000@1.2.3.4:443?security=reality&pbk=k#🇩🇪 Германия Быстрый"
        val plain = VlessUriParser.parseSingleUri(plainUri)!!
        assertEquals("Германия Быстрый", plain.name)
        assertEquals("", plain.note)
    }

    @Test
    fun parsesSubscriptionExternalLinksFromHeadersAndCommentsOnly() {
        val subUrl = "https://sub.example.com/sub/123"
        val headerLinks = SubscriptionFetcher.parseExternalLinksFromHeaders(
            supportUrl = "https://t.me/finivpn_support",
            webPageUrl = "https://panel.example.com/profile",
            announceUrl = null,
            subscriptionUrl = subUrl
        )
        assertEquals(2, headerLinks.size)
        assertEquals("Telegram / Поддержка", headerLinks[0].label)

        val commentLinks = SubscriptionFetcher.parseExternalLinksFromBodyComments(
            rawBody = """
                #support https://t.me/help_channel
                #ignore self $subUrl
                vless://550e8400-e29b-41d4-a716-446655440000@1.2.3.4:443#Node
            """.trimIndent(),
            subscriptionUrl = subUrl
        )
        assertEquals(1, commentLinks.size)
        assertEquals("https://t.me/help_channel", commentLinks[0].url)
    }

    @Test
    fun doesNotFanOutSingleXrayJsonWithBlankRemarksAndProxyTag() {
        val singleJson = """
            {
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "de.example.com", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                  "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk1", "serverName": "example.com" } }
                },
                {
                  "tag": "fallback-node",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "de2.example.com", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                  "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk1", "serverName": "example.com" } }
                },
                { "tag": "direct", "protocol": "freedom" }
              ]
            }
        """.trimIndent()

        val list = VlessUriParser.parseSubscriptionBody(singleJson)
        assertEquals(1, list.size)
        assertEquals("de.example.com", list[0].host)
    }

    @Test
    fun parsesHysteria2UriAndBuildsXrayConfig() {
        val uri = "hysteria2://my-secret-pass@hy2.example.com:8443,10000-20000" +
            "?sni=cdn.hy2.org&obfs=salamander&obfs-password=obfspass&pinSHA256=AA:BB:CC" +
            "&upmbps=50&downmbps=200&insecure=1#🇩🇪 Hysteria Node"

        val server = VlessUriParser.parseSingleUri(uri)!!
        assertEquals(VpnProtocol.HYSTERIA2, server.protocol)
        assertEquals("QUIC • Salamander", server.subProtocolLabel)
        assertEquals("hy2.example.com", server.host)
        assertEquals(8443, server.port)
        assertEquals("my-secret-pass", server.uuidOrPassword)
        assertEquals("cdn.hy2.org", server.sni)
        assertEquals("salamander", server.obfs)
        assertEquals("obfspass", server.obfsPassword)
        assertEquals("aabbcc", server.pinSha256)
        assertEquals(50, server.upMbps)
        assertEquals(200, server.downMbps)
        assertEquals("8443,10000-20000", server.ports)
        assertTrue(server.allowInsecure)

        val hy2Short = VlessUriParser.parseSingleUri("hy2://pw@1.2.3.4:443?sni=example.com&mport=20000-30000#Short Hy2")!!
        assertEquals(VpnProtocol.HYSTERIA2, hy2Short.protocol)
        assertEquals("QUIC", hy2Short.subProtocolLabel)
        assertEquals("20000-30000", hy2Short.ports)

        val json = JSONObject(XrayConfigBuilder.buildConfigJson(server, AppSettings()))
        val proxy = json.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria", proxy.getString("protocol"))
        val stream = proxy.getJSONObject("streamSettings")
        assertEquals("hysteria", stream.getString("network"))
        assertEquals("tls", stream.getString("security"))
        assertEquals("my-secret-pass", stream.getJSONObject("hysteriaSettings").getString("auth"))
        val finalMask = stream.getJSONObject("finalmask")
        assertEquals("salamander", finalMask.getJSONArray("udp").getJSONObject(0).getString("type"))
        assertEquals("brutal", finalMask.getJSONObject("quicParams").getString("congestion"))
        assertEquals("8443,10000-20000", finalMask.getJSONObject("quicParams").getJSONObject("udpHop").getString("ports"))
    }

    @Test
    fun parsesVlessGrpcWithMultiModeAndAuthorityAndBuildsXrayConfig() {
        val uri = "vless://550e8400-e29b-41d4-a716-446655440000@grpc.example.com:443" +
            "?type=grpc&security=tls&serviceName=mygrpc&mode=multi&authority=auth.example.com&sni=grpc.example.com#gRPC Node"
        val server = VlessUriParser.parseSingleUri(uri)!!
        assertEquals(TransportType.GRPC, server.transport)
        assertEquals("mygrpc", server.path)
        assertEquals("multi", server.xhttpMode)
        assertEquals("auth.example.com", server.hostHeader)

        val json = JSONObject(XrayConfigBuilder.buildConfigJson(server, AppSettings()))
        val grpc = json.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings")
            .getJSONObject("grpcSettings")
        assertEquals("mygrpc", grpc.getString("serviceName"))
        assertTrue(grpc.getBoolean("multiMode"))
        assertEquals("auth.example.com", grpc.getString("authority"))
    }

    @Test
    fun parsesClashYamlWithHysteria2VmessTrojanShadowsocks() {
        val yaml = """
            proxies:
              - name: Hy2 YAML
                type: hysteria2
                server: hy.example.com
                port: 443
                password: hy-pw
                sni: hy.example.com
                obfs: salamander
                obfs-password: mask
              - name: Trojan YAML
                type: trojan
                server: tr.example.com
                port: 443
                password: tr-pw
                network: grpc
                grpc-service-name: trgrpc
              - name: VMess YAML
                type: vmess
                server: vm.example.com
                port: 8443
                uuid: 550e8400-e29b-41d4-a716-446655440000
                tls: true
                network: ws
                ws-path: /ws
              - name: SS YAML
                type: ss
                server: ss.example.com
                port: 8388
                cipher: chacha20-ietf-poly1305
                password: ss-pw
        """.trimIndent()

        val list = VlessUriParser.parseSubscriptionBody(yaml, subscriptionId = "sub_yaml")
        assertEquals(4, list.size)
        assertEquals(VpnProtocol.HYSTERIA2, list[0].protocol)
        assertEquals("salamander", list[0].obfs)
        assertEquals(VpnProtocol.TROJAN, list[1].protocol)
        assertEquals(TransportType.GRPC, list[1].transport)
        assertEquals("trgrpc", list[1].path)
        assertEquals(VpnProtocol.VMESS, list[2].protocol)
        assertEquals(TransportType.WS, list[2].transport)
        assertEquals(VpnProtocol.SHADOWSOCKS, list[3].protocol)
        assertEquals("chacha20-ietf-poly1305", list[3].encryption)
    }

    @Test
    fun parsesHysteria2FromXrayJson() {
        val json = """
            {
              "remarks": "HY2 JSON",
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "hysteria",
                  "settings": { "version": 2, "address": "hyjson.example.com", "port": 8443 },
                  "streamSettings": {
                    "network": "hysteria",
                    "security": "tls",
                    "tlsSettings": { "serverName": "hyjson.example.com", "alpn": ["h3"] },
                    "hysteriaSettings": { "version": 2, "auth": "json-pass", "up": "20 mbps", "down": "100 mbps" },
                    "finalmask": { "udp": [{ "type": "salamander", "settings": { "password": "sal" } }] }
                  }
                }
              ]
            }
        """.trimIndent()

        val list = VlessUriParser.parseSubscriptionBody(json)
        assertEquals(1, list.size)
        assertEquals(VpnProtocol.HYSTERIA2, list[0].protocol)
        assertEquals("json-pass", list[0].uuidOrPassword)
        assertEquals("salamander", list[0].obfs)
        assertEquals("sal", list[0].obfsPassword)
        assertEquals(20, list[0].upMbps)
        assertEquals(100, list[0].downMbps)
    }

    @Test
    fun unwrapsDeepLinkQrSubscriptionUrls() {
        val rawHapp = "happ://add/https://sub.example.com/api/v1/client/subscribe?token=xyz123"
        val parsedHapp = VlessUriParser.parseClipboardOrQrInput(rawHapp)
        assertTrue(parsedHapp is ParsedImportResult.SubscriptionUrl)
        assertEquals(
            "https://sub.example.com/api/v1/client/subscribe?token=xyz123",
            (parsedHapp as ParsedImportResult.SubscriptionUrl).url
        )

        val rawEncoded = "v2raytun://import/https%3A%2F%2Fpanel.example.org%2Fsub%2Fabc"
        val parsedEncoded = VlessUriParser.parseClipboardOrQrInput(rawEncoded)
        assertTrue(parsedEncoded is ParsedImportResult.SubscriptionUrl)
        assertEquals(
            "https://panel.example.org/sub/abc",
            (parsedEncoded as ParsedImportResult.SubscriptionUrl).url
        )
    }

    @Test
    fun formatsNotificationShadeContentBasedOnToggle() {
        val hiddenDetailsRu = GRayVpnService.buildNotificationContentText(
            showDetails = false,
            elapsedSec = 125L,
            rxSpeedBps = 1024L,
            txSpeedBps = 512L,
            rxTotalBytes = 1024L,
            txTotalBytes = 1024L,
            isRussian = true
        )
        assertEquals("Подключено", hiddenDetailsRu)

        val hiddenDetailsEn = GRayVpnService.buildNotificationContentText(
            showDetails = false,
            elapsedSec = 125L,
            rxSpeedBps = 1024L,
            txSpeedBps = 512L,
            rxTotalBytes = 1024L,
            txTotalBytes = 1024L,
            isRussian = false
        )
        assertEquals("Connected", hiddenDetailsEn)

        val shownDetailsRu = GRayVpnService.buildNotificationContentText(
            showDetails = true,
            elapsedSec = 65L,
            rxSpeedBps = 1024L,
            txSpeedBps = 512L,
            rxTotalBytes = 2048L,
            txTotalBytes = 2048L,
            isRussian = true
        )
        assertTrue(shownDetailsRu.contains("00:01:05"))
        assertTrue(shownDetailsRu.contains("↓"))
        assertTrue(shownDetailsRu.contains("↑"))
    }

    @Test
    fun verifiesNewDefaultsAndStartupLogBannerWithoutLimitWording() {
        val defaults = AppSettings()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(AppLanguage.SYSTEM, defaults.language)
        assertTrue(defaults.showVpnStatusNotification)
        assertFalse(defaults.experimentalModeEnabled)
        assertFalse(defaults.onboardingCompleted)
        assertFalse(defaults.disconnectOnWifi)
        assertFalse(defaults.networkAutoSwitchActive)
        assertFalse(defaults.showSpeedInNotification)
        assertFalse(defaults.sortByPingEnabled)
        assertFalse(defaults.bypassMincifry)
        assertFalse(defaults.customRoutesEnabled)
        assertFalse(defaults.pinConnectCard)

        val sub = Subscription(
            id = "sub1",
            name = "Test",
            url = "https://example.com/sub",
            lastUpdatedAt = 1_700_000_000_000L
        )
        assertFalse(sub.pingOnLaunch)

        try {
            AppLogger.clear()
            AppLogger.setEnabled(true)
            AppLogger.log("FINI", "Журнал событий включён")
            val logText = AppLogger.logs.value.joinToString("\n")
            assertTrue(logText.contains("Журнал событий включён"))
            assertFalse(logText.contains("300 строк"))
        } finally {
            AppLogger.clear()
            AppLogger.setEnabled(false)
        }
    }

    @Test
    fun parsesSingleJsonConfigWith4OutboundsNoProxyTagAndBuildsPreservingBalancers() {
        val multiOutboundJson = """
            {
              "remarks": "🇩🇪 Multi-Route JSON",
              "outbounds": [
                {
                  "tag": "node-1",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "10.0.0.1", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                  "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk1", "serverName": "one.example.com" } }
                },
                {
                  "tag": "node-2",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "10.0.0.2", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                  "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk2", "serverName": "two.example.com" } }
                },
                {
                  "tag": "node-3",
                  "protocol": "trojan",
                  "settings": { "servers": [{ "address": "10.0.0.3", "port": 443, "password": "pw3" }] }
                },
                {
                  "tag": "node-4",
                  "protocol": "shadowsocks",
                  "settings": { "servers": [{ "address": "10.0.0.4", "port": 8388, "method": "aes-256-gcm", "password": "pw4" }] }
                },
                { "tag": "direct", "protocol": "freedom" }
              ],
              "observatory": {
                "subjectSelector": ["node-1", "node-2"],
                "probeInterval": "1m"
              },
              "routing": {
                "domainStrategy": "AsIs",
                "balancers": [
                  { "tag": "balancer", "selector": ["node-1", "node-2"], "strategy": { "type": "leastPing" } }
                ],
                "rules": [
                  { "type": "field", "network": "tcp,udp", "balancerTag": "balancer" }
                ]
              }
            }
        """.trimIndent()

        val parsed = VlessUriParser.parseSubscriptionBody(multiOutboundJson, subscriptionId = "sub_multi")
        assertEquals(1, parsed.size)
        val server = parsed.first()
        assertFalse(server.customJsonConfig.isNullOrBlank())

        val built = JSONObject(
            XrayConfigBuilder.buildConfigJson(
                server = server,
                settings = AppSettings(bypassLan = true),
                socksPort = 10808
            )
        )
        assertEquals(10808, built.getJSONArray("inbounds").getJSONObject(0).getInt("port"))
        assertEquals(6, built.getJSONArray("outbounds").length())
        assertTrue(built.has("observatory"))
        val routing = built.getJSONObject("routing")
        assertEquals(1, routing.getJSONArray("balancers").length())
        assertTrue(routing.getJSONArray("rules").length() >= 2)
    }

    @Test
    fun parsesMixedUriAndFullXrayJsonSubscriptionWithoutAutoSelectOverrideAndExpandsGeoipPrivate() {
        val uri1 = "vless://550e8400-e29b-41d4-a716-446655440000@198.51.100.10:443?type=tcp&security=reality&pbk=pk1&sni=a.example.com#🇩🇪 Германия"
        val balancerJson = """
            {
              "remarks": "🇪🇺 Европа | Основной",
              "meta": { "serverDescription": "Любой оператор" },
              "burstObservatory": {
                "subjectSelector": ["proxy-"],
                "pingConfig": { "destination": "https://www.gstatic.com/generate_204", "interval": "2m" }
              },
              "inbounds": [
                { "tag": "socks-in", "listen": "127.0.0.1", "port": 1080, "protocol": "socks" }
              ],
              "outbounds": [
                {
                  "tag": "proxy-1",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "198.51.100.11", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                  "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk2", "serverName": "b.example.com" } }
                },
                {
                  "tag": "proxy-2",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "198.51.100.12", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }] }] },
                  "streamSettings": { "network": "tcp", "security": "reality", "realitySettings": { "publicKey": "pk3", "serverName": "c.example.com" } }
                },
                { "tag": "direct", "protocol": "freedom" },
                { "tag": "block", "protocol": "blackhole" }
              ],
              "routing": {
                "domainStrategy": "IPIfNonMatch",
                "balancers": [
                  { "tag": "Super_Balancer", "selector": ["proxy-"], "strategy": { "type": "leastLoad" } }
                ],
                "rules": [
                  { "type": "field", "ip": ["geoip:private"], "outboundTag": "direct" },
                  { "type": "field", "network": "tcp,udp", "balancerTag": "Super_Balancer" }
                ]
              }
            }
        """.trimIndent()
        val finalmaskJson = """
            {
              "remarks": "🇫🇮 Финляндия",
              "meta": { "serverDescription": "Безлимитный" },
              "dns": {
                "servers": [
                  "1.1.1.1",
                  { "address": "localhost", "domains": ["geosite:private"], "expectIPs": ["geoip:private"] }
                ]
              },
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "settings": { "vnext": [{ "address": "198.51.100.20", "port": 443, "users": [{ "id": "550e8400-e29b-41d4-a716-446655440000", "flow": "xtls-rprx-vision" }] }] },
                  "streamSettings": {
                    "network": "tcp",
                    "security": "reality",
                    "realitySettings": { "publicKey": "pk4", "serverName": "d.example.com", "mlkem768": "dummy" },
                    "finalmask": { "tcp": [{ "type": "header-custom" }] }
                  }
                },
                { "tag": "direct", "protocol": "freedom" }
              ],
              "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                  { "type": "field", "ip": ["geoip:private"], "outboundTag": "direct" },
                  { "type": "field", "domain": ["geosite:category-ads"], "outboundTag": "block" }
                ]
              }
            }
        """.trimIndent()
        val uri2 = "trojan://secretpass@198.51.100.30:443?sni=tr.example.com#🇳🇱 Нидерланды"

        val mixedPayload = listOf(uri1, balancerJson, finalmaskJson, uri2).joinToString("\n")
        val servers = VlessUriParser.parseSubscriptionBody(mixedPayload, subscriptionId = "sub_mixed")

        assertEquals(4, servers.size)
        assertEquals(0, servers[0].sortOrder)
        assertEquals(1, servers[1].sortOrder)
        assertEquals(2, servers[2].sortOrder)
        assertEquals(3, servers[3].sortOrder)

        val sBalancer = servers[1]
        assertEquals("🇪🇺", sBalancer.countryFlag)
        assertFalse(sBalancer.isAutoSelect)
        assertEquals("JSON", sBalancer.subProtocolLabel)
        assertEquals("Любой оператор", sBalancer.note)

        val sFinalmask = servers[2]
        assertEquals("🇫🇮", sFinalmask.countryFlag)
        assertFalse(sFinalmask.isAutoSelect)
        assertEquals("JSON", sFinalmask.subProtocolLabel)
        assertEquals("Безлимитный", sFinalmask.note)

        val builtBalancerText = XrayConfigBuilder.buildConfigJson(sBalancer, AppSettings(), socksPort = 10808, datDir = null)
        assertFalse(builtBalancerText.contains("geoip:"))
        assertFalse(builtBalancerText.contains("geosite:"))
        val builtBalancer = JSONObject(builtBalancerText)
        assertFalse(builtBalancer.has("meta"))
        assertTrue(builtBalancer.has("burstObservatory"))
        assertEquals("Super_Balancer", builtBalancer.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).getString("tag"))
        val expandedIps = builtBalancer.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getJSONArray("ip")
        assertTrue(expandedIps.length() >= MincifryWhitelist.privateLanCidrs.size)

        val builtFinalmaskText = XrayConfigBuilder.buildConfigJson(sFinalmask, AppSettings(), socksPort = 10808, datDir = null)
        assertFalse(builtFinalmaskText.contains("geoip:"))
        assertFalse(builtFinalmaskText.contains("geosite:"))
        val builtFinalmask = JSONObject(builtFinalmaskText)
        assertFalse(builtFinalmask.has("meta"))
        assertTrue(builtFinalmask.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings").has("finalmask"))
    }

    @Test
    fun cachesXrayVersionAndSharesSingleOkHttpConnectionPool() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("1.1.0", BuildConfig.VERSION_NAME)
        SettingsDataStore.writeCachedXrayVersion(ctx, "26.6.27")
        assertEquals("26.6.27", SettingsDataStore.readCachedXrayVersion(ctx))

        val derivedA = SharedHttpClient.baseClient.newBuilder().build()
        val derivedB = SharedHttpClient.baseClient.newBuilder().build()
        assertTrue(derivedA.connectionPool === derivedB.connectionPool)
        assertTrue(derivedA.dispatcher === derivedB.dispatcher)

        InstalledAppsProvider.clearIconCache()
        assertNull(InstalledAppsProvider.getCachedAppIcon("com.nonexistent.pkg"))
    }
}

