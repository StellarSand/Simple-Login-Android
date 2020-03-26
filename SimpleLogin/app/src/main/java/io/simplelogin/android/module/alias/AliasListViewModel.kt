package io.simplelogin.android.module.alias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.simplelogin.android.utils.SLApiService
import io.simplelogin.android.utils.SLSharedPreferences
import io.simplelogin.android.utils.enums.AliasFilterMode
import io.simplelogin.android.utils.enums.SLError
import io.simplelogin.android.utils.model.Alias
import java.lang.IllegalStateException

class AliasListViewModel(application: Application) : AndroidViewModel(application) {
    val apiKey: String by lazy {
        SLSharedPreferences.getApiKey(application) ?: throw IllegalStateException("API key is null")
    }
    private val _error = MutableLiveData<SLError>()
    val error: LiveData<SLError>
        get() = _error

    fun onHandleErrorComplete() {
        _error.value = null
    }

    private var currentPage = -1
    var moreAliasesToLoad: Boolean = true
        private set

    private var _aliases = mutableListOf<Alias>()
    private var _deletedAliasIds = mutableListOf<Int>()
    var filteredAliases = listOf<Alias>()
        private set

    private var _isFetchingAliases = false

    private val _eventUpdateAliases = MutableLiveData<Boolean>()
    val eventUpdateAliases: LiveData<Boolean>
        get() = _eventUpdateAliases

    fun onEventUpdateAliasesComplete() {
        _eventUpdateAliases.value = false
    }

    fun fetchAliases() {
        if (!moreAliasesToLoad || _isFetchingAliases) return
        _isFetchingAliases = true
        SLApiService.fetchAliases(apiKey, currentPage + 1) { newAliases, error ->
            _isFetchingAliases = false

            if (error != null) {
                _error.postValue(error)
            } else if (newAliases != null) {
                if (newAliases.isEmpty()) {
                    moreAliasesToLoad = false
                    _eventUpdateAliases.postValue(true)
                } else {
                    currentPage += 1
                    _aliases.addAll(newAliases.filter { !_deletedAliasIds.contains(it.id) })
                    filterAliases()
                }
            }
        }
    }

    fun refreshAliases() {
        currentPage = -1
        moreAliasesToLoad = true
        _aliases = mutableListOf()
        _deletedAliasIds = mutableListOf()
        fetchAliases()
    }

    // Filter
    var aliasFilterMode = AliasFilterMode.ALL
        private set

    fun filterAliases(mode: AliasFilterMode? = null) {
        mode?.let {
            aliasFilterMode = it
        }

        filteredAliases = when (aliasFilterMode) {
            AliasFilterMode.ALL -> _aliases
            AliasFilterMode.ACTIVE -> _aliases.filter { it.enabled }
            AliasFilterMode.INACTIVE -> _aliases.filter { !it.enabled }
        }

        _eventUpdateAliases.postValue(true)
    }

    // Delete
    fun deleteAlias(alias: Alias) {
        SLApiService.deleteAlias(apiKey, alias) { error ->
            if (error != null) {
                _error.postValue(error)
            } else {
                _deletedAliasIds.add(alias.id)
                _aliases.removeAll { it.id == alias.id }
                filterAliases()
            }
        }
    }

    // Toggle
    private var _toggledAliasIndex = MutableLiveData<Int>()
    val toggledAliasIndex: LiveData<Int>
        get() = _toggledAliasIndex

    fun onHandleToggleAliasComplete() {
        _toggledAliasIndex.value = null
    }

    fun toggleAlias(alias: Alias, index: Int) {
        SLApiService.toggleAlias(apiKey, alias) { enabled, error ->
            if (error != null) {
                _error.postValue(error)
            } else if (enabled != null) {
                _aliases.find { it.id == alias.id }?.setEnabled(enabled)
                _eventUpdateAliases.postValue(true)
                _toggledAliasIndex.postValue(index)
            }
        }
    }
}