package io.simplelogin.android.module.alias.activity

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.MergeAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.simplelogin.android.R
import io.simplelogin.android.databinding.DialogViewEditTextBinding
import io.simplelogin.android.databinding.FragmentAliasActivityBinding
import io.simplelogin.android.module.alias.AliasListViewModel
import io.simplelogin.android.module.home.HomeActivity
import io.simplelogin.android.utils.SLApiService
import io.simplelogin.android.utils.baseclass.BaseFragment
import io.simplelogin.android.utils.extension.*
import io.simplelogin.android.utils.model.Action
import io.simplelogin.android.utils.model.AliasActivity
import io.simplelogin.android.utils.model.AliasMailbox

class AliasActivityListFragment : BaseFragment(), HomeActivity.OnBackPressed {
    private lateinit var binding: FragmentAliasActivityBinding
    private val aliasListViewModel: AliasListViewModel by activityViewModels()
    private lateinit var viewModel: AliasActivityListViewModel
    private lateinit var headerAdapter: AliasActivityListHeaderAdapter
    private lateinit var activityAdapter: AliasActivityListAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAliasActivityBinding.inflate(inflater)

        setUpViewModel()

        binding.toolbar.setNavigationOnClickListener { updateAliasListViewModelAndNavigateUp() }
        binding.toolbarTitleText.text = viewModel.alias.email
        binding.toolbarTitleText.isSelected = true // to trigger marquee animation

        setUpRecyclerView()
        setLoading(false)

        return binding.root
    }

    private fun setLoading(loading: Boolean) {
        binding.rootConstraintLayout.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setUpViewModel() {
        val alias = AliasActivityListFragmentArgs.fromBundle(requireArguments()).alias

        val tempViewModel: AliasActivityListViewModel by viewModels {
            context?.let {
                AliasActivityListViewModelFactory(it, alias)
            } ?: throw IllegalStateException("Context is null")
        }
        viewModel = tempViewModel
        viewModel.fetchActivities()
        viewModel.eventHaveNewActivities.observe(viewLifecycleOwner, Observer { haveNewActivities ->
            activity?.runOnUiThread {
                setLoading(false)

                if (haveNewActivities) {
                    activityAdapter.submitList(viewModel.activities)
                }

                if (binding.swipeRefreshLayout.isRefreshing) {
                    context?.toastUpToDate()
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        })

        viewModel.error.observe(viewLifecycleOwner, Observer { error ->
            if (error != null) {
                setLoading(false)
                context?.toastError(error)
                viewModel.onHandleErrorComplete()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        })

        viewModel.eventUpdateMetadata.observe(viewLifecycleOwner, Observer { metadataUpdated ->
            if (metadataUpdated) {
                setLoading(false)
                headerAdapter.notifyDataSetChanged()
                viewModel.onHandleUpdateMetadataComplete()
            }
        })
    }

    private fun setUpRecyclerView() {
        headerAdapter = AliasActivityListHeaderAdapter(viewModel, object : AliasActivityListHeaderAdapter.ClickListener {
            override fun editMailboxesButtonClicked() {
                fetchMailboxesAndShowAlert()
            }

            override fun editNameButtonClicked() {
                val dialogTextViewBinding = DialogViewEditTextBinding.inflate(layoutInflater)
                dialogTextViewBinding.editText.hint = "Ex: Jane Doe"
                dialogTextViewBinding.editText.setText(viewModel.alias.name)
                val title = when (viewModel.alias.name) {
                    null -> "Add name for alias"
                    else -> "Edit name for alias"
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(viewModel.alias.email)
                    .setView(dialogTextViewBinding.root)
                    .setNeutralButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        viewModel.updateName(dialogTextViewBinding.editText.text.toString())
                    }
                    .show()
            }

            override fun editNoteButtonClicked() {
                val dialogTextViewBinding = DialogViewEditTextBinding.inflate(layoutInflater)
                dialogTextViewBinding.editText.hint = "Ex: For tech newsletters, online shopping..."
                dialogTextViewBinding.editText.setText(viewModel.alias.note)
                val title = when (viewModel.alias.note) {
                    null -> "Add note for alias"
                    else -> "Edit note for alias"
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(viewModel.alias.email)
                    .setView(dialogTextViewBinding.root)
                    .setNeutralButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        viewModel.updateNote(dialogTextViewBinding.editText.text.toString())
                    }
                    .show()
            }
        })

        activityAdapter = AliasActivityListAdapter(object : AliasActivityListAdapter.ClickListener {
            override fun onClick(aliasActivity: AliasActivity) {
                val toEmail = when (aliasActivity.action) {
                    Action.REPLY -> aliasActivity.to
                    else -> aliasActivity.from
                }

                MaterialAlertDialogBuilder(requireContext(), R.style.SlAlertDialogTheme)
                    .setTitle("Email to \"$toEmail\"")
                    .setItems(
                        arrayOf("Copy reverse-alias", "Begin composing with default email")
                    ) { _, itemIndex ->
                        when (itemIndex) {
                            0 -> {
                                activity?.copyToClipboard(
                                    aliasActivity.reverseAlias,
                                    aliasActivity.reverseAlias
                                )
                                context?.toastShortly("Copied \"${aliasActivity.reverseAlias}\"")
                            }

                            1 -> {
                                activity?.startSendEmailIntent(aliasActivity.reverseAlias)
                            }
                        }
                    }
                    .show()
            }
        })

        binding.recyclerView.adapter = MergeAdapter(headerAdapter, activityAdapter)
        val linearLayoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = linearLayoutManager

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if ((linearLayoutManager.findLastCompletelyVisibleItemPosition() == viewModel.activities.size - 1) && viewModel.moreToLoad) {
                    viewModel.fetchActivities()
                }
            }
        })

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshAlias()
            viewModel.refreshActivities()
        }
    }

    private fun fetchMailboxesAndShowAlert() {
        setLoading(true)
        SLApiService.fetchMailboxes(viewModel.apiKey) { result ->
            activity?.runOnUiThread {
                setLoading(false)

                result.onFailure(requireContext()::toastThrowable)

                result.onSuccess { mailboxes ->
                    val items = arrayOf("[Select all]") + mailboxes.map { it.email }.toTypedArray()
                    val checkedItems = BooleanArray(items.size)
                    val selectedMailboxesName = viewModel.alias.mailboxes.map { it.email }
                    checkedItems.forEachIndexed { index, _ ->
                        if (selectedMailboxesName.contains(items[index])) {
                            checkedItems[index] = true
                        }
                    }

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Select mailboxes")
                        .setMultiChoiceItems(items, checkedItems) { dialog, which, isChecked ->
                            val listView = (dialog as AlertDialog).listView
                            // Select all
                            if (which == 0) {
                                checkedItems.forEachIndexed { index, _ ->
                                    checkedItems[index] = true
                                    listView.setItemChecked(index, true)
                                }

                                checkedItems[0] = false
                                listView.setItemChecked(0, false)
                            }

                            // At least 1 mailbox is selected
                            if (checkedItems.none { it }) {
                                checkedItems[which] = true
                                listView.setItemChecked(which, true)
                            }
                        }
                        .setPositiveButton("Save") { _, _ ->
                            val aliasMailboxes = mutableListOf<AliasMailbox>()
                            checkedItems.forEachIndexed { index, isChecked ->
                                if (isChecked) {
                                    aliasMailboxes.add(mailboxes.first { it.email == items[index] }.toAliasMailbox())
                                }
                            }

                            setLoading(true)
                            viewModel.updateMailboxes(aliasMailboxes)
                        }
                        .setNeutralButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun refreshAlias() {
        setLoading(true)
        SLApiService.getAlias(viewModel.apiKey, viewModel.alias.id) { result ->
            activity?.runOnUiThread {
                setLoading(false)

                result.onSuccess { alias ->
                    viewModel.alias = alias
                    headerAdapter.notifyDataSetChanged()
                }

                result.onFailure(requireContext()::toastThrowable)
            }
        }
    }

    private fun updateAliasListViewModelAndNavigateUp() {
        aliasListViewModel.updateAlias(viewModel.alias)
        findNavController().navigateUp()
    }

    // HomeActivity.OnBackPressed
    override fun onBackPressed() {
        updateAliasListViewModelAndNavigateUp()
    }
}