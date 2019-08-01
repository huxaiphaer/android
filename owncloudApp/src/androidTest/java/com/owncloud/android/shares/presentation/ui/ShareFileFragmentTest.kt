/**
 * ownCloud Android client application
 *
 * @author David González Verdugo
 * Copyright (C) 2019 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.shares.presentation.ui

import android.accounts.Account
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.owncloud.android.R
import com.owncloud.android.capabilities.db.OCCapability
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.lib.resources.shares.ShareType
import com.owncloud.android.lib.resources.status.CapabilityBooleanType
import com.owncloud.android.lib.resources.status.OwnCloudVersion
import com.owncloud.android.shares.domain.OCShare
import com.owncloud.android.shares.presentation.fragment.ShareFileFragment
import com.owncloud.android.utils.TestUtil
import org.hamcrest.CoreMatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class ShareFileFragmentTest {
    @Rule
    @JvmField
    val activityRule = ActivityTestRule(
        TestShareFileActivity::class.java,
        true,
        true
    )

    @Test
    fun showHeader() {
        loadShareFileFragment()
        onView(withId(R.id.shareFileName)).check(matches(withText("image.jpg")))
    }

    @Test
    fun fileSizeVisible() {
        loadShareFileFragment()
        onView(withId(R.id.shareFileSize)).check(matches(isDisplayed()))
    }

    @Test
    fun showPrivateLink() {
        loadShareFileFragment()
        onView(withId(R.id.getPrivateLinkButton)).check(matches(isDisplayed()))
    }

    /******************************************************************************************************
     ******************************************* PRIVATE SHARES *******************************************
     ******************************************************************************************************/

    private var userSharesList = arrayListOf(
        TestUtil.createPrivateShare(
            shareType = ShareType.USER.value,
            path = "/Photos/image.jpg",
            isFolder = false,
            shareWith = "batman",
            sharedWithDisplayName = "Batman"
        ),
        TestUtil.createPrivateShare(
            shareType = ShareType.USER.value,
            path = "/Photos/image.jpg",
            isFolder = false,
            shareWith = "jocker",
            sharedWithDisplayName = "Jocker"
        )
    )

    private var groupSharesList = arrayListOf(
        TestUtil.createPrivateShare(
            shareType = ShareType.GROUP.value,
            path = "/Photos/image.jpg",
            isFolder = false,
            shareWith = "suicideSquad",
            sharedWithDisplayName = "Suicide Squad"
        ),
        TestUtil.createPrivateShare(
            shareType = ShareType.GROUP.value,
            path = "/Photos/image.jpg",
            isFolder = false,
            shareWith = "avengers",
            sharedWithDisplayName = "Avengers"
        )
    )

    @Test
    fun showUsersAndGroupsSectionTitle() {
        loadShareFileFragment(privateShares = userSharesList)
        onView(withText(R.string.share_with_user_section_title)).check(matches(isDisplayed()))
    }

    @Test
    fun showNoPrivateShares() {
        loadShareFileFragment()
        onView(withText(R.string.share_no_users)).check(matches(isDisplayed()))
    }

    @Test
    fun showUserShares() {
        loadShareFileFragment(privateShares = userSharesList)
        onView(withText("Batman")).check(matches(isDisplayed()))
        onView(withText("Batman")).check(matches(hasSibling(withId(R.id.unshareButton))))
            .check(matches(isDisplayed()))
        onView(withText("Batman")).check(matches(hasSibling(withId(R.id.editShareButton))))
            .check(matches(isDisplayed()))
        onView(withText("Jocker")).check(matches(isDisplayed()))
    }

    @Test
    fun showGroupShares() {
        loadShareFileFragment(privateShares = arrayListOf(groupSharesList[0]))
        onView(withText("Suicide Squad (group)")).check(matches(isDisplayed()))
        onView(withText("Suicide Squad (group)")).check(matches(hasSibling(withId(R.id.icon))))
            .check(matches(isDisplayed()))
        onView(withTagValue(CoreMatchers.equalTo(R.drawable.ic_group))).check(matches(isDisplayed()))
    }

    /******************************************************************************************************
     ******************************************* PUBLIC SHARES ********************************************
     ******************************************************************************************************/

    private var publicShareList = arrayListOf(
        TestUtil.createPublicShare(
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "Image link",
            shareLink = "http://server:port/s/1"
        ),
        TestUtil.createPublicShare(
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "Image link 2",
            shareLink = "http://server:port/s/2"
        ),
        TestUtil.createPublicShare(
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "Image link 3",
            shareLink = "http://server:port/s/3"
        )
    )

    @Test
    fun showNoPublicShares() {
        loadShareFileFragment(publicShares = arrayListOf())
        onView(withText(R.string.share_no_public_links)).check(matches(isDisplayed()))
    }

    @Test
    fun showPublicShares() {
        loadShareFileFragment()
        onView(withText("Image link")).check(matches(isDisplayed()))
        onView(withText("Image link")).check(matches(hasSibling(withId(R.id.getPublicLinkButton))))
            .check(matches(isDisplayed()))
        onView(withText("Image link")).check(matches(hasSibling(withId(R.id.deletePublicLinkButton))))
            .check(matches(isDisplayed()))
        onView(withText("Image link")).check(matches(hasSibling(withId(R.id.editPublicLinkButton))))
            .check(matches(isDisplayed()))
        onView(withText("Image link 2")).check(matches(isDisplayed()))
        onView(withText("Image link 3")).check(matches(isDisplayed()))
    }

    @Test
    fun showPublicSharesSharingEnabled() {
        loadShareFileFragment(
            capabilities = TestUtil.createCapability(sharingPublicEnabled = CapabilityBooleanType.TRUE.value)
        )

        onView(withText("Image link")).check(matches(isDisplayed()))
        onView(withText("Image link 2")).check(matches(isDisplayed()))
        onView(withText("Image link 3")).check(matches(isDisplayed()))
    }

    @Test
    fun hidePublicSharesSharingDisabled() {
        loadShareFileFragment(
            capabilities = TestUtil.createCapability(sharingPublicEnabled = CapabilityBooleanType.FALSE.value)
        )

        onView(withId(R.id.shareViaLinkSection))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
    }

    @Test
    fun createPublicShareMultipleCapability() {
        loadShareFileFragment(
            capabilities = TestUtil.createCapability(
                versionString = "10.1.1",
                sharingPublicMultiple = CapabilityBooleanType.TRUE.value
            ),
            publicShares = arrayListOf(publicShareList.get(0))
        )

        onView(withId(R.id.addPublicLinkButton))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    @Test
    fun cannotCreatePublicShareMultipleCapability() {
        loadShareFileFragment(
            capabilities = TestUtil.createCapability(
                versionString = "10.1.1",
                sharingPublicMultiple = CapabilityBooleanType.FALSE.value
            ),
            publicShares = arrayListOf(publicShareList.get(0))
        )

        onView(withId(R.id.addPublicLinkButton))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.INVISIBLE)))
    }

    @Test
    fun cannotCreatePublicShareServerCapability() {
        loadShareFileFragment(
            capabilities = TestUtil.createCapability(
                versionString = "9.3.1"
            ),
            publicShares = arrayListOf(publicShareList.get(0))
        )

        onView(withId(R.id.addPublicLinkButton))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.INVISIBLE)))
    }

    /******************************************************************************************************
     *********************************************** COMMON ***********************************************
     ******************************************************************************************************/

    @Test
    fun hideSharesSharingApiDisabled() {
        loadShareFileFragment(
            capabilities = TestUtil.createCapability(sharingApiEnabled = CapabilityBooleanType.FALSE.value)
        )
        onView(withId(R.id.shareWithUsersSection))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))

        onView(withId(R.id.shareViaLinkSection))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
    }

    private fun getOCFileForTesting(name: String = "default") = OCFile("/Photos").apply {
        availableOfflineStatus = OCFile.AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE
        fileName = name
        fileId = 9456985479
        remoteId = "1"
        privateLink = "private link"
    }

    private fun loadShareFileFragment(
        capabilities: OCCapability = TestUtil.createCapability(),
        privateShares: ArrayList<OCShare> = arrayListOf(),
        publicShares: ArrayList<OCShare> = publicShareList
    ) {
        val account = mock(Account::class.java)
        val ownCloudVersion = mock(OwnCloudVersion::class.java)
        `when`(ownCloudVersion.isSearchUsersSupported).thenReturn(true)

        val shareFileFragment = ShareFileFragment.newInstance(
            getOCFileForTesting("image.jpg"),
            account,
            ownCloudVersion
        )

        activityRule.activity.capabilities = capabilities
        activityRule.activity.privateShares = privateShares
        activityRule.activity.publicShares = publicShares
        activityRule.activity.setFragment(shareFileFragment)
    }
}
