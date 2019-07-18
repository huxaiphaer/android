/**
 * ownCloud Android client application
 *
 * @author masensio
 * @author David A. Velasco
 * @author Christian Schabesberger
 * @author David González Verdugo
 * Copyright (C) 2019 ownCloud GmbH.
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package com.owncloud.android.presentation.ui.sharing.fragments

import android.accounts.Account
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.owncloud.android.R
import com.owncloud.android.data.sharing.shares.db.OCShareEntity
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.lib.common.utils.Log_OC
import com.owncloud.android.lib.resources.shares.ShareType
import com.owncloud.android.presentation.UIResult
import com.owncloud.android.presentation.adapters.sharing.ShareUserListAdapter
import com.owncloud.android.presentation.viewmodels.sharing.OCShareViewModel
import com.owncloud.android.ui.activity.BaseActivity
import com.owncloud.android.utils.PreferenceUtils
import kotlinx.android.synthetic.main.search_users_groups_layout.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.ArrayList

/**
 * Fragment for Searching sharees (users and groups)
 *
 * A simple [Fragment] subclass.
 *
 * Activities that contain this fragment must implement the
 * [ShareFragmentListener] interface
 * to handle interaction events.
 *
 * Use the [SearchShareesFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SearchShareesFragment : Fragment(),
    ShareUserListAdapter.ShareUserAdapterListener {

    // Parameters
    private var file: OCFile? = null
    private var account: Account? = null

    // other members
    private var userGroupsAdapter: ShareUserListAdapter? = null
    private var listener: ShareFragmentListener? = null

    private val ocShareViewModel: OCShareViewModel by viewModel {
        parametersOf(
            file?.remotePath,
            account
        )
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            file = it.getParcelable(ARG_FILE)
            account = it.getParcelable(ARG_ACCOUNT)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.search_users_groups_layout, container, false)

        // Allow or disallow touches with other visible windows
        view.filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)

        // Get the SearchView and set the searchable configuration
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        val searchManager = requireActivity().getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(
            searchManager.getSearchableInfo(
                requireActivity().componentName
            )   // assumes parent activity is the searchable activity
        )
        searchView.setIconifiedByDefault(false)    // do not iconify the widget; expand it by default

        searchView.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI // avoid fullscreen with softkeyboard

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                Log_OC.v(TAG, "onQueryTextSubmit intercepted, query: $query")
                return true    // return true to prevent the query is processed to be queried;
                // a user / group will be picked only if selected in the list of suggestions
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false   // let it for the parent listener in the hierarchy / default behaviour
            }
        })

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().setTitle(R.string.share_with_title)

        // Load private shares in the list
        observePrivateShares()
    }

    private fun observePrivateShares() {
        ocShareViewModel.shares.observe(
            this,
            Observer { uiResult ->
                val privateShares = uiResult.data?.filter { share ->
                    share.shareType == ShareType.USER.value ||
                            share.shareType == ShareType.GROUP.value ||
                            share.shareType == ShareType.FEDERATED.value
                } as ArrayList<OCShareEntity>
                when (uiResult?.status) {
                    UIResult.Status.SUCCESS -> {
                        updatePrivateShares(privateShares)
                        (activity as BaseActivity).dismissLoadingDialog() // TODO Use listener
                    }
                    UIResult.Status.ERROR -> {
                        Snackbar.make(
                            activity?.findViewById(android.R.id.content)!!,
                            uiResult.errorMessage!!,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        updatePrivateShares(privateShares)
                        (activity as BaseActivity).dismissLoadingDialog() // TODO Use listener
                    }
                    UIResult.Status.LOADING -> {
                        (activity as BaseActivity).showLoadingDialog(R.string.common_loading)
                        updatePrivateShares(privateShares)
                    }
                    else -> {
                        Log.d(
                            TAG, "Unknown status when loading private shares for file ${file?.fileName} in account" +
                                    "${account?.name}"
                        )
                    }
                }
            }
        )
    }

    private fun updatePrivateShares(privateShares: ArrayList<OCShareEntity>) {
        // Update list of users/groups
        userGroupsAdapter = ShareUserListAdapter(
            requireActivity().applicationContext,
            R.layout.share_user_item, privateShares, this
        )

        // Show data
        val usersList = view!!.findViewById<ListView>(R.id.searchUsersListView)

        if (privateShares.size > 0) {
            usersList.visibility = View.VISIBLE
            usersList.adapter = userGroupsAdapter

        } else {
            usersList.visibility = View.GONE
        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        try {
            listener = activity as ShareFragmentListener?
        } catch (e: ClassCastException) {
            throw ClassCastException(requireActivity().toString() + " must implement OnFragmentInteractionListener")
        }

    }

    override fun onStart() {
        super.onStart()
        // focus the search view and request the software keyboard be shown
        val searchView = view!!.findViewById<View>(R.id.searchView)
        if (searchView.requestFocus()) {
            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideSoftKeyboard()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun hideSoftKeyboard() {
        view?.let {
            view?.findViewById<View>(R.id.searchView)?.let {
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
            }
        }
    }

    override fun unshareButtonPressed(share: OCShareEntity) {
        Log_OC.d(TAG, "Removed private share with " + share.sharedWithDisplayName!!)
        listener?.showRemoveShare(share)
    }

    override fun editShare(share: OCShareEntity) {
        // move to fragment to edit share
        Log_OC.d(TAG, "Editing " + share.sharedWithDisplayName!!)
        listener?.showEditPrivateShare(share)
    }

    companion object {
        private val TAG = SearchShareesFragment::class.java.simpleName

        // the fragment initialization parameters
        private val ARG_FILE = "FILE"
        private val ARG_ACCOUNT = "ACCOUNT"

        /**
         * Public factory method to create new SearchShareesFragment instances.
         *
         * @param fileToShare   An [OCFile] to be shared
         * @param account       The ownCloud account containing fileToShare
         * @return A new instance of fragment SearchShareesFragment.
         */
        fun newInstance(fileToShare: OCFile, account: Account) = SearchShareesFragment().apply {
            arguments = bundleOf(
                ARG_FILE to fileToShare,
                ARG_ACCOUNT to account
            )
        }
    }
}
