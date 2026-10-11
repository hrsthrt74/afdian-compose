package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.ui.navigation.AppLinkDestination
import com.hrsthrt74.afdian.ui.navigation.parseAppLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinkRegistryTest {
    @Test
    fun parsesPostLinksFromSupportedHosts() {
        assertEquals(
            AppLinkDestination.Post("post-123"),
            parseAppLink("https://afdian.com/p/post-123?from=message"),
        )
        assertEquals(
            AppLinkDestination.Post("post-123"),
            parseAppLink("https://www.ifdian.net/p/post-123"),
        )
    }

    @Test
    fun parsesNewAndLegacyCreatorLinks() {
        assertEquals(
            AppLinkDestination.Creator("alice"),
            parseAppLink("https://afdian.com/a/alice"),
        )
        assertEquals(
            AppLinkDestination.Creator("alice"),
            parseAppLink("https://ifdian.net/@alice"),
        )
    }

    @Test
    fun parsesAppOwnedManagementAndProductLinks() {
        assertEquals(
            AppLinkDestination.Post("post-456"),
            parseAppLink("https://afdian.com/album/album-123/post-456"),
        )
        assertEquals(AppLinkDestination.Explore, parseAppLink("https://afdian.com/explore"))
        assertEquals(AppLinkDestination.Favorites, parseAppLink("https://afdian.com/dashboard/mark"))
        assertEquals(AppLinkDestination.Shop, parseAppLink("https://ifdian.net/setting/shop"))
        assertEquals(AppLinkDestination.Management, parseAppLink("https://afdian.com/dashboard/stats"))
        assertEquals(
            AppLinkDestination.ReceivedSponsors,
            parseAppLink("https://afdian.com/dashboard/sponsored-bill"),
        )
        assertEquals(
            AppLinkDestination.MonthlySummary,
            parseAppLink("https://afdian.com/dashboard/income"),
        )
        assertEquals(
            AppLinkDestination.Sponsoring,
            parseAppLink("https://afdian.com/dashboard/sponsoring"),
        )
        assertEquals(AppLinkDestination.Orders, parseAppLink("https://afdian.com/dashboard/order"))
        assertEquals(AppLinkDestination.Affiliates, parseAppLink("https://afdian.com/setting/aff"))
        assertEquals(AppLinkDestination.Developer, parseAppLink("https://afdian.com/dashboard/dev"))
        assertEquals(
            AppLinkDestination.CustomerService,
            parseAppLink("https://afdian.com/message/27f7cea2370d11e8ae8852540025c377"),
        )
        assertEquals(
            AppLinkDestination.Product("item-123"),
            parseAppLink("https://afdian.com/item/item-123"),
        )
        assertEquals(
            AppLinkDestination.Product("bundle-123"),
            parseAppLink("https://ifdian.net/bundle/bundle-123"),
        )
    }

    @Test
    fun leavesUnsupportedLinksForCustomTabs() {
        assertNull(parseAppLink("https://afdian.com/withdraw"))
        assertNull(parseAppLink("https://example.com/p/post-123"))
        assertNull(parseAppLink("not a url"))
        assertNull(parseAppLink("ftp://afdian.com/p/post-123"))
        assertNull(parseAppLink("https://afdian.com/setting/shop"))
        assertNull(parseAppLink("https://afdian.com/message/another-user"))
    }
}
