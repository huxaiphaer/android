/**
 * ownCloud Android client application
 *
 * @author Jesus Recio (@jesmrec)
 * @author David González (@davigonz)
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

package com.owncloud.android.flow

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.owncloud.android.R
import com.owncloud.android.authentication.AccountAuthenticator.KEY_AUTH_TOKEN_TYPE
import com.owncloud.android.data.DataResult
import com.owncloud.android.data.capabilities.db.OCCapabilityEntity
import com.owncloud.android.data.sharing.shares.db.OCShareEntity
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.lib.common.accounts.AccountUtils
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.status.CapabilityBooleanType
import com.owncloud.android.lib.resources.status.OwnCloudVersion
import com.owncloud.android.presentation.viewmodels.capabilities.OCCapabilityViewModel
import com.owncloud.android.presentation.viewmodels.sharing.OCShareViewModel
import com.owncloud.android.presentation.ui.sharing.ShareActivity
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.utils.AccountsManager
import com.owncloud.android.utils.AppTestUtil
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DeletePublicShareTest {
    @Rule
    @JvmField
    val activityRule = ActivityTestRule(
        ShareActivity::class.java,
        true,
        false
    )

    private lateinit var file: OCFile

    private val publicShares = arrayListOf(
        AppTestUtil.createPublicShare( // With no expiration date
            path = "/Photos/image.jpg",
            expirationDate = 0L,
            isFolder = false,
            name = "image.jpg link",
            shareLink = "http://server:port/s/1"
        ),
        AppTestUtil.createPublicShare(
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "image.jpg updated link",
            shareLink = "http://server:port/s/2"
        )
    )

    private val capabilitiesLiveData = MutableLiveData<DataResult<OCCapabilityEntity>>()
    private val sharesLiveData = MutableLiveData<DataResult<List<OCShareEntity>>>()

    private val ocCapabilityViewModel = mockk<OCCapabilityViewModel>(relaxed = true)
    private val ocShareViewModel = mockk<OCShareViewModel>(relaxed = true)

    companion object {
        private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        private val account = Account("admin", "owncloud")

        @BeforeClass
        @JvmStatic
        fun init() {
            addAccount()
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            AccountsManager.deleteAllAccounts(targetContext)
        }

        private fun addAccount() {
            // obtaining an AccountManager instance
            val accountManager = AccountManager.get(targetContext)

            accountManager.addAccountExplicitly(account, "a", null)

            // include account version, user, server version and token with the new account
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_VERSION,
                OwnCloudVersion("10.2").toString()
            )
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_BASE_URL,
                "serverUrl:port"
            )
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_DISPLAY_NAME,
                "admin"
            )
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_ACCOUNT_VERSION,
                "1"
            )

            accountManager.setAuthToken(
                account,
                KEY_AUTH_TOKEN_TYPE,
                "AUTH_TOKEN"
            )
        }
    }

    @Before
    fun setUp() {
        val intent = Intent()

        file = getOCFileForTesting("image.jpg")
        intent.putExtra(FileActivity.EXTRA_FILE, file)

        every { ocCapabilityViewModel.getCapabilityForAccount(false) } returns capabilitiesLiveData
        every { ocCapabilityViewModel.getCapabilityForAccount(true) } returns capabilitiesLiveData
        every { ocShareViewModel.getPublicShares(file.remotePath) } returns sharesLiveData
        every { ocShareViewModel.getPrivateShares(file.remotePath) } returns MutableLiveData()

        stopKoin()

        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(
                module(override = true) {
                    viewModel {
                        ocCapabilityViewModel
                    }
                    viewModel {
                        ocShareViewModel
                    }
                }
            )
        }

        activityRule.launchActivity(intent)
    }

    @Test
    fun deletePublicLink() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares.take(2) as ArrayList<OCShareEntity>
        loadSharesSuccessfully(existingPublicShare)

        every {
            ocShareViewModel.deleteShare(any())
        } returns MutableLiveData<DataResult<Unit>>().apply {
            postValue(DataResult.success())

        }

        onView(allOf(withId(R.id.deletePublicLinkButton), hasSibling(withText(existingPublicShare[0].name))))
            .perform(click())
        onView(withId(android.R.id.button1)).perform(click())

        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf(existingPublicShare[1])
            )
        )

        onView(withText(existingPublicShare[0].name)).check(doesNotExist())
        onView(withText(existingPublicShare[1].name)).check(matches(isDisplayed()))
    }

    @Test
    fun deleteLastPublicLink() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        every {
            ocShareViewModel.deleteShare(any())
        } returns MutableLiveData<DataResult<Unit>>().apply {
            postValue(DataResult.success())
        }

        onView(withId(R.id.deletePublicLinkButton)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())

        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf()
            )
        )

        onView(withText(existingPublicShare.name)).check(matches(not(isDisplayed())))
        onView(withText(R.string.share_no_public_links)).check(matches(isDisplayed()))

    }

    @Test
    fun deleteShareLoading() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        onView(withId(R.id.deletePublicLinkButton)).perform(click())

        sharesLiveData.postValue(
            DataResult.loading(
                publicShares
            )
        )

        onView(withText(R.string.common_loading)).check(matches(isDisplayed()))
    }

    @Test
    fun deleteShareError() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        every {
            ocShareViewModel.deleteShare(any())
        } returns MutableLiveData<DataResult<Unit>>().apply {
            postValue(
                DataResult.error(
                    RemoteOperationResult.ResultCode.FORBIDDEN,
                    exception = Exception("Error when retrieving shares")
                )
            )
        }

        onView(withId(R.id.deletePublicLinkButton)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())

        onView(withText(R.string.unshare_link_file_error)).check(matches(isDisplayed()))
    }

    private fun getOCFileForTesting(name: String = "default"): OCFile {
        val file = OCFile("/Photos/image.jpg")
        file.availableOfflineStatus = OCFile.AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE
        file.fileName = name
        file.fileId = 9456985479
        file.remoteId = "1"
        file.privateLink = "image link"
        return file
    }

    private fun loadCapabilitiesSuccessfully(
        capability: OCCapabilityEntity = AppTestUtil.createCapability(
            versionString = "10.1.1",
            sharingPublicMultiple = CapabilityBooleanType.TRUE.value
        )
    ) {
        capabilitiesLiveData.postValue(
            DataResult.success(
                capability
            )
        )
    }

    private fun loadSharesSuccessfully(shares: ArrayList<OCShareEntity> = publicShares) {
        sharesLiveData.postValue(DataResult.success(shares))
    }
}
