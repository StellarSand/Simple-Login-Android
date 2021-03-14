package io.simplelogin.android.utils.extension

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.simplelogin.android.R
import io.simplelogin.android.utils.interfaces.Reversable
import io.simplelogin.android.utils.model.Alias
import io.simplelogin.android.utils.model.AliasMailbox
import io.simplelogin.android.utils.model.Mailbox

fun Activity.showKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(this.currentFocus, InputMethodManager.SHOW_IMPLICIT)
}

fun Activity.dismissKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (inputMethodManager.isAcceptingText) {
        inputMethodManager.hideSoftInputFromWindow(this.currentFocus?.windowToken, 0)
    }
}

fun Activity.copyToClipboard(label: String, text: String): Boolean {
    val clipboardManager =
        (getSystemService(Context.CLIPBOARD_SERVICE) ?: false) as ClipboardManager
    val clipData = ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clipData) // using setter will cause error 'val cannot be reassigned'
    return true
}

fun Activity.startSendEmailIntent(emailAddress: String) {
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("mailto:")
    intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
    startActivity(intent)
}

fun Activity.getScreenHeight(): Int {
    val size = Point()
    windowManager.defaultDisplay.getRealSize(size)
    // y is height, x is width
    return size.y
}

fun Activity.openUrlInBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
}

fun Activity.showSelectMailboxesAlert(
    mailboxes: List<Mailbox>,
    selectedMailboxes: List<AliasMailbox>,
    save: (checkedMailboxes: List<AliasMailbox>) -> Unit
) {
    val items = arrayOf("[Select all]") + mailboxes.map { it.email }.toTypedArray()
    val checkedItems = BooleanArray(items.size)
    val selectedMailboxesName = selectedMailboxes.map { it.email }
    checkedItems.forEachIndexed { index, _ ->
        if (selectedMailboxesName.contains(items[index])) {
            checkedItems[index] = true
        }
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("Select mailboxes")
        .setMultiChoiceItems(items, checkedItems) { dialog, which, _ ->
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
                    aliasMailboxes.add(mailboxes.first { it.email == items[index] }
                        .toAliasMailbox())
                }
            }

            save(aliasMailboxes)
        }
        .setNeutralButton("Cancel", null)
        .show()
}

fun Activity.alertReversableOptions(reversable: Reversable, alias: Alias? = null) {
    fun copyToClipboardAndToast(text: String) {
        copyToClipboard(text, text)
        toastShortly("Copied $text")
    }

    val toString = "Email to \"${reversable.email}\""
    val fromString = if (alias != null) {
        " from \"${alias.email}\""
    } else {
        ""
    }

    MaterialAlertDialogBuilder(this, R.style.SlAlertDialogTheme)
        .setTitle(toString + fromString)
        .setItems(
            arrayOf(
                getString(R.string.copy_reverse_alias_with_display_name),
                getString(R.string.copy_reverse_alias_without_display_name),
                getString(R.string.begin_composing_with_default_email)
            )
        ) { _, itemIndex ->
            when (itemIndex) {
                0 -> copyToClipboardAndToast(reversable.reverseAlias)
                1 -> copyToClipboardAndToast(reversable.reverseAliasAddress)
                2 -> startSendEmailIntent(reversable.reverseAlias)
            }
        }
        .show()
}