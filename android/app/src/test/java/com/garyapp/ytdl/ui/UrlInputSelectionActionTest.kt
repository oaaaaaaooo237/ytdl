package com.garyapp.ytdl.ui

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UrlInputSelectionActionTest {
    @Test
    fun editTextInstallationAssignsSelectionAndInsertionCallbacks() {
        val editText = EditText(context())

        editText.installUrlSelectionActionModeCallbacks()

        val selectionCallback = editText.customSelectionActionModeCallback
        assertTrue(selectionCallback is UrlSelectionActionModeCallback)
        assertSame(selectionCallback, editText.customInsertionActionModeCallback)
        val nativeItem = PopupMenu(editText.context, editText).menu.add("复制")
        assertFalse(selectionCallback.onActionItemClicked(NoOpActionMode(), nativeItem))
    }

    @Test
    fun selectionActionAddsDeleteWithoutRemovingNativeActions() {
        val editText = EditText(context())
        val menu = PopupMenu(editText.context, editText).menu.apply {
            add("全选")
            add("复制")
            add("粘贴")
        }

        val created = UrlSelectionActionModeCallback(editText)
            .onCreateActionMode(NoOpActionMode(), menu)

        assertTrue(created)
        assertNotNull(menu.findItem(UrlSelectionDeleteActionId))
        assertNotNull(menu.findItemByTitle("全选"))
        assertNotNull(menu.findItemByTitle("复制"))
        assertNotNull(menu.findItemByTitle("粘贴"))
    }

    @Test
    fun deleteActionRemovesSelectionAndKeepsTextWatcherPath() {
        val editText = EditText(context()).apply {
            setText("abcdef")
            setSelection(1, 4)
        }
        var changedText = ""
        editText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    changedText = s.toString()
                }
            },
        )
        val menu = PopupMenu(editText.context, editText).menu
        val callback = UrlSelectionActionModeCallback(editText)
        callback.onCreateActionMode(NoOpActionMode(), menu)

        val handled = callback.onActionItemClicked(
            NoOpActionMode(),
            requireNotNull(menu.findItem(UrlSelectionDeleteActionId)),
        )

        assertTrue(handled)
        assertEquals("aef", editText.text.toString())
        assertEquals("aef", changedText)
    }

    @Test
    fun deleteActionClearsFieldWhenThereIsNoSelection() {
        val editText = EditText(context()).apply {
            setText("abcdef")
            setSelection(3)
        }
        val menu = PopupMenu(editText.context, editText).menu
        val callback = UrlSelectionActionModeCallback(editText)
        callback.onCreateActionMode(NoOpActionMode(), menu)

        callback.onActionItemClicked(
            NoOpActionMode(),
            requireNotNull(menu.findItem(UrlSelectionDeleteActionId)),
        )

        assertEquals("", editText.text.toString())
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()
}

private fun Menu.findItemByTitle(title: String): MenuItem? =
    (0 until size()).map(::getItem).firstOrNull { it.title.toString() == title }

private class NoOpActionMode : ActionMode() {
    override fun setTitle(title: CharSequence?) = Unit
    override fun setTitle(resId: Int) = Unit
    override fun setSubtitle(subtitle: CharSequence?) = Unit
    override fun setSubtitle(resId: Int) = Unit
    override fun setCustomView(view: View?) = Unit
    override fun invalidate() = Unit
    override fun finish() = Unit
    override fun getMenu(): Menu? = null
    override fun getTitle(): CharSequence? = null
    override fun getSubtitle(): CharSequence? = null
    override fun getCustomView(): View? = null
    override fun getMenuInflater() = null
}
