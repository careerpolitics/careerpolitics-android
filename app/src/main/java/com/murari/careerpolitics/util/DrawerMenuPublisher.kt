package com.murari.careerpolitics.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DrawerMenuPublisher {
    private val _items = MutableStateFlow<List<DrawerMenuItem>>(emptyList())
    val items: StateFlow<List<DrawerMenuItem>> = _items

    fun publish(newItems: List<DrawerMenuItem>) {
        _items.value = newItems
    }
}


