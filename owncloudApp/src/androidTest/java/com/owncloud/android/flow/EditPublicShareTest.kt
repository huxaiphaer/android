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

package com.owncloud.android.flow

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.widget.DatePicker
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withClassName
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
import org.hamcrest.Matchers
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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar

class EditPublicShareTest {
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
        ),
        AppTestUtil.createPublicShare(
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "image.jpg changed again link",
            shareLink = "http://server:port/s/3"
        ),
        AppTestUtil.createPublicShare( // With password but not expiration date
            path = "/Photos/image.jpg",
            expirationDate = 0L,
            isFolder = false,
            name = "image.jpg link",
            shareLink = "http://server:port/s/1",
            shareWith = "1"
        ),
        AppTestUtil.createPublicShare( // With expiration date but not password
            path = "/Photos/image.jpg",
            expirationDate = 2587896257,
            isFolder = false,
            name = "image.jpg link",
            shareLink = "http://server:port/s/1"
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
    fun editName() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        val updatedPublicShare = publicShares[1]

        // 1. Open dialog to edit an existing public share
        onView(withId(R.id.editPublicLinkButton)).perform(click())

        // 2. Update fields
        onView(withId(R.id.shareViaLinkNameValue)).perform(replaceText(updatedPublicShare.name))

        // 3. Edit public share with success
        editPublicShare(updatedPublicShare, "", resource = DataResult.success())

        // 4. Share properly updated
        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf(updatedPublicShare)
            )
        )

        // 5. Check whether the dialog to create the public share has been properly closed
        onView(withText(R.string.share_via_link_edit_title)).check(doesNotExist())
        onView(withText(updatedPublicShare.name)).check(matches(isDisplayed()))
    }

    @Test
    fun addPassword() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        val password = "1234"

        // 1. Open dialog to edit an existing public share
        onView(withId(R.id.editPublicLinkButton)).perform(click())

        // 2. Enable password and type it
        onView(withId(R.id.shareViaLinkPasswordSwitch)).perform(click())
        onView(withId(R.id.shareViaLinkPasswordValue)).perform(typeText(password))

        // 3. Edit public share with success
        editPublicShare(existingPublicShare, "1234", resource = DataResult.success())

        // 4. Share properly updated
        val updatedPublicShare = publicShares[3]

        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf(updatedPublicShare)
            )
        )

        // 5. Open dialog to check whether the password has been properly set
        onView(withId(R.id.editPublicLinkButton)).perform(click())
        onView(withId(R.id.shareViaLinkPasswordSwitch)).check(matches(isChecked()))
    }

    @Test
    fun removePassword() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[3]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        // 1. Open dialog to edit an existing public share
        onView(withId(R.id.editPublicLinkButton)).perform(click())

        // 2. Disable password
        onView(withId(R.id.shareViaLinkPasswordSwitch)).perform(click())

        // 3. Edit public share with success
        editPublicShare(existingPublicShare, "", resource = DataResult.success())

        // 4. Share properly updated
        val updatedPublicShare = publicShares[0]

        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf(updatedPublicShare)
            )
        )

        // 5. Open dialog to check whether the password has been properly disabled
        onView(withId(R.id.editPublicLinkButton)).perform(click())
        onView(withId(R.id.shareViaLinkPasswordSwitch)).check(matches(isNotChecked()))
    }

    @Test
    fun addExpirationDate() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val formatter: DateFormat = SimpleDateFormat("MMM dd, yyyy");
        val expirationDate = formatter.format(calendar.time);
        val publicLinkExpirationDateInMillis = SimpleDateFormat.getDateInstance().parse(expirationDate).time

        // 1. Open dialog to edit an existing public share
        onView(withId(R.id.editPublicLinkButton)).perform(click())

        // 2. Enable expiration date and set it
        onView(withId(R.id.shareViaLinkExpirationSwitch)).perform(click())
        onView(withClassName(Matchers.equalTo(DatePicker::class.java.name))).perform(
            PickerActions.setDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, // January code is 0, so let's add 1
                calendar.get(Calendar.DATE)
            )
        );
        onView(withId(android.R.id.button1)).perform(click())

        // 3. Edit public share with success
        editPublicShare(existingPublicShare, "", publicLinkExpirationDateInMillis, DataResult.success())

        // 4. Share properly updated
        val updatedPublicShare = publicShares[4]

        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf(updatedPublicShare)
            )
        )

        // 5. Open dialog to check whether the expiration date is enabled
        onView(withId(R.id.editPublicLinkButton)).perform(click())
        onView(withId(R.id.shareViaLinkExpirationSwitch)).check(matches(isChecked()))
    }

    @Test
    fun removeExpirationDate() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[4]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        // 1. Open dialog to edit an existing public share
        onView(withId(R.id.editPublicLinkButton)).perform(click())

        // 2. Disable expiration date
        onView(withId(R.id.shareViaLinkExpirationSwitch)).perform(click())

        // 3. Edit public share with success
        editPublicShare(existingPublicShare, "", resource = DataResult.success())

        // 4. Share properly updated
        val updatedPublicShare = publicShares[0]

        sharesLiveData.postValue(
            DataResult.success(
                arrayListOf(updatedPublicShare)
            )
        )

        // 5. Open dialog to check whether the expiration date is disabled
        onView(withId(R.id.editPublicLinkButton)).perform(click())
        onView(withId(R.id.shareViaLinkExpirationSwitch)).check(matches(isNotChecked()))
    }

    @Test
    fun editShareLoading() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[3]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        onView(withId(R.id.editPublicLinkButton)).perform(click())

        editPublicShare(existingPublicShare, resource = DataResult.loading())

        onView(withText(R.string.common_loading)).check(matches(isDisplayed()))
    }

    @Test
    fun editShareError() {
        loadCapabilitiesSuccessfully()

        val existingPublicShare = publicShares[0]
        loadSharesSuccessfully(arrayListOf(existingPublicShare))

        onView(withId(R.id.editPublicLinkButton)).perform(click())

        editPublicShare(
            existingPublicShare,
            password = "",
            resource = DataResult.error(
                RemoteOperationResult.ResultCode.FORBIDDEN,
                exception = Exception("Error when retrieving shares")
            )
        )

        onView(withText(R.string.update_link_file_error)).check(matches(isDisplayed()))
    }

    private fun getOCFileForTesting(name: String = "default") = OCFile("/Photos").apply {
        availableOfflineStatus = OCFile.AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE
        fileName = name
        fileId = 9456985479
        remoteId = "1"
        privateLink = "private link"
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

    private fun editPublicShare(
        share: OCShareEntity,
        password: String? = null,
        publicLinkExpirationDateInMillis: Long = -1,
        resource: DataResult<Unit> = DataResult.success() // Expected result when editing the share
    ) {
        every {
            ocShareViewModel.updatePublicShare(
                1,
                share.name!!,
                password,
                publicLinkExpirationDateInMillis,
                1,
                false
            )
        } returns MutableLiveData<DataResult<Unit>>().apply {
            postValue(resource)
        }

        onView(withId(R.id.saveButton)).perform(click())
    }
}
